package com.kharcha.tracker.data.repository

import com.kharcha.tracker.core.util.NetworkResult
import com.kharcha.tracker.core.util.safeCall
import com.kharcha.tracker.data.remote.ApiService
import com.kharcha.tracker.data.remote.dto.EmailInvitationRequest
import com.kharcha.tracker.data.remote.dto.InvitationDto

/**
 * Stage 8a (E1): pending invitations + owner email invites.
 *
 * Kept separate from [GroupRepository] so the dashboard can load
 * invitations without depending on group detail. Session handling is
 * covered by [com.kharcha.tracker.data.remote.AuthInterceptor].
 */
class InvitationRepository(
    private val api: ApiService
) {

    /** `GET /users/me/invitations` - pending invites addressed to the signed-in user. */
    suspend fun myInvitations(): NetworkResult<List<InvitationDto>> =
        safeCall { api.myInvitations() }

    /**
     * `POST /groups/{groupId}/invitations` - email invite from group detail.
     * The owner must be a member of the group (server-side check).
     */
    suspend fun createInvitation(
        groupId: String,
        email: String
    ): NetworkResult<InvitationDto> =
        safeCall { api.createInvitation(groupId, EmailInvitationRequest(email)) }

    /** `POST /invitations/{invitationId}/accept` - the invitee joins the group. */
    suspend fun acceptInvitation(invitationId: String): NetworkResult<InvitationDto> =
        safeCall { api.acceptInvitation(invitationId) }

    /** `POST /invitations/{invitationId}/decline` - the invitee declines. */
    suspend fun declineInvitation(invitationId: String): NetworkResult<InvitationDto> =
        safeCall { api.declineInvitation(invitationId) }
}