package com.kharcha.tracker.ui.dashboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class NoteFormTest {

    @Test
    fun `parseTags splits on comma`() {
        assertEquals(listOf("grocery", "milk", "bread"), NoteForm.parseTags("grocery, milk, bread"))
    }

    @Test
    fun `parseTags trims spaces`() {
        assertEquals(listOf("grocery"), NoteForm.parseTags("  grocery  "))
    }

    @Test
    fun `parseTags drops blanks`() {
        assertEquals(listOf("grocery", "milk", "bread"), NoteForm.parseTags("grocery,, milk,, bread,"))
    }

    @Test
    fun `parseTags dedupes`() {
        assertEquals(listOf("grocery", "milk"), NoteForm.parseTags("grocery, grocery, milk"))
    }

    @Test
    fun `parseTags caps at 20 entries`() {
        val input = (1..25).joinToString(",") { "tag$it" }
        val result = NoteForm.parseTags(input)
        assertEquals(20, result.size)
    }

    @Test
    fun `parseTags drops overly long tags`() {
        val longTag = "x".repeat(51)
        assertEquals(emptyList<String>(), NoteForm.parseTags("$longTag"))
        assertEquals(listOf("ok"), NoteForm.parseTags("$longTag,ok"))
    }

    @Test
    fun `parseTags blank input returns empty list`() {
        assertEquals(emptyList<String>(), NoteForm.parseTags("   , , ,  "))
    }

    @Test
    fun `validNoteForm blank message returns error even with valid date`() {
        assertNotNull(NoteForm.validateNoteForm("", "2025-01-01"))
    }

    @Test
    fun `validNoteForm valid message with blank date passes date validation`() {
        assertNull(NoteForm.validateNoteForm("hello", ""))
    }

    @Test
    fun `validNoteForm bad date returns error`() {
        assertNotNull(NoteForm.validateNoteForm("hello", "bad-date"))
    }

    @Test
    fun `validNoteForm message over 500 chars returns error`() {
        val longMessage = "x".repeat(501)
        assertEquals("Message exceeds 500 characters", NoteForm.validateNoteForm(longMessage, "2025-01-01"))
    }

    @Test
    fun `buildTagsText joins with comma space`() {
        assertEquals("grocery, milk", NoteForm.buildTagsText(listOf("grocery", "milk")))
    }
}