package com.kharcha.tracker.ui.dashboard

import com.kharcha.tracker.data.remote.dto.ExpenseDto
import com.kharcha.tracker.data.remote.dto.GroupDto

sealed class ExpensesUiState {
    object Loading : ExpensesUiState()
    data class Error(val message: String) : ExpensesUiState()
    object Empty : ExpensesUiState()
    data class Ready(
        val groups: List<GroupDto>,
        val selectedGroupId: String?,
        val expenses: List<ExpenseDto>
    ) : ExpensesUiState()
}