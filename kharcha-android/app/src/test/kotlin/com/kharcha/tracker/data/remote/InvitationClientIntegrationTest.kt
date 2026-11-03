package com.kharcha.tracker.data.remote

import com.kharcha.tracker.core.util.NetworkResult
import com.kharcha.tracker.core.util.safeCall
import com.kharcha.tracker.data.remote.dto.GroupDto
import com.kharcha.tracker.data.remote.dto.InvitationDto
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class InvitationClientIntegrationTest {

    private val server = MockWebServer()

    private val invitationJson = """
        {
            "id": "inv-1",
            "group_id": "grp-1",
            "inviter_id": "u-1",
            "invitee_email": "alice@example.com",
            "invite_code": "ABCD1234",
            "status": "pending",
            "created_at": "2026-11-01T10:00:00Z",
            "expires_at": "2026-11-08T10:00:00Z",
            "group_name": "Personal Expenses"
        }
    """.trimIndent()

    @Before fun setUp() {
        server.start()
    }

    @After fun tearDown() {
        server.shutdown()
    }

    @Test fun list_my_invitations_when_payload_has_group_name() {
        server.enqueue(
            MockResponse()
                .setBody(invitationJson)
                .addHeader("Content-Type", "application/json")
        )

        val retrofit = newRetrofit()
        val client = retrofit.create(ApiService::class.java)
        val result = runBlocking { safeCall { client.myInvitations() } }

        assertTrue("expected success, got $result", result is NetworkResult.Success)
        val data = (result as NetworkResult.Success).data
        assertNotNull(data)
        assertEquals(1, data.size)
        assertEquals("Personal Expenses", data[0].groupName)

        val recorded = server.takeRequest()
        assertEquals("GET", recorded.method)
        assertEquals("/api/v1/users/me/invitations", recorded.requestUrl!!.encodedPath)
    }

    @Test fun list_my_invitations_when_payload_lacks_group_name() {
        val jsonOnly = """{"id":"inv-1","group_id":"grp-1","invitee_email":"alice@example.com","status":"pending","invite_code":"ABCD1234"}"""
        server.enqueue(
            MockResponse()
                .setBody(jsonOnly)
                .addHeader("Content-Type", "application/json")
        )

        val retrofit = newRetrofit()
        val client = retrofit.create(ApiService::class.java)
        val result = runBlocking { safeCall { client.myInvitations() } }

        assertTrue("expected success, got $result", result is NetworkResult.Success)
        val data = (result as NetworkResult.Success).data
        assertNotNull(data)
        assertEquals(1, data.size)
        assertNull(data[0].groupName)

        val recorded = server.takeRequest()
        assertEquals("GET", recorded.method)
        assertEquals("/api/v1/users/me/invitations", recorded.requestUrl!!.encodedPath)
    }

    @Test fun accept_invitation_adds_group_to_dashboard() {
        server.enqueue(
            MockResponse()
                .setBody(invitationJson)
                .addHeader("Content-Type", "application/json")
        )
        val groupJson = """{"id":"grp-1","name":"Personal Expenses","currency":"INR","status":"OPEN","invite_code":"ABCD1234","members":["u-1"],"expenses":[],"created_at":"2026-11-01T10:00:00Z","updated_at":"2026-11-01T10:00:00Z"}"""
        server.enqueue(
            MockResponse()
                .setBody(groupJson)
                .addHeader("Content-Type", "application/json")
        )

        val retrofit = newRetrofit()
        val client = retrofit.create(ApiService::class.java)

        val invitationsResult = runBlocking { safeCall { client.myInvitations() } }
        assertTrue("expected success, got $invitationsResult", invitationsResult is NetworkResult.Success)
        val invitations = (invitationsResult as NetworkResult.Success).data
        assertNotNull(invitations)
        assertEquals(1, invitations.size)

        val acceptResult = runBlocking { safeCall { client.acceptInvitation("inv-1") } }
        assertTrue("expected success, got $acceptResult", acceptResult is NetworkResult.Success)
        val acceptedInv = (acceptResult as NetworkResult.Success).data
        assertNotNull(acceptedInv)
        assertEquals("Personal Expenses", acceptedInv.groupName)

        val groupsResult = runBlocking { safeCall { client.myGroups() } }
        assertTrue("expected success, got $groupsResult", groupsResult is NetworkResult.Success)
        val groups = (groupsResult as NetworkResult.Success).data
        assertNotNull(groups)
        assertTrue(groups.any { it.id == "grp-1" })

        val recordedAccept = server.takeRequest()
        assertEquals("POST", recordedAccept.method)
        assertEquals("/api/v1/invitations/inv-1/accept", recordedAccept.requestUrl!!.encodedPath)

        val recordedGroups = server.takeRequest()
        assertEquals("GET", recordedGroups.method)
        assertEquals("/api/v1/groups", recordedGroups.requestUrl!!.encodedPath)
    }

    private fun newRetrofit(): Retrofit {
        return Retrofit.Builder()
            .baseUrl(server.url("/api/v1"))
            .addConverterFactory(GsonConverterFactory.create())
            .client(OkHttpClient())
            .build()
    }
}
