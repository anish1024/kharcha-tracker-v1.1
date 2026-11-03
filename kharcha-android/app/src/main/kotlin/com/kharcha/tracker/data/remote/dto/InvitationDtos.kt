package com.kharcha.tracker.data.remote.dto

import com.google.gson.annotations.SerializedName

/**
 * Pending invitation returned by `GET /users/me/invitations`.
 *
 * Per E1 (review-meeting), the backend resolves `group_name` from
 * the associated group so the invitee can see which group invited them.
 */
data class InvitationDto(
    @SerializedName("id")
    val id: String? = null,

    @SerializedName("group_id")
    val groupId: String? = null,

    @SerializedName("inviter_id")
    val inviterId: String? = null,

    @SerializedName("invitee_email")
    val inviteeEmail: String? = null,

    @SerializedName("invite_code")
    val inviteCode: String? = null,

    @SerializedName("status")
    val status: String? = null,

    @SerializedName("created_at")
    val createdAt: String? = null,

    @SerializedName("expires_at")
    val expiresAt: String? = null,

    @SerializedName("group_name")
    val groupName: String? = null
)

/**
 * Request body for `POST /groups/{groupId}/invitations` (email invite from group detail).
 */
data class EmailInvitationRequest(
    @SerializedName("email")
    val email: String
)
