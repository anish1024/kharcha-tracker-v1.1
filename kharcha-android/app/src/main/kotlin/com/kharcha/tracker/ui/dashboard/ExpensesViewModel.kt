package com.kharcha.tracker.ui.dashboard

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kharcha.tracker.core.util.NetworkResult
import com.kharcha.tracker.core.util.safeCall
import com.kharcha.tracker.data.local.LastExpenseGroupStore
import com.kharcha.tracker.KharchaApplication
import com.kharcha.tracker.data.remote.ApiService
import com.kharcha.tracker.data.remote.NetworkModule
import com.kharcha.tracker.data.remote.dto.ExpenseDto
import com.kharcha.tracker.data.remote.dto.GroupDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ExpensesViewModel(
    private val api: ApiService = NetworkModule.api,
    private val store: LastExpenseGroupStore = LastExpenseGroupStore(KharchaApplication.appContext)
) : ViewModel() {

    private val _uiState = MutableStateFlow<ExpensesUiState>(ExpensesUiState.Loading)
    val uiState: StateFlow<ExpensesUiState> = _uiState.asStateFlow()

    init {
        loadExpenses()
    }

    fun loadExpenses() {
        viewModelScope.launch {
            _uiState.value = ExpensesUiState.Loading
            val groupsResult = safeCall { api.myGroups() }
            val groups = (groupsResult as? NetworkResult.Success)?.data ?: emptyList()
            if (groups.isEmpty()) {
                _uiState.value = ExpensesUiState.Empty
                return@launch
            }
            val stored = try { store.getLastGroupId().first() } catch (_: Exception) { null }
            val selected = resolveInitialExpenseGroupId(groups, stored)
            val expenses = if (selected != null) {
                (safeCall { api.groupExpenses(selected, 50) } as? NetworkResult.Success)?.data ?: emptyList()
            } else emptyList()
            _uiState.value = ExpensesUiState.Ready(
                groups = groups,
                selectedGroupId = selected,
                expenses = expenses
            )
        }
    }

    fun selectGroup(groupId: String) {
        viewModelScope.launch {
            store.setLastGroupId(groupId)
            _uiState.update { current ->
                if (current is ExpensesUiState.Ready) {
                    val expenses = (safeCall { api.groupExpenses(groupId, 50) } as? NetworkResult.Success)?.data ?: emptyList()
                    current.copy(
                        selectedGroupId = groupId,
                        expenses = expenses
                    )
                } else {
                    current
                }
            }
        }
    }

    fun addExpenseClicked() {
        // Navigation to existing add-expense route is handled by caller / Navigation
    }
}

fun resolveInitialExpenseGroupId(groups: List<GroupDto>, storedId: String?): String? {
    if (storedId != null && groups.any { it.id == storedId }) return storedId
    return groups.firstOrNull()?.id
}