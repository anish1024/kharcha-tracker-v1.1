package com.kharcha.tracker.data.remote

import com.kharcha.tracker.core.util.NetworkResult
import com.kharcha.tracker.core.util.safeCall
import com.kharcha.tracker.data.remote.dto.AuthTokens
import com.kharcha.tracker.data.remote.dto.ExpenseDto
import com.kharcha.tracker.data.remote.dto.GroupDto
import com.kharcha.tracker.data.remote.dto.MagicLinkRequest
import com.kharcha.tracker.data.remote.dto.MagicLinkResponse
import com.kharcha.tracker.data.remote.dto.MagicLinkVerifyRequest
import com.kharcha.tracker.data.remote.dto.SettlementPreviewDto
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/**
 * Round-trips the real Retrofit + Gson stack against a local
 * [MockWebServer] that answers with the exact JSON the FastAPI gateway
 * emits, for the Stage 5 read-only endpoints plus the passwordless
 * magic-link login pair.
 */
class DashboardClientIntegrationTest {

    private val groupJson = """
        {
          "id": "g1",
          "name": "Flatmates",
          "created_by_user_id": "aa11",
          "invite_code": "ABCD1234",
          "members": ["aa11", "bb22"],
          "status": "ACTIVE",
          "currency": "INR",
          "time_created": "2026-10-01T09:00:00+00:00",
          "last_updated": "2026-10-29T14:22:03+00:00"
        }
    """.trimIndent()

    private val expenseJson = """
        {
          "trans_id": 17,
          "group_id": "g1",
          "date_time": "2026-10-29T14:22:03+00:00",
          "expn_ctgry": "FOOD",
          "expn_desc": "Groceries",
          "expn_amnt": 450.5,
          "expn_splt": [{"user_id": "aa11", "percentage": 100.0}],
          "paid_by": [{"user_id": "aa11", "amt": 450.5}],
          "expn_bill_screenshot": "",
          "expn_payment_screenshot": "",
          "status": "ACTIVE"
        }
    """.trimIndent()

    private val baseJson = """
        {
          "auth_user_id": "3b5d3c2e-9f6a-4e1b-8c2d-1a2b3c4d5e6f",
          "auth_token": "access-jwt",
          "auth_token_expiry": "2026-10-30T14:22:03+00:00",
          "refresh_token": "refresh-jwt",
          "refresh_token_expiry": "2026-11-29T14:22:03+00:00"
        }
    """.trimIndent()

    @Test
    fun `myGroups round-trips a list of groups`() {
        withMockServer(200, "[$groupJson, $groupJson]") { _, api ->
            val result = runBlocking { safeCall { api.myGroups() } }
            assertTrue("expected success, got $result", result is NetworkResult.Success)
            val groups = (result as NetworkResult.Success<List<GroupDto>>).data
            assertEquals(2, groups.size)
            assertEquals("Flatmates", groups[0].name)
            assertEquals("INR", groups[1].currency)
        }
    }

    @Test
    fun `myGroups surfaces a 401 with the server detail`() {
        withMockServer(401, """{"detail":"Invalid or expired token"}""") { _, api ->
            val result = runBlocking { safeCall { api.myGroups() } }
            assertTrue("expected error, got $result", result is NetworkResult.Error)
            val error = result as NetworkResult.Error
            assertEquals(401, error.code)
            assertEquals("Invalid or expired token", error.message)
        }
    }

    @Test
    fun `groupExpenses sends limit and offset and round-trips expenses`() {
        withMockServer(200, "[$expenseJson]") { server, api ->
            val result = runBlocking {
                safeCall { api.groupExpenses("g1", limit = 50, offset = 10) }
            }
            assertTrue("expected success, got $result", result is NetworkResult.Success)
            val expenses = (result as NetworkResult.Success<List<ExpenseDto>>).data
            assertEquals(1, expenses.size)
            assertEquals(450.5, expenses[0].amount, 0.0)

            val recorded = server.takeRequest()
            assertEquals("/api/v1/groups/g1/expenses", recorded.requestUrl!!.encodedPath)
            assertEquals("50", recorded.requestUrl!!.queryParameter("limit"))
            assertEquals("10", recorded.requestUrl!!.queryParameter("offset"))
        }
    }

    @Test
    fun `settlement preview parses net_dues from the full gateway shape`() {
        val body = """
            {
              "group_id": "g1",
              "group_status": "ACTIVE",
              "net_dues": {"aa11": -250.0, "bb22": 250.0},
              "open_payouts": [],
              "proposed_payouts": [{"id": "p1", "amount": 250.0}],
              "orphan_payouts": []
            }
        """.trimIndent()
        withMockServer(200, body) { _, api ->
            val result = runBlocking { safeCall { api.settlementPreview("g1") } }
            assertTrue("expected success, got $result", result is NetworkResult.Success)
            val preview = (result as NetworkResult.Success<SettlementPreviewDto>).data
            assertEquals("g1", preview.groupId)
            assertEquals(-250.0, preview.netDues["aa11"]!!, 0.0)
            assertEquals(250.0, preview.netDues["bb22"]!!, 0.0)
        }
    }

    @Test
    fun `magic link request returns the token on 202`() {
        val body = """
            {
              "email": "anish@example.com",
              "magic_token": "magic-token-123",
              "magic_link": "https://example.com/verify?token=magic-token-123",
              "expires_at": "2026-10-29T14:37:03+00:00"
            }
        """.trimIndent()
        withMockServer(202, body) { _, api ->
            val result = runBlocking {
                safeCall { api.requestMagicLink(MagicLinkRequest("anish@example.com")) }
            }
            assertTrue("expected success, got $result", result is NetworkResult.Success)
            val response = (result as NetworkResult.Success<MagicLinkResponse>).data
            assertEquals("anish@example.com", response.email)
            assertEquals("magic-token-123", response.magicToken)
        }
    }

    @Test
    fun `magic link verify round-trips a session`() {
        withMockServer(200, baseJson) { _, api ->
            val result = runBlocking {
                safeCall { api.verifyMagicLink(MagicLinkVerifyRequest("magic-token-123")) }
            }
            assertTrue("expected success, got $result", result is NetworkResult.Success)
            val tokens = (result as NetworkResult.Success<AuthTokens>).data
            assertEquals("access-jwt", tokens.authToken)
            assertEquals("refresh-jwt", tokens.refreshToken)
            assertEquals("3b5d3c2e-9f6a-4e1b-8c2d-1a2b3c4d5e6f", tokens.authUserId)
        }
    }

    // -- helpers ----------------------------------------------------------

    private fun withMockServer(
        code: Int,
        body: String,
        block: (MockWebServer, ApiService) -> Unit
    ) {
        val server = MockWebServer()
        server.enqueue(
            MockResponse()
                .setResponseCode(code)
                .setHeader("Content-Type", "application/json")
                .setBody(body)
        )
        server.start()
        try {
            val retrofit = Retrofit.Builder()
                .baseUrl(server.url("/"))
                .client(OkHttpClient())
                .addConverterFactory(GsonConverterFactory.create())
                .build()
            block(server, retrofit.create(ApiService::class.java))
        } finally {
            server.shutdown()
        }
    }
}
