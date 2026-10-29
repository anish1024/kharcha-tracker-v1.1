package com.kharcha.tracker.data.remote

import com.google.gson.JsonParser
import com.kharcha.tracker.core.util.NetworkResult
import com.kharcha.tracker.core.util.safeCall
import com.kharcha.tracker.data.remote.dto.ExpenseUpdateRequest
import com.kharcha.tracker.data.remote.dto.ExpensePaymentDto
import com.kharcha.tracker.data.remote.dto.PayoutActionRequest
import com.kharcha.tracker.data.remote.dto.SettleRequest
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
 * Stage 7a endpoints against a [MockWebServer]: asserts the exact
 * method, path and body the client sends for the expense edit/delete
 * routes, the settle action, the payout list and the four payout-action
 * transitions, and lock/unlock - plus the error-surface behaviour for
 * the gateway's 422 (string and array `detail`) and 403 (object
 * `detail`) responses.
 */
class SettlementClientIntegrationTest {

    private val expenseJson =
        """{"trans_id":17,"group_id":"g1","date_time":"2025-11-01T10:30:00Z","expn_ctgry":"FOOD","expn_desc":"Groceries","expn_amnt":450.5,"paid_by":[{"user_id":"aa11","amt":450.5}]}"""

    private val payoutJson =
        """{"id":"p1","group_id":"g1","payor_id":"aa11","payee_id":"bb22","amt":1200.5,"status":"PENDING"}"""

    private val groupJson =
        """{"id":"g1","name":"Flatmates","status":"OPEN","currency":"INR","members":["aa11","bb22"]}"""

    // -- expense read/edit/delete -------------------------------------------

    @Test
    fun `get expense GETs the trans_id route`() {
        withMockServer(200, expenseJson) { server, api ->
            val result = runBlocking { safeCall { api.getExpense(17) } }
            assertTrue("expected success, got $result", result is NetworkResult.Success)
            val expense = (result as NetworkResult.Success).data
            assertEquals(17, expense.transId)
            assertEquals(450.5, expense.amount, 0.0)

            val recorded = server.takeRequest()
            assertEquals("GET", recorded.method)
            assertEquals("/api/v1/expenses/17", recorded.requestUrl!!.encodedPath)
        }
    }

    @Test
    fun `update expense PATCHes only the edited fields`() {
        withMockServer(200, expenseJson) { server, api ->
            val result = runBlocking {
                safeCall {
                    api.updateExpense(
                        17,
                        ExpenseUpdateRequest(
                            dateTime = "2025-11-02",
                            category = "TRAVEL",
                            description = "Train ticket",
                            paidBy = listOf(ExpensePaymentDto(userId = "bb22", amount = 450.5))
                        )
                    )
                }
            }
            assertTrue("expected success, got $result", result is NetworkResult.Success)

            val recorded = server.takeRequest()
            assertEquals("PATCH", recorded.method)
            assertEquals("/api/v1/expenses/17", recorded.requestUrl!!.encodedPath)
            val body = JsonParser.parseString(recorded.body.readUtf8()).asJsonObject
            assertEquals("2025-11-02", body.get("date_time").asString)
            assertEquals("TRAVEL", body.get("expn_ctgry").asString)
            assertEquals("Train ticket", body.get("expn_desc").asString)
            val paidBy = body.getAsJsonArray("paid_by")
            assertEquals(1, paidBy.size())
            assertEquals("bb22", paidBy[0].asJsonObject.get("user_id").asString)
            assertEquals(450.5, paidBy[0].asJsonObject.get("amt").asDouble, 0.0)
        }
    }

    @Test
    fun `update expense with an unedited form sends an empty object`() {
        withMockServer(200, expenseJson) { server, api ->
            val result = runBlocking {
                safeCall { api.updateExpense(17, ExpenseUpdateRequest()) }
            }
            assertTrue("expected success, got $result", result is NetworkResult.Success)
            assertEquals("{}", server.takeRequest().body.readUtf8())
        }
    }

    @Test
    fun `delete expense DELETEs the trans_id route`() {
        withMockServer(200, "true") { server, api ->
            val result = runBlocking { safeCall { api.deleteExpense(17) } }
            assertTrue("expected success, got $result", result is NetworkResult.Success)
            assertEquals(true, (result as NetworkResult.Success).data)

            val recorded = server.takeRequest()
            assertEquals("DELETE", recorded.method)
            assertEquals("/api/v1/expenses/17", recorded.requestUrl!!.encodedPath)
        }
    }

    @Test
    fun `update expense 422 while the group is not OPEN surfaces the message`() {
        withMockServer(422, """{"detail":"Cannot update expense while group is LOCKED"}""") {
            _, api ->
            val result = runBlocking {
                safeCall { api.updateExpense(17, ExpenseUpdateRequest(description = "x")) }
            }
            assertTrue("expected error, got $result", result is NetworkResult.Error)
            val error = result as NetworkResult.Error
            assertEquals(422, error.code)
            assertEquals("Cannot update expense while group is LOCKED", error.message)
        }
    }

    @Test
    fun `update expense 422 array detail surfaces the first msg`() {
        withMockServer(
            422,
            """{"detail":[{"loc":["body","expn_ctgry"],"msg":"Invalid expense category: FOO","type":"value_error"}]}"""
        ) { _, api ->
            val result = runBlocking {
                safeCall { api.updateExpense(17, ExpenseUpdateRequest(category = "FOO")) }
            }
            assertTrue("expected error, got $result", result is NetworkResult.Error)
            val error = result as NetworkResult.Error
            assertEquals(422, error.code)
            assertEquals("Invalid expense category: FOO", error.message)
        }
    }

    // -- settle & payouts -----------------------------------------------------

    @Test
    fun `settle group POSTs an empty body and parses the payout list`() {
        withMockServer(200, "[$payoutJson]") { server, api ->
            val result = runBlocking { safeCall { api.settleGroup("g1", SettleRequest()) } }
            assertTrue("expected success, got $result", result is NetworkResult.Success)
            val payouts = (result as NetworkResult.Success).data
            assertEquals(1, payouts.size)
            assertEquals("p1", payouts.first().id)
            assertEquals(1200.5, payouts.first().amount, 0.0)

            val recorded = server.takeRequest()
            assertEquals("POST", recorded.method)
            assertEquals("/api/v1/groups/g1/settle", recorded.requestUrl!!.encodedPath)
            assertEquals("{}", recorded.body.readUtf8())
        }
    }

    @Test
    fun `payouts list GETs the group payouts`() {
        withMockServer(200, "[$payoutJson]") { server, api ->
            val result = runBlocking { safeCall { api.groupPayouts("g1") } }
            assertTrue("expected success, got $result", result is NetworkResult.Success)
            assertEquals(1, (result as NetworkResult.Success).data.size)

            val recorded = server.takeRequest()
            assertEquals("GET", recorded.method)
            assertEquals("/api/v1/groups/g1/payouts", recorded.requestUrl!!.encodedPath)
        }
    }

    @Test
    fun `mark payout paid POSTs the optional evidence`() {
        withMockServer(200, payoutJson) { server, api ->
            val result = runBlocking {
                safeCall { api.markPayoutPaid("g1", "p1", PayoutActionRequest(evidence = "UPI ref 4821")) }
            }
            assertTrue("expected success, got $result", result is NetworkResult.Success)
            assertEquals("p1", (result as NetworkResult.Success).data.id)

            val recorded = server.takeRequest()
            assertEquals("POST", recorded.method)
            assertEquals("/api/v1/groups/g1/payouts/p1/paid", recorded.requestUrl!!.encodedPath)
            val body = JsonParser.parseString(recorded.body.readUtf8()).asJsonObject
            assertEquals("UPI ref 4821", body.get("evidence").asString)
        }
    }

    @Test
    fun `confirm payout settled POSTs without a reason`() {
        withMockServer(200, payoutJson) { server, api ->
            val result = runBlocking {
                safeCall { api.confirmPayoutSettled("g1", "p1", PayoutActionRequest()) }
            }
            assertTrue("expected success, got $result", result is NetworkResult.Success)

            val recorded = server.takeRequest()
            assertEquals("POST", recorded.method)
            assertEquals("/api/v1/groups/g1/payouts/p1/settled", recorded.requestUrl!!.encodedPath)
            assertEquals("{}", recorded.body.readUtf8())
        }
    }

    @Test
    fun `fail payout POSTs the required reason`() {
        withMockServer(200, payoutJson) { server, api ->
            val result = runBlocking {
                safeCall { api.failPayout("g1", "p1", PayoutActionRequest(reason = "wrong amount")) }
            }
            assertTrue("expected success, got $result", result is NetworkResult.Success)

            val recorded = server.takeRequest()
            assertEquals("POST", recorded.method)
            assertEquals("/api/v1/groups/g1/payouts/p1/failed", recorded.requestUrl!!.encodedPath)
            val body = JsonParser.parseString(recorded.body.readUtf8()).asJsonObject
            assertEquals("wrong amount", body.get("reason").asString)
        }
    }

    @Test
    fun `fail payout with a blank reason 422s with the server message`() {
        withMockServer(422, """{"detail":"A reason is required to mark this payout failed."}""") {
            _, api ->
            val result = runBlocking {
                safeCall { api.failPayout("g1", "p1", PayoutActionRequest(reason = "")) }
            }
            assertTrue("expected error, got $result", result is NetworkResult.Error)
            val error = result as NetworkResult.Error
            assertEquals(422, error.code)
            assertEquals("A reason is required to mark this payout failed.", error.message)
        }
    }

    @Test
    fun `cancel payout POSTs the required reason`() {
        withMockServer(200, payoutJson) { server, api ->
            val result = runBlocking {
                safeCall { api.cancelPayout("g1", "p1", PayoutActionRequest(reason = "no longer needed")) }
            }
            assertTrue("expected success, got $result", result is NetworkResult.Success)

            val recorded = server.takeRequest()
            assertEquals("POST", recorded.method)
            assertEquals("/api/v1/groups/g1/payouts/p1/cancel", recorded.requestUrl!!.encodedPath)
            val body = JsonParser.parseString(recorded.body.readUtf8()).asJsonObject
            assertEquals("no longer needed", body.get("reason").asString)
        }
    }

    // -- lock / unlock ---------------------------------------------------------

    @Test
    fun `lock group POSTs to the lock route`() {
        withMockServer(200, """{"id":"g1","name":"Flatmates","status":"LOCKED","currency":"INR"}""") {
            server, api ->
            val result = runBlocking { safeCall { api.lockGroup("g1") } }
            assertTrue("expected success, got $result", result is NetworkResult.Success)
            assertEquals("LOCKED", (result as NetworkResult.Success).data.status)

            val recorded = server.takeRequest()
            assertEquals("POST", recorded.method)
            assertEquals("/api/v1/groups/g1/lock", recorded.requestUrl!!.encodedPath)
        }
    }

    @Test
    fun `unlock group POSTs to the unlock route`() {
        withMockServer(200, groupJson) { server, api ->
            val result = runBlocking { safeCall { api.unlockGroup("g1") } }
            assertTrue("expected success, got $result", result is NetworkResult.Success)
            assertEquals("OPEN", (result as NetworkResult.Success).data.status)

            val recorded = server.takeRequest()
            assertEquals("POST", recorded.method)
            assertEquals("/api/v1/groups/g1/unlock", recorded.requestUrl!!.encodedPath)
        }
    }

    @Test
    fun `lock group 403 object detail surfaces the inner message`() {
        withMockServer(
            403,
            """{"detail":{"code":"SUBSCRIPTION_LIMIT","message":"Monthly expense limit reached"}}"""
        ) { _, api ->
            val result = runBlocking { safeCall { api.lockGroup("g1") } }
            assertTrue("expected error, got $result", result is NetworkResult.Error)
            val error = result as NetworkResult.Error
            assertEquals(403, error.code)
            assertEquals("Monthly expense limit reached", error.message)
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
