package com.kharcha.tracker.data.remote.dto

import org.junit.Assert.*
import org.junit.Test

/**
 * Stage 8a (E1): Invitation DTO shape tests.
 * The wire contract guarantees `group_name` when the backend has it
 * (see `android_review_meeting_plan.md` E1); when it is absent the
 * DTO remains valid (nullable fields) so the client does not crash.
 */
class InvitationDtosTest {

    @Test
    fun fullInvitationDtoParsesWithGroupName() {
        val json = """{"id":"inv-1","group_id":"grp-1","inviter_id":"u-1","invitee_email":"alice@example.com","invite_code":"ABCD1234","status":"pending","created_at":"2026-11-01T10:00:00Z","expires_at":"2026-11-08T10:00:00Z","group_name":"Personal Expenses"}"""
        val dto = parseInvitation(json)
        assertNotNull(dto.id)
        assertEquals("grp-1", dto.groupId)
        assertEquals("Personal Expenses", dto.groupName)
    }

    @Test
    fun invitationWithoutGroupNameIsValid() {
        val json = """{"id":"inv-2","group_id":"grp-2","invitee_email":"bob@example.com","status":"pending","invite_code":"EFGH5678"}"""
        val dto = parseInvitation(json)
        assertNotNull(dto.id)
        assertEquals("grp-2", dto.groupId)
        assertNull(dto.groupName)
    }

    @Test
    fun nullExpiresAtIsAllowed() {
        val json = """{"id":"inv-3","group_id":"grp-3","invitee_email":"carol@example.com","status":"pending","invite_code":"IJKL9999","created_at":"2026-11-02T12:00:00Z","expires_at":null}"""
        val dto = parseInvitation(json)
        assertNotNull(dto.id)
        assertNull(dto.expiresAt)
    }

    private fun parseInvitation(json: String): InvitationDto {
        val gson = com.google.gson.Gson()
        return gson.fromJson(json, InvitationDto::class.java)
    }
}
