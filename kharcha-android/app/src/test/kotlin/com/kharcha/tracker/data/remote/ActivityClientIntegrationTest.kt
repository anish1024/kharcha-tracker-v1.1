package com.kharcha.tracker.data.remote

import com.kharcha.tracker.core.util.NetworkResult
import com.kharcha.tracker.core.util.headerInt
import com.kharcha.tracker.core.util.safeCall
import com.kharcha.tracker.core.util.safeCallWithHeaders
import com.kharcha.tracker.data.repository.ActivityRepository
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/**
 * Stage 7b activity endpoints against a [MockWebServer]: the group picker
 * and member-lookup routes, the paginated audit-log query (limit/offset +
 * optional date range), the `X-Total-Count` header surfaced through
 * [safeCallWithHeaders], the repository's 0-based page -> offset mapping,
 * and the 422 / 403 error surfaces.
 */
class ActivityClientIntegrationTest {

    private val activityJson =
        """
        [
          {"id":"al1","event":"CREATE","entity_type":"group","new_value":"Flatmates",
           "actor_name":"Sudeep","created_at":"2026-01-05T09:00:00Z"},
          {"id":"al2","event":"UPDATE","entity_type":"expense",
           "created_at":"2026-01-06T10:00:00Z"}
        ]
        """

    private val membersJson =
        """[{"id":"aa11","name":"Sudeep"},{"id":"bb22","name":null}]"""

    private val groupsJson =
        """[{"id":"g1","name":"Flatmates","currency":"INR","members":["aa11","bb22"]}]"""

    // -- picker + member lookup ----------------------------------------------

    @Test
    fun `groups GETs the picker list`() {
        withMockServer(200, groupsJson) { server, api ->
            val result = runBlocking { safeCall { api.myGroups() } }
            assertTrue("expected success, got $result", result is NetworkResult.Success)
            val groups = (result as NetworkResult.Success).data
            assertEquals("g1", groups.first().id)
            assertEquals("Flatmates", groups.first().name)
            assertEquals(2, groups.first().members.size)

            val recorded = server.takeRequest()
            assertEquals("GET", recorded.method)
            assertEquals("/api/v1/groups", recorded.requestUrl!!.encodedPath)
        }
    }

    @Test
    fun `members GETs the group member profiles`() {
        withMockServer(200, membersJson) { server, api ->
            val result = runBlocking { safeCall { api.groupMembers("g1") } }
            assertTrue("expected success, got $result", result is NetworkResult.Success)
            val members = (result as NetworkResult.Success).data
            assertEquals("aa11", members.first().id)
            assertEquals("Sudeep", members.first().name)
            assertNull(members[1].name)

            val recorded = server.takeRequest()
            assertEquals("GET", recorded.method)
            assertEquals("/api/v1/groups/g1/members", recorded.requestUrl!!.encodedPath)
        }
    }

    // -- paginated audit log ---------------------------------------------------

    @Test
    fun `activity sends limit and offset for the page`() {
        withMockServer(200, activityJson, mapOf("X-Total-Count" to "42")) { server, api ->
            val result = runBlocking {
                safeCallWithHeaders { api.groupActivity("g1", limit = 20, offset = 20) }
            }
            assertTrue("expected success, got $result", result is NetworkResult.Success)
            val page = (result as NetworkResult.Success).data
            assertEquals(2, page.body.size)
            assertEquals("al1", page.body.first().id)
            assertEquals(42, page.headerInt("X-Total-Count"))

            val recorded = server.takeRequest()
            assertEquals("GET", recorded.method)
            assertEquals("/api/v1/groups/g1/activity", recorded.requestUrl!!.encodedPath)
            assertEquals("20", recorded.requestUrl!!.queryParameter("limit"))
            assertEquals("20", recorded.requestUrl!!.queryParameter("offset"))
            assertNull(recorded.requestUrl!!.queryParameter("date_from"))
            assertNull(recorded.requestUrl!!.queryParameter("date_to"))
        }
    }

    @Test
    fun `activity sends the date range when provided`() {
        withMockServer(200, activityJson, mapOf("X-Total-Count" to "2")) { server, api ->
            runBlocking {
                safeCallWithHeaders {
                    api.groupActivity(
                        "g1",
                        limit = 20,
                        offset = 0,
                        dateFrom = "2026-01-01T00:00:00",
                        dateTo = "2026-01-31T23:59:59.999"
                    )
                }
            }

            val recorded = server.takeRequest()
            assertEquals("2026-01-01T00:00:00", recorded.requestUrl!!.queryParameter("date_from"))
            assertEquals("2026-01-31T23:59:59.999", recorded.requestUrl!!.queryParameter("date_to"))
        }
    }

    @Test
    fun `repository multiplies the 0-based page into the offset`() {
        withMockServer(200, activityJson, mapOf("X-Total-Count" to "42")) { server, api ->
            val repo = ActivityRepository(api)
            val result = runBlocking { repo.activity("g1", page = 1, pageSize = 20) }
            assertTrue("expected success, got $result", result is NetworkResult.Success)
            val page = (result as NetworkResult.Success).data
            assertEquals(2, page.logs.size)
            assertEquals(42, page.total)

            val recorded = server.takeRequest()
            assertEquals("20", recorded.requestUrl!!.queryParameter("limit"))
            assertEquals("20", recorded.requestUrl!!.queryParameter("offset"))
        }
    }

    @Test
    fun `repository falls back to the page size when the total header is absent`() {
        withMockServer(200, activityJson) { _, api ->
            val repo = ActivityRepository(api)
            val result = runBlocking { repo.activity("g1", page = 0, pageSize = 20) }
            assertTrue("expected success, got $result", result is NetworkResult.Success)
            assertEquals(2, (result as NetworkResult.Success).data.total)
        }
    }

    // -- error surfaces ----------------------------------------------------------

    @Test
    fun `activity 422 for a bad date range surfaces the server message`() {
        withMockServer(422, """{"detail":"Invalid date_from or date_to"}""") { _, api ->
            val result = runBlocking {
                safeCallWithHeaders {
                    api.groupActivity("g1", limit = 20, offset = 0, dateFrom = "bad")
                }
            }
            assertTrue("expected error, got $result", result is NetworkResult.Error)
            val error = result as NetworkResult.Error
            assertEquals(422, error.code)
            assertEquals("Invalid date_from or date_to", error.message)
        }
    }

    @Test
    fun `activity 403 for a non-member surfaces the server message`() {
        withMockServer(403, """{"detail":"You are not a member of this group"}""") { _, api ->
            val result = runBlocking {
                safeCallWithHeaders { api.groupActivity("g1", limit = 20, offset = 0) }
            }
            assertTrue("expected error, got $result", result is NetworkResult.Error)
            val error = result as NetworkResult.Error
            assertEquals(403, error.code)
            assertEquals("You are not a member of this group", error.message)
        }
    }

    // -- helpers ----------------------------------------------------------

    private fun withMockServer(
        code: Int,
        body: String,
        headers: Map<String, String> = emptyMap(),
        block: (MockWebServer, ApiService) -> Unit
    ) {
        val server = MockWebServer()
        server.enqueue(
            MockResponse()
                .setResponseCode(code)
                .setHeader("Content-Type", "application/json")
                .apply { headers.forEach { (name, value) -> setHeader(name, value) } }
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
