package com.kharcha.tracker.data.remote.dto

import com.google.gson.annotations.SerializedName

/**
 * Mirrors the backend `Group` model (returned by `GET /groups` and
 * `GET /groups/{group_id}`).
 *
 * Wire shape (verified against `kharcha_service/models/group.py`):
 * ```json
 * {
 *   "id": "<uuid>",
 *   "name": "Flatmates",
 *   "created_by_user_id": "<uuid>",
 *   "invite_code": "ABCD1234",
 *   "members": ["<uuid>", "..."],
 *   "status": "<group_status>",
 *   "currency": "INR",
 *   "time_created": "<iso-8601>",
 *   "last_updated": "<iso-8601>"
 * }
 * ```
 *
 * UUIDs and datetimes arrive as strings and are kept as strings on the
 * client (same convention as [AuthTokens]).
 */
data class GroupDto(
    @SerializedName("id")
    val id: String? = null,
    @SerializedName("name")
    val name: String? = null,
    @SerializedName("created_by_user_id")
    val createdById: String? = null,
    @SerializedName("invite_code")
    val inviteCode: String? = null,
    @SerializedName("members")
    val members: List<String> = emptyList(),
    @SerializedName("status")
    val status: String? = null,
    @SerializedName("currency")
    val currency: String? = null,
    @SerializedName("time_created")
    val timeCreated: String? = null,
    @SerializedName("last_updated")
    val lastUpdated: String? = null
)
