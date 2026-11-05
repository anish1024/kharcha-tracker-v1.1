package com.kharcha.tracker.data.repository

import com.kharcha.tracker.core.util.NetworkResult
import com.kharcha.tracker.core.util.safeCall
import com.kharcha.tracker.data.remote.ApiService
import com.kharcha.tracker.data.remote.dto.CreateGroupNoteRequest
import com.kharcha.tracker.data.remote.dto.GroupNoteDto
import com.kharcha.tracker.data.remote.dto.UpdateGroupNoteRequest

class GroupNoteRepository(private val api: ApiService) {

    suspend fun list(groupId: String): NetworkResult<List<GroupNoteDto>> =
        safeCall { api.groupNotes(groupId) }

    suspend fun create(groupId: String, request: CreateGroupNoteRequest): NetworkResult<GroupNoteDto> =
        safeCall { api.createNote(groupId, request) }

    suspend fun update(groupId: String, noteId: String, request: UpdateGroupNoteRequest): NetworkResult<GroupNoteDto> =
        safeCall { api.updateNote(groupId, noteId, request) }

    suspend fun delete(groupId: String, noteId: String): NetworkResult<Boolean> =
        safeCall { api.deleteNote(groupId, noteId) }
}