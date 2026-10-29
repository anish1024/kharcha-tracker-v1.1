package com.kharcha.tracker.data.repository

import com.kharcha.tracker.core.util.NetworkResult
import com.kharcha.tracker.core.util.safeCall
import com.kharcha.tracker.data.remote.ApiService
import com.kharcha.tracker.data.remote.dto.AddExpenseRequest
import com.kharcha.tracker.data.remote.dto.CreateGroupRequest
import com.kharcha.tracker.data.remote.dto.ExpenseDto
import com.kharcha.tracker.data.remote.dto.ExpenseUpdateRequest
import com.kharcha.tracker.data.remote.dto.GroupDto
import com.kharcha.tracker.data.remote.dto.GroupMemberDto
import com.kharcha.tracker.data.remote.dto.InviteAcceptRequest
import com.kharcha.tracker.data.remote.dto.InviteCodeResponse
import com.kharcha.tracker.data.remote.dto.PayoutActionRequest
import com.kharcha.tracker.data.remote.dto.PayoutDto
import com.kharcha.tracker.data.remote.dto.SettleRequest

/**
 * Group/expense mutations for Stage 6 (create/join/leave, invite codes,
 * members, add-expense) and Stage 7a (expense edit/delete, settlements,
 * payout actions, group lock/unlock).
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

    // ---- Stage 7a: expense edit/delete ----------------------------------------

    /** `GET /expenses/{transId}` - one expense (404 if not a member). */
    suspend fun getExpense(transId: Int): NetworkResult<ExpenseDto> =
        safeCall { api.getExpense(transId) }

    /**
     * `PATCH /expenses/{transId}` - edit an expense. Only the fields set on
     * [request] change (absent keys = no change). 422 while the group isn't
     * OPEN, on an invalid category, a non-member payer, or a paid total that
     * doesn't match the expense amount.
     */
    suspend fun updateExpense(
        transId: Int,
        request: ExpenseUpdateRequest
    ): NetworkResult<ExpenseDto> =
        safeCall { api.updateExpense(transId, request) }

    /**
     * `DELETE /expenses/{transId}` - soft delete. 422 while the group isn't
     * OPEN or a PAID/SETTLED payout references this expense.
     */
    suspend fun deleteExpense(transId: Int): NetworkResult<Boolean> =
        safeCall { api.deleteExpense(transId) }

    // ---- Stage 7a: settlements & payouts ---------------------------------------

    /**
     * `POST /groups/{groupId}/settle` - run a PERIODIC settlement (the
     * server default kind). Works while the group is OPEN or LOCKED;
     * returns the payouts the settlement created.
     */
    suspend fun settleGroup(groupId: String): NetworkResult<List<PayoutDto>> =
        safeCall { api.settleGroup(groupId, SettleRequest()) }

    /** `GET /groups/{groupId}/payouts` - the group's payout records. */
    suspend fun groupPayouts(groupId: String): NetworkResult<List<PayoutDto>> =
        safeCall { api.groupPayouts(groupId) }

    /** `POST .../paid` - payor marks a PENDING payout PAID; [evidence] optional. */
    suspend fun markPayoutPaid(
        groupId: String,
        payoutId: String,
        evidence: String? = null
    ): NetworkResult<PayoutDto> =
        safeCall { api.markPayoutPaid(groupId, payoutId, PayoutActionRequest(evidence = evidence)) }

    /** `POST .../settled` - payee confirms a payout as settled; [evidence] optional. */
    suspend fun confirmPayoutSettled(
        groupId: String,
        payoutId: String,
        evidence: String? = null
    ): NetworkResult<PayoutDto> =
        safeCall { api.confirmPayoutSettled(groupId, payoutId, PayoutActionRequest(evidence = evidence)) }

    /** `POST .../failed` - payor or payee fails a PAID payout; [reason] required. */
    suspend fun failPayout(
        groupId: String,
        payoutId: String,
        reason: String,
        evidence: String? = null
    ): NetworkResult<PayoutDto> =
        safeCall { api.failPayout(groupId, payoutId, PayoutActionRequest(reason = reason, evidence = evidence)) }

    /** `POST .../cancel` - payor or payee cancels a PENDING payout; [reason] required. */
    suspend fun cancelPayout(
        groupId: String,
        payoutId: String,
        reason: String
    ): NetworkResult<PayoutDto> =
        safeCall { api.cancelPayout(groupId, payoutId, PayoutActionRequest(reason = reason)) }

    // ---- Stage 7a: group lock/unlock -------------------------------------------

    /** `POST /groups/{groupId}/lock` - OPEN -> LOCKED (closes the group to changes). */
    suspend fun lockGroup(groupId: String): NetworkResult<GroupDto> =
        safeCall { api.lockGroup(groupId) }

    /** `POST /groups/{groupId}/unlock` - LOCKED -> OPEN (reopens the group). */
    suspend fun unlockGroup(groupId: String): NetworkResult<GroupDto> =
        safeCall { api.unlockGroup(groupId) }
}
