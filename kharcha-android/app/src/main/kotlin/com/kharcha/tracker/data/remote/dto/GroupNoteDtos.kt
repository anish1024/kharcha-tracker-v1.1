package com.kharcha.tracker.data.remote.dto

data class GroupNoteDto(
    val id: String? = null,
    val group_id: String? = null,
    val date: String? = null,
    val message: String? = null,
    val tags: List<String>? = null,
    val created_by: String? = null,
    val created_at: String? = null,
    val modified_by: String? = null,
    val modified_at: String? = null
)

data class CreateGroupNoteRequest(
    val message: String,
    val tags: List<String> = emptyList(),
    val date: String
)

data class UpdateGroupNoteRequest(
    val message: String? = null,
    val tags: List<String>? = null,
    val date: String? = null
)
