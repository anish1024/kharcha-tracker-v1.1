package com.kharcha.tracker.ui.dashboard

object NoteForm {

    fun parseTags(input: String): List<String> {
        return input.split(",")
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .filter { it.length <= 50 }
            .take(20)
    }

    fun validateNoteForm(message: String, date: String): String? {
        if (message.isBlank()) return "Message is required"
        if (message.length > 500) return "Message exceeds 500 characters"
        if (validateEditDate(date) != null) return "Invalid date"
        return null
    }

    fun buildTagsText(tags: List<String>): String = tags.joinToString(", ")
}