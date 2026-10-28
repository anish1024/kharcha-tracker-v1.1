package com.kharcha.tracker.data.remote

import com.kharcha.tracker.core.util.NetworkResult
import com.kharcha.tracker.core.util.safeCall
import com.kharcha.tracker.data.remote.dto.AuthTokens
import com.kharcha.tracker.data.remote.dto.LoginRequest
import com.kharcha.tracker.data.remote.dto.UserDto
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
 * Round-trips the real Retrofit + Gson stack against a local [MockWebServer]
 * that answers with the exact JSON the FastAPI gateway emits. Validates the
 * auth client paths end-to-end, minus the Android runtime.
 */
class AuthClientIntegrationTest {

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
    fun `login success round-trips through Retrofit and Gson`() {
        withMockServer(200, baseJson) { api ->
            val result = runBlocking {
                safeCall { api.login(LoginRequest("anish@example.com", "secret123")) }
            }
            assertTrue("expected success, got $result", result is NetworkResult.Success)
            val tokens = (result as NetworkResult.Success<AuthTokens>).data
            assertEquals("access-jwt", tokens.authToken)
            assertEquals("refresh-jwt", tokens.refreshToken)
            assertEquals("3b5d3c2e-9f6a-4e1b-8c2d-1a2b3c4d5e6f", tokens.authUserId)
        }
    }

    @Test
    fun `login with invalid credentials surfaces the server detail`() {
        val body = """{"detail":"Invalid credentials"}"""
        withMockServer(401, body) { api ->
            val result = runBlocking {
                safeCall { api.login(LoginRequest("anish@example.com", "wrong")) }
            }
            assertTrue("expected error, got $result", result is NetworkResult.Error)
            val error = result as NetworkResult.Error
            assertEquals(401, error.code)
            assertEquals("Invalid credentials", error.message)
        }
    }

    @Test
    fun `users me round-trips the User shape`() {
        val body = """
            {
              "id": "3b5d3c2e-9f6a-4e1b-8c2d-1a2b3c4d5e6f",
              "name": "Anish",
              "password": "",
              "email": "anish@example.com",
              "avatar_url": null
            }
        """.trimIndent()
        withMockServer(200, body) { api ->
            val result = runBlocking { safeCall { api.me() } }
            assertTrue("expected success, got $result", result is NetworkResult.Success)
            val user = (result as NetworkResult.Success<UserDto>).data
            assertEquals("Anish", user.name)
            assertEquals("anish@example.com", user.email)
        }
    }

    @Test
    fun `users me with a rejected session yields 401`() {
        val body = """{"detail":"Invalid or expired token"}"""
        withMockServer(401, body) { api ->
            val result = runBlocking { safeCall { api.me() } }
            assertTrue("expected error, got $result", result is NetworkResult.Error)
            val error = result as NetworkResult.Error
            assertEquals(401, error.code)
            assertEquals("Invalid or expired token", error.message)
        }
    }

    // -- helpers ----------------------------------------------------------

    private inline fun withMockServer(
        code: Int,
        body: String,
        block: (ApiService) -> Unit
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
            block(retrofit.create(ApiService::class.java))
        } finally {
            server.shutdown()
        }
    }
}
