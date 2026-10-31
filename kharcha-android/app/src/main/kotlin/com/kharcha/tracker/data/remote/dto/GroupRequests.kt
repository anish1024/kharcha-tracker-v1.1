package com.kharcha.tracker.data.remote.dto

import com.google.gson.annotations.SerializedName

/**
 * Request body for `POST /groups` (backend reads `name` + `currency`;
 * `currency` defaults to "INR" server-side and must be a 3-letter code,
 * which the gateway uppercases).
 */
data class CreateGroupRequest(
    @SerializedName("name")
    val name: String,
    @SerializedName("currency")
    val currency: String
)

/**
 * Request body for `POST /groups/{groupId}/invite/accept` (backend reads
 * `invite_code`). The group id comes from the URL path and the code is
 * derived per member by the gateway, so both are required.
 */
data class InviteAcceptRequest(
    @SerializedName("invite_code")
    val inviteCode: String
)

/**
 * Response for `POST /groups/{groupId}/invite`:
 * `{"invite_code": "<8-char uppercase base32>"}`.
 */
data class InviteCodeResponse(
    @SerializedName("invite_code")
    val inviteCode: String? = null
)

/**
 * One entry of `GET /groups/{groupId}/members`.
 *
 * The backend resolves each member id to a user profile; when the user
 * record is missing it falls back to `{id, name: <id>, email: "",
 * avatar_url: null}`, so `email`/`avatarUrl` may be blank/null.
 */
data class GroupMemberDto(
    @SerializedName("id")
    val id: String? = null,
    @SerializedName("name")
    val name: String? = null,
    @SerializedName("email")
    val email: String? = null,
    @SerializedName("avatar_url")
    val avatarUrl: String? = null
)
