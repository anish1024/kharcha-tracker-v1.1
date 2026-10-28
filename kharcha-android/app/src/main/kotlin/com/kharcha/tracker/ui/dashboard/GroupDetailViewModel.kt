package com.kharcha.tracker.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kharcha.tracker.core.util.NetworkResult
import com.kharcha.tracker.data.remote.NetworkModule
import com.kharcha.tracker.data.remote.dto.ExpenseDto
import com.kharcha.tracker.data.remote.dto.GroupDto
import com.kharcha.tracker.data.repository.DashboardRepository
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
 */
sealed class GroupDetailUiState {
    object Loading : GroupDetailUiState()
    data class Error(val message: String) : GroupDetailUiState()
    data class Ready(
        val group: GroupDto,
        val expenses: List<ExpenseDto>,
        val totalSpent: Double,
        val myNetDues: Double?
    ) : GroupDetailUiState()
}

/**
 * Loads one group's header, recent expenses and settlement preview.
 *
 * Constructor takes the route's [groupId]; navigation creates one VM per
 * back-stack entry, so each group keeps its own state. Data sources
 * degrade independently: a failed expense list renders empty, a failed
 * settle preview just hides the user's balance.
 */
class GroupDetailViewModel(
    private val groupId: String
) : ViewModel() {

    private val repository = DashboardRepository(NetworkModule.api)

    private val _uiState = MutableStateFlow<GroupDetailUiState>(GroupDetailUiState.Loading)
    val uiState: StateFlow<GroupDetailUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    /** (Re)loads group + expenses + settle - also the retry action. */
    fun load() {
        viewModelScope.launch {
            _uiState.value = GroupDetailUiState.Loading

            // Fail fast on the group itself: a 401/403/404 here means the
            // session or membership is gone (session cases force sign-out
            // via the auth interceptor + AuthViewModel).
            val groupResult = repository.group(groupId)
            val group = (groupResult as? NetworkResult.Success)?.data
            if (group == null) {
                _uiState.value = GroupDetailUiState.Error(
                    message = (groupResult as? NetworkResult.Error)?.message
                        ?: "Could not load the group"
                )
                return@launch
            }

            // Degrade independently: each missing piece hides only itself.
            val expenses = (repository.groupExpenses(groupId) as? NetworkResult.Success)?.data
                ?: emptyList()

            val netDues = (repository.settlementPreview(groupId) as? NetworkResult.Success)
                ?.data?.netDues
            val myId = (repository.me() as? NetworkResult.Success)?.data?.id
            val myNetDues = if (netDues != null && myId != null) netDues[myId] else null

            _uiState.value = GroupDetailUiState.Ready(
                group = group,
                expenses = expenses,
                totalSpent = expenses.sumOf { it.amount },
                myNetDues = myNetDues
            )
        }
    }
}
