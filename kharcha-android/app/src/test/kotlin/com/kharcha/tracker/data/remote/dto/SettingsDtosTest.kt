package com.kharcha.tracker.data.remote.dto

import com.google.gson.Gson
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifies the Stage 7b settings DTOs parse the gateway's subscription /
 * notification shapes (verified against
 * `subscription_service.get_status` and the `Notification` model) and
 * serialize to the exact wire keys the FastAPI routes read - including the
 * "absent key means no change" contract for the `PATCH /users/me` body.
 */
class SettingsDtosTest {

    private val gson = Gson()

    // -- SubscriptionStatusDto ------------------------------------------------

    @Test
    fun `subscription parses the trial shape`() {
        val s = gson.fromJson(
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
            """,
            SubscriptionStatusDto::class.java
        )
        assertEquals("2026-01-20T00:00:00Z", s.trialEndsAt)
        assertEquals("none", s.plan)
        assertEquals("none", s.planStatus)
        assertNull(s.planEndsAt)
        assertNull(s.entriesRemaining)
        assertFalse(s.isLimited)
        assertFalse(s.isExpired)
    }

    @Test
    fun `subscription parses an active paid plan`() {
        val s = gson.fromJson(
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
            """,
            SubscriptionStatusDto::class.java
        )
        assertNull(s.trialEndsAt)
        assertEquals("yearly", s.plan)
        assertEquals("active", s.planStatus)
        assertEquals("2026-12-31T23:59:59Z", s.planEndsAt)
        assertNull(s.entriesRemaining)
    }

    @Test
    fun `subscription parses an expired trial with remaining entries`() {
        val s = gson.fromJson(
            """
            {
              "trial_ends_at": "2025-10-01T00:00:00Z",
              "plan": "none",
              "plan_status": "none",
              "plan_ends_at": null,
              "entries_remaining": 3,
              "is_limited": true,
              "is_expired": true
            }
            """,
            SubscriptionStatusDto::class.java
        )
        assertTrue(s.isLimited)
        assertTrue(s.isExpired)
        assertEquals(3, s.entriesRemaining)
    }

    @Test
    fun `subscription with absent keys falls back to defaults`() {
        val s = gson.fromJson("""{}""", SubscriptionStatusDto::class.java)
        assertNull(s.trialEndsAt)
        assertNull(s.plan)
        assertNull(s.planStatus)
        assertNull(s.entriesRemaining)
        assertFalse(s.isLimited)
        assertFalse(s.isExpired)
    }

    // -- request bodies --------------------------------------------------------

    @Test
    fun `profile update request serialises the wire keys`() {
        val json = JsonParser.parseString(
            gson.toJson(ProfileUpdateRequest(name = "Sudeep", avatarUrl = "https://example.com/a.png"))
        ).asJsonObject
        assertEquals("Sudeep", json.get("name").asString)
        assertEquals("https://example.com/a.png", json.get("avatar_url").asString)
    }

    @Test
    fun `profile update request omits null fields`() {
        val json = JsonParser.parseString(
            gson.toJson(ProfileUpdateRequest(name = "Sudeep", avatarUrl = null))
        ).asJsonObject
        assertEquals("Sudeep", json.get("name").asString)
        assertFalse(json.has("avatar_url"))
    }

    @Test
    fun `profile update request with no edits serialises to an empty object`() {
        assertEquals("{}", gson.toJson(ProfileUpdateRequest(name = null, avatarUrl = null)))
    }

    @Test
    fun `activate plan request serialises the plan key`() {
        val json = JsonParser.parseString(
            gson.toJson(ActivatePlanRequest(plan = "yearly"))
        ).asJsonObject
        assertEquals("yearly", json.get("plan").asString)
    }

    // -- NotificationDto --------------------------------------------------------

    @Test
    fun `notification parses the gateway shape`() {
        val n = gson.fromJson(
            """
            {
              "id": "n1",
              "user_id": "u1",
              "group_id": null,
              "payout_id": "p9",
              "kind": "PAYOUT_FAILED",
              "message": null,
              "is_read": false,
              "created_at": "2026-01-05T09:00:00Z"
            }
            """,
            NotificationDto::class.java
        )
        assertEquals("n1", n.id)
        assertEquals("u1", n.userId)
        assertNull(n.groupId)
        assertEquals("p9", n.payoutId)
        assertEquals("PAYOUT_FAILED", n.kind)
        assertNull(n.message)
        assertFalse(n.isRead)
        assertEquals("2026-01-05T09:00:00Z", n.createdAt)
    }

    @Test
    fun `notification with absent optional fields defaults`() {
        val n = gson.fromJson("""{"id":"n2","is_read":true}""", NotificationDto::class.java)
        assertEquals("n2", n.id)
        assertTrue(n.isRead)
        assertNull(n.userId)
        assertNull(n.kind)
        assertNull(n.createdAt)
    }
}
