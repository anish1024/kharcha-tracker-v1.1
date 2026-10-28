package com.kharcha.tracker.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kharcha.tracker.core.util.NetworkResult
import com.kharcha.tracker.data.remote.NetworkModule
import com.kharcha.tracker.data.remote.dto.GroupDto
import com.kharcha.tracker.data.remote.dto.UserDto
import com.kharcha.tracker.data.repository.DashboardRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

/**
 * Loads the signed-in profile and the user's groups for the dashboard.
 *
 * No-arg constructor (default [ViewModel] factory): the network stack is a
 * process-wide singleton initialised in [com.kharcha.tracker.MainActivity].
 * The VM loads once on creation; navigation keeps it alive on the back
 * stack, so returning from the group detail does not refetch.
 */
class DashboardViewModel : ViewModel() {

    private val repository = DashboardRepository(NetworkModule.api)

    private val _uiState = MutableStateFlow<DashboardUiState>(DashboardUiState.Loading)
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

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
        }
    }
}
