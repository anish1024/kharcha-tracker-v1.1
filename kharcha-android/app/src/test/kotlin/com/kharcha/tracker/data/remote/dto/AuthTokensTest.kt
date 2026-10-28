package com.kharcha.tracker.data.remote.dto

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The DTOs must match the FastAPI wire format exactly (verified against
 * `kharcha_service/models/base.py` and `models/user.py`).
 */
class AuthTokensTest {

    private val gson = Gson()

    @Test
    fun `parses login response in backend Base shape`() {
        val json = """
            {
              "auth_user_id": "3b5d3c2e-9f6a-4e1b-8c2d-1a2b3c4d5e6f",
              "auth_token": "access-jwt",
              "auth_token_expiry": "2026-10-30T14:22:03.123456+00:00",
              "refresh_token": "refresh-jwt",
              "refresh_token_expiry": "2026-11-29T14:22:03.123456+00:00"
            }
        """.trimIndent()

        val tokens = gson.fromJson(json, AuthTokens::class.java)

        assertNotNull(tokens)
        assertEquals("3b5d3c2e-9f6a-4e1b-8c2d-1a2b3c4d5e6f", tokens.authUserId)
        assertEquals("access-jwt", tokens.authToken)
        assertEquals("2026-10-30T14:22:03.123456+00:00", tokens.authTokenExpiry)
        assertEquals("refresh-jwt", tokens.refreshToken)
        assertEquals("2026-11-29T14:22:03.123456+00:00", tokens.refreshTokenExpiry)
    }

    @Test
    fun `parses Base when the refresh token is absent`() {
        val json = """
            {
              "auth_user_id": "3b5d3c2e-9f6a-4e1b-8c2d-1a2b3c4d5e6f",
              "auth_token": "access-jwt",
              "auth_token_expiry": "2026-10-30T14:22:03+00:00",
              "refresh_token": null,
              "refresh_token_expiry": null
            }
        """.trimIndent()

        val tokens = gson.fromJson(json, AuthTokens::class.java)

        assertEquals("access-jwt", tokens.authToken)
        assertNull(tokens.refreshToken)
        assertNull(tokens.refreshTokenExpiry)
    }

    @Test
    fun `ignores unknown fields in Base`() {
        val json = """
            {
              "auth_user_id": "u",
              "auth_token": "t",
              "auth_token_expiry": "e",
              "some_future_field": 42
            }
        """.trimIndent()

        val tokens = gson.fromJson(json, AuthTokens::class.java)

        assertEquals("t", tokens.authToken)
    }

    @Test
    fun `parses user profile and ignores password`() {
        // `GET /users/me` returns the backend User model, which declares a
        // `password` field on the wire; the client must not map (or crash on) it.
        val json = """
            {
              "id": "3b5d3c2e-9f6a-4e1b-8c2d-1a2b3c4d5e6f",
              "name": "Anish",
              "password": "hashed-value",
              "email": "anish@example.com",
              "avatar_url": null
            }
        """.trimIndent()

        val user = gson.fromJson(json, UserDto::class.java)

        assertNotNull(user)
        assertEquals("3b5d3c2e-9f6a-4e1b-8c2d-1a2b3c4d5e6f", user.id)
        assertEquals("Anish", user.name)
        assertEquals("anish@example.com", user.email)
        assertNull(user.avatarUrl)
    }

    @Test
    fun `refresh request serialises with the backend field name`() {
        val json = gson.toJson(RefreshRequest("refresh-jwt"))
        assertTrue(json.contains("\"refresh_token\":\"refresh-jwt\""))
    }

    @Test
    fun `signup request serialises with the backend field names`() {
        val json = gson.toJson(SignupRequest("Anish", "anish@example.com", "secret123", null))
        assertTrue(json.contains("\"name\":\"Anish\""))
        assertTrue(json.contains("\"email\":\"anish@example.com\""))
        assertTrue(json.contains("\"password\":\"secret123\""))
        // Gson omits null fields by default
        assertTrue(!json.contains("avatar_url"))
    }
}
