package com.kharcha.tracker.data.remote.dto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GroupNoteDtosTest {

    @Test
    fun `dto with all fields`() {
        val dto = GroupNoteDto(
            id = "n1",
            group_id = "g1",
            date = "2025-01-01",
            message = "milk",
            tags = listOf("grocery"),
            created_by = "u1",
            created_at = "2025-01-01T10:00:00Z",
            modified_by = null,
            modified_at = null
        )
        assertEquals("n1", dto.id)
        assertEquals("g1", dto.group_id)
        assertEquals("milk", dto.message)
        assertEquals(listOf("grocery"), dto.tags)
        assertNull(dto.modified_by)
        assertNull(dto.modified_at)
    }

    @Test
    fun `dto when tags absent defaults to null (not empty list)`() {
        val dto = GroupNoteDto(id = "n1", message = "hello")
        assertNull(dto.tags)
    }
}