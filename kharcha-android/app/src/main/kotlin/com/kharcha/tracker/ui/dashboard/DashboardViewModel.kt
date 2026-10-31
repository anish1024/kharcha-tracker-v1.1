package com.kharcha.tracker.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kharcha.tracker.core.util.NetworkResult
import com.kharcha.tracker.data.remote.NetworkModule
import com.kharcha.tracker.data.remote.dto.GroupDto
import com.kharcha.tracker.data.remote.dto.UserDto
import com.kharcha.tracker.data.repository.DashboardRepository
import com.kharcha.tracker.data.repository.GroupRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Render state for the dashboard screen: profile card + group list.
 *
 * [Ready.user] may be null when `/users/me` failed but the group list
 * loaded - the screen still renders the groups.
 */
sealed class DashboardUiState {
    object Loading : DashboardUiState()
    data class Error(val message: String) : DashboardUiState()
    data class Ready(
        val user: UserDto?,
        val groups: List<GroupDto>
    ) : DashboardUiState()
}

/** Which group action the overflow dialogs are driving (one at a time). */
enum class GroupActionKind { CREATE, JOIN }

/**
 * Progress of the create-group / join-group actions from the overflow-menu
 * dialogs. Only one dialog is open at a time, so a single state is enough:
 * [Busy] disables the active dialog's confirm button and [Failed] shows
 * the server's message inline in that dialog.
 */
sealed class GroupActionUiState {
    object Idle : GroupActionUiState()
    data class Busy(val kind: GroupActionKind) : GroupActionUiState()
    data class Failed(val kind: GroupActionKind, val message: String) : GroupActionUiState()
}

/**
 * Loads the signed-in profile and the user's groups for the dashboard, and
 * (Stage 6) creates/joins groups from the overflow menu.
 *
 * No-arg constructor (default [ViewModel] factory): the network stack is a
 * process-wide singleton initialised in [com.kharcha.tracker.MainActivity].
 * Created/joined groups are merged into the list in place (deduped by id)
 * so the dialog close does not flash a full-screen spinner; [refresh]
 * re-fetches in the background whenever the screen resumes.
 */
class DashboardViewModel : ViewModel() {

    private val repository = DashboardRepository(NetworkModule.api)
    private val groupRepository = GroupRepository(NetworkModule.api)

    private val _uiState = MutableStateFlow<DashboardUiState>(DashboardUiState.Loading)
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    private val _actionState = MutableStateFlow<GroupActionUiState>(GroupActionUiState.Idle)
    val actionState: StateFlow<GroupActionUiState> = _actionState.asStateFlow()

    /**
     * The group most recently created through this VM, or null. The screen
     * navigates to it and clears it via [consumeCreatedGroup].
     */
    private val _createdGroup = MutableStateFlow<GroupDto?>(null)
    val createdGroup: StateFlow<GroupDto?> = _createdGroup.asStateFlow()

    /** Set once [load] has completed, so [refresh] cannot race the first load. */
    private var firstLoadComplete = false

    init {
        load()
    }

    /** (Re)loads profile + groups - also the retry action after an error. */
    fun load() {
        viewModelScope.launch {
            _uiState.value = DashboardUiState.Loading

            // Best-effort profile: the session is what matters, a failed
            // /users/me should not hide the groups.
            val me = repository.me()
            when (val groups = repository.myGroups()) {
                is NetworkResult.Success ->
                    _uiState.value = DashboardUiState.Ready(
                        user = (me as? NetworkResult.Success)?.data,
                        groups = groups.data
                    )

                is NetworkResult.Error ->
                    _uiState.value = DashboardUiState.Error(
                        // A 401 on /users/me carries the server's message
                        // (e.g. "Invalid or expired token"); prefer it.
                        message = if (me is NetworkResult.Error && me.code == 401)
                            me.message else groups.message
                    )

                else ->
                    _uiState.value = DashboardUiState.Error("Unexpected error")
            }

            firstLoadComplete = true
        }
    }

    /**
     * Refetches profile + groups without a full-screen spinner (keeps the
     * current content visible until the new data arrives). The screens call
     * it on resume so the list reflects create/join/leave actions done
     * elsewhere. A failed refresh keeps the last good data - a hard session
     * failure is force-signed-out by the auth interceptor anyway.
     */
    fun refresh() {
        if (!firstLoadComplete) return
        viewModelScope.launch {
            val me = repository.me()
            when (val groups = repository.myGroups()) {
                is NetworkResult.Success -> _uiState.update { current ->
                    if (current is DashboardUiState.Ready) {
                        current.copy(
                            user = (me as? NetworkResult.Success)?.data ?: current.user,
                            groups = groups.data
                        )
                    } else {
                        current
                    }
                }

                is NetworkResult.Error -> Unit
                else -> Unit
            }
        }
    }

    /**
     * Creates a group and, on success, merges it into the list (and stashes
     * it in [createdGroup] so the screen can navigate there).
     */
    fun createGroup(name: String, currency: String) {
        if (_actionState.value is GroupActionUiState.Busy) return
        viewModelScope.launch {
            _actionState.value = GroupActionUiState.Busy(GroupActionKind.CREATE)
            when (val result = groupRepository.create(name, currency)) {
                is NetworkResult.Success -> {
                    _actionState.value = GroupActionUiState.Idle
                    _createdGroup.value = result.data
                    mergeGroupIntoList(result.data)
                }

                is NetworkResult.Error ->
                    _actionState.value =
                        GroupActionUiState.Failed(GroupActionKind.CREATE, result.message)

                else ->
                    _actionState.value =
                        GroupActionUiState.Failed(GroupActionKind.CREATE, "Unexpected error")
            }
        }
    }

    /**
     * Joins a group with an invite code and, on success, merges the group
     * into the list (joining is idempotent server-side).
     */
    fun joinGroup(groupId: String, inviteCode: String) {
        if (_actionState.value is GroupActionUiState.Busy) return
        viewModelScope.launch {
            _actionState.value = GroupActionUiState.Busy(GroupActionKind.JOIN)
            when (val result = groupRepository.join(groupId, inviteCode)) {
                is NetworkResult.Success -> {
                    _actionState.value = GroupActionUiState.Idle
                    mergeGroupIntoList(result.data)
                }

                is NetworkResult.Error ->
                    _actionState.value =
                        GroupActionUiState.Failed(GroupActionKind.JOIN, result.message)

                else ->
                    _actionState.value =
                        GroupActionUiState.Failed(GroupActionKind.JOIN, "Unexpected error")
            }
        }
    }

    /** Clears the inline error once the active create/join dialog closes. */
    fun clearActionError() {
        if (_actionState.value is GroupActionUiState.Failed) {
            _actionState.value = GroupActionUiState.Idle
        }
    }

    /** Clears [createdGroup] after the screen has navigated to it. */
    fun consumeCreatedGroup() {
        _createdGroup.value = null
    }

    /**
     * Merges [group] into the list in place (deduped by id, new entry
     * first) so create/join do not require a full reload.
     */
    private fun mergeGroupIntoList(group: GroupDto) {
        _uiState.update { current ->
            if (current is DashboardUiState.Ready) {
                val withoutDuplicate =
                    current.groups.filter { it.id == null || it.id != group.id }
                current.copy(groups = listOf(group) + withoutDuplicate)
            } else {
                current
            }
        }
    }
}
