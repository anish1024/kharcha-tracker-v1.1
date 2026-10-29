package com.kharcha.tracker.data.remote

import com.google.gson.JsonParser
import com.kharcha.tracker.core.util.NetworkResult
import com.kharcha.tracker.core.util.safeCall
import com.kharcha.tracker.data.remote.dto.ActivatePlanRequest
import com.kharcha.tracker.data.remote.dto.ProfileUpdateRequest
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/**
 * Stage 7b settings endpoints against a [MockWebServer]: the profile
 * read/edit routes, the subscription status / mock plan activation, and the
 * notification list + mark-all-read - plus the error-surface behaviour for
 * the gateway's 422 responses.
 */
class SettingsClientIntegrationTest {

    private val userJson =
        """{"id":"u1","name":"Sudeep","email":"sudeep@example.com","avatar_url":null}"""

    private val trialJson =
        """
        {
          "trial_ends_at": "2026-01-20T00:00:00Z",
          "plan": "none",
          "plan_status": "none",
          "plan_ends_at": null,
          "entries_remaining": null,
          "is_limited": false,
          "is_expired": false
        }
        """

    private val activeJson =
        """
        {
          "trial_ends_at": null,
          "plan": "yearly",
          "plan_status": "active",
          "plan_ends_at": "2026-12-31T23:59:59Z",
          "entries_remaining": null,
          "is_limited": false,
          "is_expired": false
        }
        """

    private val notificationsJson =
        """
        [
          {"id":"n1","kind":"GROUP_SETTLED","message":null,
           "is_read":false,"created_at":"2026-01-05T09:00:00Z"}
        ]
        """

    // -- profile ---------------------------------------------------------------

    @Test
    fun `me GETs the signed-in profile`() {
        withMockServer(200, userJson) { server, api ->
            val result = runBlocking { safeCall { api.me() } }
            assertTrue("expected success, got $result", result is NetworkResult.Success)
            val user = (result as NetworkResult.Success).data
            assertEquals("u1", user.id)
            assertEquals("Sudeep", user.name)
            assertEquals("sudeep@example.com", user.email)
            assertNull(user.avatarUrl)

            val recorded = server.takeRequest()
            assertEquals("GET", recorded.method)
            assertEquals("/api/v1/users/me", recorded.requestUrl!!.encodedPath)
        }
    }

    @Test
    fun `update profile PATCHes only the edited fields`() {
        withMockServer(200, userJson) { server, api ->
            val result = runBlocking {
                safeCall { api.updateProfile(ProfileUpdateRequest(name = "Sudeep K", avatarUrl = null)) }
            }
            assertTrue("expected success, got $result", result is NetworkResult.Success)

            val recorded = server.takeRequest()
            assertEquals("PATCH", recorded.method)
            assertEquals("/api/v1/users/me", recorded.requestUrl!!.encodedPath)
            val body = JsonParser.parseString(recorded.body.readUtf8()).asJsonObject
            assertEquals("Sudeep K", body.get("name").asString)
            assertFalse(body.has("avatar_url"))
        }
    }

    @Test
    fun `update profile with no edits sends an empty object`() {
        withMockServer(200, userJson) { server, api ->
            val result = runBlocking { safeCall { api.updateProfile(ProfileUpdateRequest(null, null)) } }
            assertTrue("expected success, got $result", result is NetworkResult.Success)
            assertEquals("{}", server.takeRequest().body.readUtf8())
        }
    }

    @Test
    fun `update profile 422 for a blank name surfaces the server message`() {
        withMockServer(422, """{"detail":"name must not be blank"}""") { _, api ->
            val result = runBlocking {
                safeCall { api.updateProfile(ProfileUpdateRequest(name = "", avatarUrl = null)) }
            }
            assertTrue("expected error, got $result", result is NetworkResult.Error)
            val error = result as NetworkResult.Error
            assertEquals(422, error.code)
            assertEquals("name must not be blank", error.message)
        }
    }

    // -- subscription -------------------------------------------------------------

    @Test
    fun `subscription GETs and parses the trial status`() {
        withMockServer(200, trialJson) { server, api ->
            val result = runBlocking { safeCall { api.subscriptionStatus() } }
            assertTrue("expected success, got $result", result is NetworkResult.Success)
            val status = (result as NetworkResult.Success).data
            assertEquals("none", status.plan)
            assertEquals("none", status.planStatus)
            assertEquals("2026-01-20T00:00:00Z", status.trialEndsAt)
            assertNull(status.entriesRemaining)

            val recorded = server.takeRequest()
            assertEquals("GET", recorded.method)
            assertEquals("/api/v1/users/me/subscription", recorded.requestUrl!!.encodedPath)
        }
    }

    @Test
    fun `activate plan POSTs the plan and returns the fresh status`() {
        withMockServer(200, activeJson) { server, api ->
            val result = runBlocking { safeCall { api.activatePlan(ActivatePlanRequest(plan = "yearly")) } }
            assertTrue("expected success, got $result", result is NetworkResult.Success)
            val status = (result as NetworkResult.Success).data
            assertEquals("active", status.planStatus)
            assertEquals("yearly", status.plan)
            assertEquals("2026-12-31T23:59:59Z", status.planEndsAt)

            val recorded = server.takeRequest()
            assertEquals("POST", recorded.method)
            assertEquals("/api/v1/subscription", recorded.requestUrl!!.encodedPath)
            val body = JsonParser.parseString(recorded.body.readUtf8()).asJsonObject
            assertEquals("yearly", body.get("plan").asString)
        }
    }

    @Test
    fun `activate plan 422 for an invalid plan surfaces the server message`() {
        withMockServer(
            422,
            """{"detail":"Invalid plan; choose one of: 6month, lifetime, monthly, yearly"}"""
        ) { _, api ->
            val result = runBlocking { safeCall { api.activatePlan(ActivatePlanRequest(plan = "weekly")) } }
            assertTrue("expected error, got $result", result is NetworkResult.Error)
            val error = result as NetworkResult.Error
            assertEquals(422, error.code)
            assertEquals(
                "Invalid plan; choose one of: 6month, lifetime, monthly, yearly",
                error.message
            )
        }
    }

    // -- notifications ---------------------------------------------------------------

    @Test
    fun `notifications GETs with the unread_only query`() {
        withMockServer(200, notificationsJson) { server, api ->
            val result = runBlocking { safeCall { api.notifications(unreadOnly = true) } }
            assertTrue("expected success, got $result", result is NetworkResult.Success)
            val notes = (result as NetworkResult.Success).data
            assertEquals(1, notes.size)
            assertEquals("GROUP_SETTLED", notes.first().kind)

            val recorded = server.takeRequest()
            assertEquals("GET", recorded.method)
            assertEquals("/api/v1/users/me/notifications", recorded.requestUrl!!.encodedPath)
            assertEquals("true", recorded.requestUrl!!.queryParameter("unread_only"))
        }
    }

    @Test
    fun `mark all read POSTs and parses the bare count`() {
        withMockServer(200, "5") { server, api ->
            val result = runBlocking { safeCall { api.markNotificationsRead() } }
            assertTrue("expected success, got $result", result is NetworkResult.Success)
            assertEquals(5, (result as NetworkResult.Success).data)

            val recorded = server.takeRequest()
            assertEquals("POST", recorded.method)
            assertEquals("/api/v1/users/me/notifications/read", recorded.requestUrl!!.encodedPath)
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
