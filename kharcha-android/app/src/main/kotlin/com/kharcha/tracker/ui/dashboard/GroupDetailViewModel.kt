package com.kharcha.tracker.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kharcha.tracker.core.util.NetworkResult
import com.kharcha.tracker.data.remote.NetworkModule
import com.kharcha.tracker.data.remote.dto.ExpenseDto
import com.kharcha.tracker.data.remote.dto.GroupDto
import com.kharcha.tracker.data.remote.dto.GroupMemberDto
import com.kharcha.tracker.data.repository.DashboardRepository
import com.kharcha.tracker.data.repository.GroupRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
        val myUserId: String? = null
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
 * Loads one group's header, recent expenses and settlement preview, and
 * (Stage 6) its members plus the invite-code / leave-group actions.
 *
 * Constructor takes the route's [groupId]; navigation creates one VM per
 * back-stack entry, so each group keeps its own state. Data sources
 * degrade independently: a failed expense list renders empty, a failed
 * settle preview just hides the user's balance, a failed members fetch
 * hides the members section.
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
        if (!firstLoadComplete) return
        viewModelScope.launch {
            val next = fetchDetail()
            if (next is GroupDetailUiState.Ready) {
                _uiState.value = next
            }
        }
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

    /**
     * Fetches the group, then (best-effort) expenses, settle preview,
     * profile and members. Returns [GroupDetailUiState.Error] only when the
     * group itself is unreachable.
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

        val netDues = (repository.settlementPreview(groupId) as? NetworkResult.Success)
            ?.data?.netDues
        val myId = (repository.me() as? NetworkResult.Success)?.data?.id
        val myNetDues = if (myId != null) netDues?.get(myId) else null

        val members = (groupRepository.members(groupId) as? NetworkResult.Success)?.data
            ?: emptyList()

        return GroupDetailUiState.Ready(
            group = group,
            expenses = expenses,
            totalSpent = expenses.sumOf { it.amount },
            myNetDues = myNetDues,
            members = members,
            myUserId = myId
        )
    }
}
