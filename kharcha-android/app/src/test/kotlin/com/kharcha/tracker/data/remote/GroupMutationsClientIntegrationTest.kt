package com.kharcha.tracker.data.remote

import com.google.gson.JsonParser
import com.kharcha.tracker.core.util.NetworkResult
import com.kharcha.tracker.core.util.safeCall
import com.kharcha.tracker.data.remote.dto.AddExpenseRequest
import com.kharcha.tracker.data.remote.dto.CreateGroupRequest
import com.kharcha.tracker.data.remote.dto.ExpenseDto
import com.kharcha.tracker.data.remote.dto.ExpenseShareDto
import com.kharcha.tracker.data.remote.dto.GroupDto
import com.kharcha.tracker.data.remote.dto.GroupMemberDto
import com.kharcha.tracker.data.remote.dto.InviteAcceptRequest
import com.kharcha.tracker.data.remote.dto.InviteCodeResponse
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/**
 * Round-trips the Stage 6 mutation endpoints (create/join/leave group,
 * invite codes, members, add-expense) through the real Retrofit + Gson
 * stack against a [MockWebServer], asserting the exact method, path and
 * body the app sends and the exact response shapes it consumes - including
 * FastAPI's OBJECT `detail` (403 subscription limit) and Pydantic ARRAY
 * `detail` (422 validation).
 */
class GroupMutationsClientIntegrationTest {

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
          "expn_splt": [{"user_id": "aa11", "percentage": 60.0}, {"user_id": "bb22", "percentage": 40.0}],
          "paid_by": [{"user_id": "aa11", "amt": 450.5}],
          "expn_bill_screenshot": "",
          "expn_payment_screenshot": "",
          "status": "ACTIVE"
        }
    """.trimIndent()

    @Test
    fun `create group posts name and currency and parses the created group`() {
        withMockServer(201, groupJson) { server, api ->
            val result = runBlocking {
                safeCall { api.createGroup(CreateGroupRequest("Flatmates", "INR")) }
            }
            assertTrue("expected success, got $result", result is NetworkResult.Success)
            val group = (result as NetworkResult.Success<GroupDto>).data
            assertEquals("g1", group.id)
            assertEquals("Flatmates", group.name)

            val recorded = server.takeRequest()
            assertEquals("POST", recorded.method)
            assertEquals("/api/v1/groups", recorded.requestUrl!!.encodedPath)
            val body = JsonParser.parseString(recorded.body.readUtf8()).asJsonObject
            assertEquals("Flatmates", body.get("name").asString)
            assertEquals("INR", body.get("currency").asString)
        }
    }

    @Test
    fun `create group surfaces a 422 validation message`() {
        withMockServer(422, """{"detail":"name must be at least 2 characters"}""") { _, api ->
            val result = runBlocking {
                safeCall { api.createGroup(CreateGroupRequest("A", "INR")) }
            }
            assertTrue("expected error, got $result", result is NetworkResult.Error)
            val error = result as NetworkResult.Error
            assertEquals(422, error.code)
            assertEquals("name must be at least 2 characters", error.message)
        }
    }

    @Test
    fun `accept invite posts the code and round-trips the group`() {
        withMockServer(200, groupJson) { server, api ->
            val result = runBlocking {
                safeCall { api.acceptInvite("g1", InviteAcceptRequest("ABCD1234")) }
            }
            assertTrue("expected success, got $result", result is NetworkResult.Success)
            assertEquals("g1", (result as NetworkResult.Success<GroupDto>).data.id)

            val recorded = server.takeRequest()
            assertEquals("POST", recorded.method)
            assertEquals("/api/v1/groups/g1/invite/accept", recorded.requestUrl!!.encodedPath)
            val body = JsonParser.parseString(recorded.body.readUtf8()).asJsonObject
            assertEquals("ABCD1234", body.get("invite_code").asString)
        }
    }

    @Test
    fun `accept invite surfaces the 404 invalid code message`() {
        withMockServer(404, """{"detail":"Invalid invite code"}""") { _, api ->
            val result = runBlocking {
                safeCall { api.acceptInvite("g1", InviteAcceptRequest("WRONG9")) }
            }
            assertTrue("expected error, got $result", result is NetworkResult.Error)
            val error = result as NetworkResult.Error
            assertEquals(404, error.code)
            assertEquals("Invalid invite code", error.message)
        }
    }

    @Test
    fun `group members round-trips the member list`() {
        withMockServer(
            200,
            """
            [
              {"id":"aa11","name":"Anish","email":"anish@example.com","avatar_url":null},
              {"id":"bb22","name":"bb22","email":"","avatar_url":null}
            ]
            """.trimIndent()
        ) { server, api ->
            val result = runBlocking { safeCall { api.groupMembers("g1") } }
            assertTrue("expected success, got $result", result is NetworkResult.Success)
            val members = (result as NetworkResult.Success<List<GroupMemberDto>>).data
            assertEquals(2, members.size)
            assertEquals("Anish", members[0].name)
            assertEquals("anish@example.com", members[0].email)
            assertEquals("bb22", members[1].name)
            assertEquals("", members[1].email)

            val recorded = server.takeRequest()
            assertEquals("GET", recorded.method)
            assertEquals("/api/v1/groups/g1/members", recorded.requestUrl!!.encodedPath)
        }
    }

    @Test
    fun `leave group deletes the membership`() {
        withMockServer(200, groupJson) { server, api ->
            val result = runBlocking { safeCall { api.leaveGroup("g1") } }
            assertTrue("expected success, got $result", result is NetworkResult.Success)
            assertEquals("g1", (result as NetworkResult.Success<GroupDto>).data.id)

            val recorded = server.takeRequest()
            assertEquals("DELETE", recorded.method)
            assertEquals("/api/v1/groups/g1/members/me", recorded.requestUrl!!.encodedPath)
        }
    }

    @Test
    fun `leave group surfaces the outstanding balance refusal`() {
        withMockServer(
            422,
            """{"detail":"Cannot leave group with outstanding balance: -250.0"}"""
        ) { _, api ->
            val result = runBlocking { safeCall { api.leaveGroup("g1") } }
            assertTrue("expected error, got $result", result is NetworkResult.Error)
            val error = result as NetworkResult.Error
            assertEquals(422, error.code)
            assertTrue(error.message, error.message.contains("outstanding balance"))
        }
    }

    @Test
    fun `generate invite code returns the derived code`() {
        withMockServer(200, """{"invite_code":"ABCD1234"}""") { server, api ->
            val result = runBlocking { safeCall { api.generateInviteCode("g1") } }
            assertTrue("expected success, got $result", result is NetworkResult.Success)
            assertEquals(
                "ABCD1234",
                (result as NetworkResult.Success<InviteCodeResponse>).data.inviteCode
            )

            val recorded = server.takeRequest()
            assertEquals("POST", recorded.method)
            assertEquals("/api/v1/groups/g1/invite", recorded.requestUrl!!.encodedPath)
        }
    }

    @Test
    fun `add expense equal split sends no split and no payer`() {
        withMockServer(201, expenseJson) { server, api ->
            val result = runBlocking {
                safeCall { api.addExpense(AddExpenseRequest(groupId = "g1", amount = 450.5)) }
            }
            assertTrue("expected success, got $result", result is NetworkResult.Success)
            assertEquals(
                450.5,
                (result as NetworkResult.Success<ExpenseDto>).data.amount,
                0.0
            )

            val recorded = server.takeRequest()
            assertEquals("POST", recorded.method)
            assertEquals("/api/v1/expenses", recorded.requestUrl!!.encodedPath)
            val body = JsonParser.parseString(recorded.body.readUtf8()).asJsonObject
            assertEquals("g1", body.get("group_id").asString)
            assertEquals(450.5, body.get("expn_amnt").asDouble, 0.0)
            assertFalse("expn_splt must be absent for an equal split", body.has("expn_splt"))
            assertFalse("paid_by_user_id must be absent for the default payer", body.has("paid_by_user_id"))
        }
    }

    @Test
    fun `add expense percentage split sends shares and payer`() {
        withMockServer(201, expenseJson) { server, api ->
            val result = runBlocking {
                safeCall {
                    api.addExpense(
                        AddExpenseRequest(
                            groupId = "g1",
                            amount = 450.5,
                            description = "Groceries",
                            category = "FOOD",
                            paidByUserId = "aa11",
                            shares = listOf(
                                ExpenseShareDto(userId = "aa11", percentage = 60.0),
                                ExpenseShareDto(userId = "bb22", percentage = 40.0)
                            )
                        )
                    )
                }
            }
            assertTrue("expected success, got $result", result is NetworkResult.Success)

            val recorded = server.takeRequest()
            assertEquals("POST", recorded.method)
            assertEquals("/api/v1/expenses", recorded.requestUrl!!.encodedPath)
            val body = JsonParser.parseString(recorded.body.readUtf8()).asJsonObject
            assertEquals("Groceries", body.get("expn_desc").asString)
            assertEquals("FOOD", body.get("expn_ctgry").asString)
            assertEquals("aa11", body.get("paid_by_user_id").asString)
            val shares = body.getAsJsonArray("expn_splt")
            assertEquals(2, shares.size())
            assertEquals("aa11", shares[0].asJsonObject.get("user_id").asString)
            assertEquals(60.0, shares[0].asJsonObject.get("percentage").asDouble, 0.0)
            assertEquals(40.0, shares[1].asJsonObject.get("percentage").asDouble, 0.0)
        }
    }

    @Test
    fun `add expense 403 object detail surfaces the inner message`() {
        withMockServer(
            403,
            """{"detail":{"code":"SUBSCRIPTION_LIMIT","message":"Monthly expense limit reached"}}"""
        ) { _, api ->
            val result = runBlocking {
                safeCall { api.addExpense(AddExpenseRequest(groupId = "g1", amount = 10.0)) }
            }
            assertTrue("expected error, got $result", result is NetworkResult.Error)
            val error = result as NetworkResult.Error
            assertEquals(403, error.code)
            assertEquals("Monthly expense limit reached", error.message)
        }
    }

    @Test
    fun `add expense 422 array detail surfaces the first msg`() {
        withMockServer(
            422,
            """{"detail":[{"loc":["body","expn_amnt"],"msg":"Value must be greater than 0","type":"greater_than"}]}"""
        ) { _, api ->
            val result = runBlocking {
                safeCall { api.addExpense(AddExpenseRequest(groupId = "g1", amount = 10.0)) }
            }
            assertTrue("expected error, got $result", result is NetworkResult.Error)
            val error = result as NetworkResult.Error
            assertEquals(422, error.code)
            assertEquals("Value must be greater than 0", error.message)
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
