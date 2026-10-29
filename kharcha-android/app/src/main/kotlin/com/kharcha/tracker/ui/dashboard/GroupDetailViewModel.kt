package com.kharcha.tracker.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kharcha.tracker.core.util.NetworkResult
import com.kharcha.tracker.data.remote.NetworkModule
import com.kharcha.tracker.data.remote.dto.ExpenseDto
import com.kharcha.tracker.data.remote.dto.GroupDto
import com.kharcha.tracker.data.remote.dto.GroupMemberDto
import com.kharcha.tracker.data.remote.dto.PayoutDto
import com.kharcha.tracker.data.repository.DashboardRepository
import com.kharcha.tracker.data.repository.GroupRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Render state for the group-detail screen.
 *
 * [Ready.myNetDues] is the signed-in user's net position from
 * `GET /groups/{id}/settle` (positive = owed, negative = owes). It is
 * null when either `/settle` or `/users/me` was unreachable - the rest of
 * the screen still renders.
 *
 * [Ready.members] is the resolved member list (Stage 6); it degrades to an
 * empty list when `/members` failed, in which case the members section is
 * hidden. [Ready.myUserId] marks "you" in that list (null when `/users/me`
 * failed - the "(You)" marker is simply omitted).
 *
 * Stage 7a: [Ready.payouts] is the group's payout records (empty when the
 * fetch failed - the payouts section is hidden) and [Ready.proposedPayouts]
 * is what the next settlement would create (from the settle preview; empty
 * when there is nothing to settle or the preview failed).
 */
sealed class GroupDetailUiState {
    object Loading : GroupDetailUiState()
    data class Error(val message: String) : GroupDetailUiState()
    data class Ready(
        val group: GroupDto,
        val expenses: List<ExpenseDto>,
        val totalSpent: Double,
        val myNetDues: Double?,
        val members: List<GroupMemberDto> = emptyList(),
        val myUserId: String? = null,
        val payouts: List<PayoutDto> = emptyList(),
        val proposedPayouts: List<PayoutDto> = emptyList()
    ) : GroupDetailUiState()
}

/**
 * Progress of the "share invite code" action. [Ready] drives the code
 * dialog; [Failed] is surfaced as a snackbar by the screen (which then
 * resets the state via [GroupDetailViewModel.dismissInviteCode]).
 */
sealed class InviteCodeUiState {
    object Idle : InviteCodeUiState()
    object Busy : InviteCodeUiState()
    data class Ready(val code: String) : InviteCodeUiState()
    data class Failed(val message: String) : InviteCodeUiState()
}

/**
 * Progress of "leave group". [Left] is the success signal the screen uses
 * to pop back to the dashboard (the list refreshes on resume); [Failed]
 * is shown inline in the confirm dialog.
 */
sealed class LeaveUiState {
    object Idle : LeaveUiState()
    object Busy : LeaveUiState()
    object Left : LeaveUiState()
    data class Failed(val message: String) : LeaveUiState()
}

/**
 * Progress of "settle group" (Stage 7a). [Success.message] carries the
 * human-readable outcome the screen snackbars (including the "nothing to
 * pay out" case); [Failed] is surfaced as a snackbar and cleared via
 * [GroupDetailViewModel.clearSettleState].
 */
sealed class SettleUiState {
    object Idle : SettleUiState()
    object Busy : SettleUiState()
    data class Success(val message: String) : SettleUiState()
    data class Failed(val message: String) : SettleUiState()
}

/**
 * Progress of a payout action (Stage 7a). The [payoutId] in [Busy] /
 * [Failed] scopes the busy indicator to that row; [Success.message] is
 * snackbar'd by the screen and cleared via
 * [GroupDetailViewModel.clearPayoutActionState].
 */
sealed class PayoutActionUiState {
    object Idle : PayoutActionUiState()
    data class Busy(val payoutId: String?) : PayoutActionUiState()
    data class Success(val message: String) : PayoutActionUiState()
    data class Failed(val payoutId: String?, val message: String) : PayoutActionUiState()
}

/**
 * Progress of the group lock/unlock action (Stage 7a). [Success.nowLocked]
 * tells the screen what the group is now; [Failed] is surfaced as a
 * snackbar and cleared via [GroupDetailViewModel.clearLockState].
 */
sealed class LockUiState {
    object Idle : LockUiState()
    object Busy : LockUiState()
    data class Success(val nowLocked: Boolean) : LockUiState()
    data class Failed(val message: String) : LockUiState()
}

/**
 * Loads one group's header, recent expenses and settlement preview, and
 * (Stage 6) its members plus the invite-code / leave-group actions.
 * Stage 7a adds the group's payouts, the settlement ("settle group" +
 * payout actions) and the group lock/unlock.
 *
 * Constructor takes the route's [groupId]; navigation creates one VM per
 * back-stack entry, so each group keeps its own state. Data sources
 * degrade independently: a failed expense list renders empty, a failed
 * settle preview just hides the user's balance, a failed members fetch
 * hides the members section, a failed payouts fetch hides the payouts
 * section.
 */
class GroupDetailViewModel(
    private val groupId: String
) : ViewModel() {

    private val repository = DashboardRepository(NetworkModule.api)
    private val groupRepository = GroupRepository(NetworkModule.api)

    private val _uiState = MutableStateFlow<GroupDetailUiState>(GroupDetailUiState.Loading)
    val uiState: StateFlow<GroupDetailUiState> = _uiState.asStateFlow()

    private val _inviteCode = MutableStateFlow<InviteCodeUiState>(InviteCodeUiState.Idle)
    val inviteCode: StateFlow<InviteCodeUiState> = _inviteCode.asStateFlow()

    private val _leaveState = MutableStateFlow<LeaveUiState>(LeaveUiState.Idle)
    val leaveState: StateFlow<LeaveUiState> = _leaveState.asStateFlow()

    private val _settleState = MutableStateFlow<SettleUiState>(SettleUiState.Idle)
    val settleState: StateFlow<SettleUiState> = _settleState.asStateFlow()

    private val _payoutActionState =
        MutableStateFlow<PayoutActionUiState>(PayoutActionUiState.Idle)
    val payoutActionState: StateFlow<PayoutActionUiState> = _payoutActionState.asStateFlow()

    private val _lockState = MutableStateFlow<LockUiState>(LockUiState.Idle)
    val lockState: StateFlow<LockUiState> = _lockState.asStateFlow()

    /** Set once [load] has completed, so [refresh] cannot race the first load. */
    private var firstLoadComplete = false

    init {
        load()
    }

    /** (Re)loads group + expenses + settle + members - also the retry action. */
    fun load() {
        viewModelScope.launch {
            _uiState.value = GroupDetailUiState.Loading
            _uiState.value = fetchDetail()
            firstLoadComplete = true
        }
    }

    /**
     * Refetches without a full-screen spinner, called on screen resume (e.g.
     * returning from the add-expense screen). Only replaces the content
     * when the group is still reachable; a transient failure keeps the
     * last good data (a hard session failure force-signs-out via the auth
     * interceptor anyway).
     */
    fun refresh() {
        refreshContent()
    }

    /** Derives this member's invite code (dialog shows group id + code). */
    fun generateInviteCode() {
        if (_inviteCode.value is InviteCodeUiState.Busy) return
        viewModelScope.launch {
            _inviteCode.value = InviteCodeUiState.Busy
            when (val result = groupRepository.inviteCode(groupId)) {
                is NetworkResult.Success -> {
                    val code = result.data.inviteCode
                    _inviteCode.value =
                        if (code.isNullOrBlank()) {
                            InviteCodeUiState.Failed("The server returned no invite code")
                        } else {
                            InviteCodeUiState.Ready(code)
                        }
                }

                is NetworkResult.Error ->
                    _inviteCode.value = InviteCodeUiState.Failed(result.message)

                else ->
                    _inviteCode.value = InviteCodeUiState.Failed("Unexpected error")
            }
        }
    }

    /** Resets the invite state (dismiss dialog / clear a shown error). */
    fun dismissInviteCode() {
        _inviteCode.value = InviteCodeUiState.Idle
    }

    /**
     * Leaves the group. On success the state becomes [LeaveUiState.Left];
     * the server refuses with 422 while the caller has an outstanding
     * balance or open payouts - that message lands in [LeaveUiState.Failed].
     */
    fun leaveGroup() {
        if (_leaveState.value is LeaveUiState.Busy) return
        viewModelScope.launch {
            _leaveState.value = LeaveUiState.Busy
            when (val result = groupRepository.leave(groupId)) {
                is NetworkResult.Success ->
                    _leaveState.value = LeaveUiState.Left

                is NetworkResult.Error ->
                    _leaveState.value = LeaveUiState.Failed(result.message)

                else ->
                    _leaveState.value = LeaveUiState.Failed("Unexpected error")
            }
        }
    }

    /** Clears a leave error when the confirm dialog is dismissed. */
    fun resetLeaveState() {
        if (_leaveState.value is LeaveUiState.Failed) {
            _leaveState.value = LeaveUiState.Idle
        }
    }

    // ---- Stage 7a: settlements & payouts --------------------------------------

    /**
     * Runs the group's settlement (PERIODIC, the server default kind).
     * On success the payouts / preview sections are refetched and the
     * outcome is reported in [SettleUiState.Success].
     */
    fun settleGroup() {
        if (_settleState.value is SettleUiState.Busy) return
        viewModelScope.launch {
            _settleState.value = SettleUiState.Busy
            when (val result = groupRepository.settleGroup(groupId)) {
                is NetworkResult.Success -> {
                    _settleState.value = SettleUiState.Success(
                        message = if (result.data.isEmpty()) {
                            "Group settled - nothing to pay out"
                        } else {
                            "Group settled - ${result.data.size} payout" +
                                if (result.data.size == 1) "" else "s" + " created"
                        }
                    )
                    refreshContent()
                }

                is NetworkResult.Error ->
                    _settleState.value = SettleUiState.Failed(result.message)

                else ->
                    _settleState.value = SettleUiState.Failed("Unexpected error")
            }
        }
    }

    /**
     * Applies a [PayoutAction] to one payout, sending [evidence] (optional
     * for all actions) and [reason] (required for MARK_FAILED / CANCEL -
     * the screen validates it in the dialog before calling). On success
     * the payouts section is refetched.
     */
    fun performPayoutAction(
        payoutId: String,
        action: PayoutAction,
        evidence: String? = null,
        reason: String? = null
    ) {
        if (_payoutActionState.value is PayoutActionUiState.Busy) return
        viewModelScope.launch {
            _payoutActionState.value = PayoutActionUiState.Busy(payoutId)
            val result = when (action) {
                PayoutAction.MARK_PAID ->
                    groupRepository.markPayoutPaid(groupId, payoutId, evidence)

                PayoutAction.CONFIRM_SETTLED ->
                    groupRepository.confirmPayoutSettled(groupId, payoutId, evidence)

                PayoutAction.MARK_FAILED ->
                    groupRepository.failPayout(
                        groupId, payoutId, reason.orEmpty(), evidence
                    )

                PayoutAction.CANCEL ->
                    groupRepository.cancelPayout(groupId, payoutId, reason.orEmpty())
            }
            when (result) {
                is NetworkResult.Success -> {
                    _payoutActionState.value = PayoutActionUiState.Success(
                        message = PayoutActions.label(action) + " - done"
                    )
                    refreshContent()
                }

                is NetworkResult.Error ->
                    _payoutActionState.value =
                        PayoutActionUiState.Failed(payoutId, result.message)

                else ->
                    _payoutActionState.value =
                        PayoutActionUiState.Failed(payoutId, "Unexpected error")
            }
        }
    }

    /** Resets the settle state (the screen calls this after snackbar'ing). */
    fun clearSettleState() {
        _settleState.value = SettleUiState.Idle
    }

    /**
     * Resets the payout-action state. [keepBusy] stays false: once a
     * result has been shown there is nothing in flight.
     */
    fun clearPayoutActionState() {
        _payoutActionState.value = PayoutActionUiState.Idle
    }

    // ---- Stage 7a: group lock/unlock -------------------------------------------

    /**
     * Locks an OPEN group (closes it to expense changes) or unlocks a
     * LOCKED one, based on the currently loaded status. The server
     * enforces the transition (422 otherwise); the updated group is
     * swapped into [GroupDetailUiState.Ready.group] on success.
     */
    fun toggleLock() {
        if (_lockState.value is LockUiState.Busy) return
        val group = (_uiState.value as? GroupDetailUiState.Ready)?.group ?: return
        val wasLocked = group.status == "LOCKED"
        viewModelScope.launch {
            _lockState.value = LockUiState.Busy
            val result = if (wasLocked) {
                groupRepository.unlockGroup(groupId)
            } else {
                groupRepository.lockGroup(groupId)
            }
            when (result) {
                is NetworkResult.Success -> {
                    _uiState.update { current ->
                        (current as? GroupDetailUiState.Ready)
                            ?.copy(group = result.data) ?: current
                    }
                    _lockState.value = LockUiState.Success(nowLocked = !wasLocked)
                }

                is NetworkResult.Error ->
                    _lockState.value = LockUiState.Failed(result.message)

                else ->
                    _lockState.value = LockUiState.Failed("Unexpected error")
            }
        }
    }

    /** Resets the lock state (the screen calls this after snackbar'ing). */
    fun clearLockState() {
        _lockState.value = LockUiState.Idle
    }

    /**
     * Refetches the detail after a mutation (settle / payout action),
     * keeping the last good data on a transient failure - same rule as
     * [refresh].
     */
    private fun refreshContent() {
        if (!firstLoadComplete) return
        viewModelScope.launch {
            val next = fetchDetail()
            if (next is GroupDetailUiState.Ready) {
                _uiState.value = next
            }
        }
    }

    /**
     * Fetches the group, then (best-effort) expenses, settle preview,
     * profile, members and payouts. Returns [GroupDetailUiState.Error]
     * only when the group itself is unreachable.
     */
    private suspend fun fetchDetail(): GroupDetailUiState {
        // Fail fast on the group itself: a 401/403/404 here means the
        // session or membership is gone (session cases force sign-out
        // via the auth interceptor + AuthViewModel).
        val groupResult = repository.group(groupId)
        val group = (groupResult as? NetworkResult.Success)?.data
        if (group == null) {
            return GroupDetailUiState.Error(
                message = (groupResult as? NetworkResult.Error)?.message
                    ?: "Could not load the group"
            )
        }

        // Degrade independently: each missing piece hides only itself.
        val expenses = (repository.groupExpenses(groupId) as? NetworkResult.Success)?.data
            ?: emptyList()

        val preview = (repository.settlementPreview(groupId) as? NetworkResult.Success)?.data
        val netDues = preview?.netDues
        val myId = (repository.me() as? NetworkResult.Success)?.data?.id
        val myNetDues = if (myId != null) netDues?.get(myId) else null

        val members = (groupRepository.members(groupId) as? NetworkResult.Success)?.data
            ?: emptyList()

        val payouts = (groupRepository.groupPayouts(groupId) as? NetworkResult.Success)?.data
            ?: emptyList()

        return GroupDetailUiState.Ready(
            group = group,
            expenses = expenses,
            totalSpent = expenses.sumOf { it.amount },
            myNetDues = myNetDues,
            members = members,
            myUserId = myId,
            payouts = payouts,
            proposedPayouts = preview?.proposedPayouts.orEmpty()
        )
    }
}
