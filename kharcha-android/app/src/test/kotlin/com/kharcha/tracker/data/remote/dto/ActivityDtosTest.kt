package com.kharcha.tracker.data.remote.dto

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Verifies the Stage 7b activity DTO parses the gateway's audit-log shape
 * (verified against `storage_service/schema/audit_log.py` +
 * `base_record.py`) and that the optional detail fields default when
 * absent.
 */
class ActivityDtosTest {

    private val gson = Gson()

    @Test
    fun `audit log parses the gateway shape`() {
        val log = gson.fromJson(
            """
            {
              "id": "al1",
              "event": "CREATE",
              "entity_type": "group",
              "entity_id": "g1",
              "group_id": "g1",
              "action": "group_created",
              "field_name": null,
              "old_value": null,
              "new_value": "Flatmates",
              "actor_user_id": "aa11",
              "actor_name": "Sudeep",
              "actor_ip": "10.0.0.2",
              "actor_user_agent": "okhttp/4.12.0",
              "reason": null,
              "error_code": null,
              "is_deleted": false,
              "created_by": "aa11",
              "created_at": "2026-01-05T09:00:00Z",
              "modified_by": null,
              "modified_at": null
            }
            """,
            ActivityLogDto::class.java
        )
        assertEquals("al1", log.id)
        assertEquals("CREATE", log.event)
        assertEquals("group", log.entityType)
        assertEquals("g1", log.entityId)
        assertEquals("g1", log.groupId)
        assertEquals("group_created", log.action)
        assertNull(log.fieldName)
        assertNull(log.oldValue)
        assertEquals("Flatmates", log.newValue)
        assertEquals("aa11", log.actorUserId)
        assertEquals("Sudeep", log.actorName)
        assertEquals("10.0.0.2", log.actorIp)
        assertEquals("okhttp/4.12.0", log.actorUserAgent)
        assertNull(log.reason)
        assertNull(log.errorCode)
        assertFalse(log.isDeleted)
        assertEquals("aa11", log.createdBy)
        assertEquals("2026-01-05T09:00:00Z", log.createdAt)
        assertNull(log.modifiedBy)
        assertNull(log.modifiedAt)
    }

    @Test
    fun `audit log with absent optional fields defaults`() {
        val log = gson.fromJson("""{"id":"al2","event":"UPDATE"}""", ActivityLogDto::class.java)
        assertEquals("al2", log.id)
        assertEquals("UPDATE", log.event)
        assertNull(log.entityType)
        assertNull(log.entityId)
        assertNull(log.action)
        assertNull(log.newValue)
        assertNull(log.actorUserId)
        assertNull(log.actorName)
        assertNull(log.createdAt)
        assertFalse(log.isDeleted)
    }
}
