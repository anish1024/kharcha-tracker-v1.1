package com.kharcha.tracker

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kharcha.tracker.core.util.Format
import com.kharcha.tracker.data.remote.dto.ExpenseDto
import com.kharcha.tracker.ui.dashboard.GroupDetailUiState
import com.kharcha.tracker.ui.dashboard.GroupDetailViewModel

/**
 * Stage 5 group detail: header (name/currency), a summary card (total
 * spent, expense count, the signed-in user's net balance) and the
 * group's recent expenses, newest first as returned by the gateway.
 *
 * [viewModel] is created per back-stack entry by [AppNavHost]; [groupId]
 * is passed so the state classes render with the right context before
 * the first frame of data arrives.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupDetailScreen(
    groupId: String,
    viewModel: GroupDetailViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()

    val title = (state as? GroupDetailUiState.Ready)
        ?.group
        ?.name
        ?.takeIf { it.isNotBlank() }
        ?: "Group"

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when (val s = state) {
                is GroupDetailUiState.Loading -> ScreenLoading()

                is GroupDetailUiState.Error -> ScreenError(
                    message = s.message,
                    onRetry = { viewModel.load() }
                )

                is GroupDetailUiState.Ready -> DetailContent(s)
            }
        }
    }
}

@Composable
private fun DetailContent(state: GroupDetailUiState.Ready) {
    val currency = state.group.currency

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "currency") {
            if (!currency.isNullOrBlank()) {
                Text(
                    text = currency,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
            }
        }

        item(key = "summary") {
            SummaryCard(
                totalSpent = state.totalSpent,
                expenseCount = state.expenses.size,
                balance = state.myNetDues?.let { Format.netPosition(it, currency) },
                currency = currency
            )
        }

        if (state.expenses.isEmpty()) {
            item(key = "empty") {
                Text(
                    text = "No expenses yet",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            return@LazyColumn
        }

        item(key = "expenses-heading") {
            Text(
                text = "Recent expenses",
                style = MaterialTheme.typography.titleMedium
            )
        }

        items(state.expenses, key = { it.transId }) { expense ->
            ExpenseRow(expense = expense, currency = currency)
        }
    }
}

@Composable
private fun SummaryCard(
    totalSpent: Double,
    expenseCount: Int,
    balance: String?,
    currency: String?
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            SummaryRow("Total spent", Format.amount(totalSpent, currency))
            Spacer(modifier = Modifier.height(8.dp))
            SummaryRow("Expenses", "$expenseCount")
            Spacer(modifier = Modifier.height(8.dp))
            SummaryRow("Your balance", balance ?: "Balance unavailable")
        }
    }
}

@Composable
private fun SummaryRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun ExpenseRow(expense: ExpenseDto, currency: String?) {
    val title = expense.description?.takeIf { it.isNotBlank() }
        ?: expense.category?.takeIf { it.isNotBlank() }
        ?: "Expense"
    val subtitle = buildList {
        expense.category?.takeIf { it.isNotBlank() }?.let { add(it) }
        expense.dateTime?.takeIf { it.isNotBlank() }?.let {
            val formatted = Format.dateTime(it)
            add(if (formatted == it) it else formatted)
        }
    }.joinToString(" · ")

    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall
                )
                if (subtitle.isNotBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = Format.amount(expense.amount, currency),
                style = MaterialTheme.typography.titleSmall
            )
        }
    }
}
