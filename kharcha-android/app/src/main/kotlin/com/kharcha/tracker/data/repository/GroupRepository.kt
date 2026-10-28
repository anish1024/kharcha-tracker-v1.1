package com.kharcha.tracker.data.repository

import com.kharcha.tracker.core.util.NetworkResult
import com.kharcha.tracker.core.util.safeCall
import com.kharcha.tracker.data.remote.ApiService
import com.kharcha.tracker.data.remote.dto.AddExpenseRequest
import com.kharcha.tracker.data.remote.dto.CreateGroupRequest
import com.kharcha.tracker.data.remote.dto.ExpenseDto
import com.kharcha.tracker.data.remote.dto.GroupDto
import com.kharcha.tracker.data.remote.dto.GroupMemberDto
import com.kharcha.tracker.data.remote.dto.InviteAcceptRequest
import com.kharcha.tracker.data.remote.dto.InviteCodeResponse

/**
 * Group/expense mutations for Stage 6 (create/join/leave, invite codes,
 * members, add-expense).
 *
 * Stateless like [DashboardRepository]: every method runs one Retrofit call
 * through [safeCall] and maps the outcome to a [NetworkResult]. Session
 * handling (401 refresh + force sign-out) is already covered by the auth
 * interceptor, so nothing here needs extra auth logic.
 *
 * Error semantics worth knowing at the call site (from `group_service.py`
 * and `main.py`):
 * - `create`: 422 for a blank name or an invalid currency code.
 * - `join`: 404 "Invalid invite code" on a bad code, 422 when the group is
 *   not in the OPEN status. Re-joining an existing membership is a no-op
 *   that returns the group (idempotent server-side).
 * - `leave`: 422 "Cannot leave group with outstanding balance: {net_due}"
 *   while the caller's net position is non-zero, or the group is settled.
 * - `addExpense`: 403 with an OBJECT detail (`{"code": ..., "message": ...}`)
 *   when the caller's subscription is exhausted.
 */
class GroupRepository(
    private val api: ApiService
) {

    /** `POST /groups` - create a group (the caller becomes its first member). */
    suspend fun create(name: String, currency: String): NetworkResult<GroupDto> =
        safeCall { api.createGroup(CreateGroupRequest(name, currency)) }

    /** `POST /groups/{groupId}/invite/accept` - join with a valid invite code. */
    suspend fun join(groupId: String, inviteCode: String): NetworkResult<GroupDto> =
        safeCall { api.acceptInvite(groupId, InviteAcceptRequest(inviteCode)) }

    /** `GET /groups/{groupId}/members` - member profiles for this group. */
    suspend fun members(groupId: String): NetworkResult<List<GroupMemberDto>> =
        safeCall { api.groupMembers(groupId) }

    /** `DELETE /groups/{groupId}/members/me` - leave the group. */
    suspend fun leave(groupId: String): NetworkResult<GroupDto> =
        safeCall { api.leaveGroup(groupId) }

    /** `POST /groups/{groupId}/invite` - derive this member's invite code. */
    suspend fun inviteCode(groupId: String): NetworkResult<InviteCodeResponse> =
        safeCall { api.generateInviteCode(groupId) }

    /** `POST /expenses` - add an expense to the group (top-level endpoint). */
    suspend fun addExpense(request: AddExpenseRequest): NetworkResult<ExpenseDto> =
        safeCall { api.addExpense(request) }
}
