@file:OptIn(ExperimentalLayoutApi::class)

package com.kharcha.tracker

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.kharcha.tracker.data.remote.dto.GroupMemberDto
import com.kharcha.tracker.ui.dashboard.AddExpenseUiState
import com.kharcha.tracker.ui.dashboard.AddExpenseViewModel
import com.kharcha.tracker.ui.dashboard.ExpenseFormState
import com.kharcha.tracker.ui.dashboard.SplitMode
import com.kharcha.tracker.ui.dashboard.SubmitUiState
import kotlin.math.abs

/**
 * Add-expense screen (Stage 6): description, amount, optional category,
 * the payer, and the split (equal, or by percentage per member).
 *
 * Form state and validation live in [AddExpenseViewModel] (the pure
 * `validateExpenseForm` / `buildAddExpenseRequest` functions are unit
 * tested); this screen renders the form and reports the submit outcome:
 * success -> "Expense added" snackbar, then pop back; failure -> the
 * server's message as a snackbar (then cleared via [AddExpenseViewModel.clearSubmitError]
 * so a later failure re-fires this effect).
 *
 * Live validation is shown only after the first submit attempt, so the
 * empty form does not start out red.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddExpenseScreen(
    viewModel: AddExpenseViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val form by viewModel.form.collectAsState()
    val submitState by viewModel.submitState.collectAsState()
    val validationError by viewModel.validationError.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    var attempted by remember { mutableStateOf(false) }

    // One-shot submit results: confirm with a snackbar, then act.
    LaunchedEffect(submitState) {
        when (val result = submitState) {
            is SubmitUiState.Success -> {
                snackbarHostState.showSnackbar(
                    "Expense added",
                    duration = SnackbarDuration.Short
                )
                onBack()
            }

            is SubmitUiState.Failed -> {
                snackbarHostState.showSnackbar(
                    result.message,
                    duration = SnackbarDuration.Long
                )
                viewModel.clearSubmitError()
            }

            else -> Unit
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Add expense") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when (val s = state) {
                is AddExpenseUiState.Loading -> ScreenLoading()

                is AddExpenseUiState.Error -> ScreenError(
                    message = s.message,
                    onRetry = { viewModel.load() }
                )

                is AddExpenseUiState.Ready -> FormBody(
                    state = s,
                    form = form,
                    validationError = validationError,
                    attempted = attempted,
                    submitting = submitState is SubmitUiState.Busy,
                    viewModel = viewModel
                ) {
                    attempted = true
                    viewModel.submit()
                }
            }
        }
    }
}

/** One selectable payer: the signed-in user first, then the other members. */
private data class PayerOption(val id: String, val label: String)

@Composable
private fun FormBody(
    state: AddExpenseUiState.Ready,
    form: ExpenseFormState,
    validationError: String?,
    attempted: Boolean,
    submitting: Boolean,
    viewModel: AddExpenseViewModel,
    onSubmit: () -> Unit
) {
    val members = state.members
    val namedMembers = members.filter { it.id != null }

    val payerOptions = buildList {
        val myId = state.myUserId
        if (myId != null) {
            val myName = state.myName?.takeIf { it.isNotBlank() }
            add(PayerOption(myId, myName?.let { "$it (You)" } ?: "You"))
        }
        namedMembers
            .filter { it.id != myId }
            .forEach { member ->
                val label = member.name?.takeIf { it.isNotBlank() }
                    ?: member.id
                    ?: "Member"
                add(PayerOption(member.id!!, label))
            }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        OutlinedTextField(
            value = form.description,
            onValueChange = { viewModel.setDescription(it) },
            label = { Text("Description") },
            supportingText = { Text("Optional") },
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = form.amountText,
            onValueChange = { viewModel.setAmount(it) },
            label = { Text("Amount") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = form.category,
            onValueChange = { viewModel.setCategory(it) },
            label = { Text("Category") },
            supportingText = { Text("Optional") },
            modifier = Modifier.fillMaxWidth()
        )

        SectionTitle("Paid by")
        if (payerOptions.isEmpty()) {
            Text(
                text = "You will pay the full amount.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                payerOptions.forEach { option ->
                    FilterChip(
                        selected = form.payerId == option.id,
                        onClick = { viewModel.setPayer(option.id) },
                        label = { Text(option.label) }
                    )
                }
            }
        }

        SectionTitle("Split")
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = form.splitMode == SplitMode.EQUAL,
                onClick = { viewModel.setSplitMode(SplitMode.EQUAL) },
                label = { Text("Equal") }
            )
            FilterChip(
                selected = form.splitMode == SplitMode.PERCENTAGE,
                onClick = { viewModel.setSplitMode(SplitMode.PERCENTAGE) },
                label = { Text("By percentage") }
            )
        }

        if (form.splitMode == SplitMode.PERCENTAGE) {
            if (namedMembers.isEmpty()) {
                Text(
                    text = "Member list unavailable - use an equal split.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            } else {
                namedMembers.forEach { member ->
                    PercentageRow(
                        member = member,
                        value = form.percentages[member.id] ?: "",
                        onValueChange = { viewModel.setPercentage(member.id!!, it) }
                    )
                }
                PercentageTotal(
                    values = form.percentages.values.toList(),
                    expectedCount = namedMembers.size
                )
            }
        }

        if (attempted && validationError != null) {
            Text(
                text = validationError,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error
            )
        }

        Button(
            onClick = onSubmit,
            enabled = !submitting,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
        ) {
            if (submitting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(12.dp))
            }
            Text(if (submitting) "Adding…" else "Add expense")
        }

        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium
    )
}

/** One member's percentage: name on the left, a "%" field on the right. */
@Composable
private fun PercentageRow(
    member: GroupMemberDto,
    value: String,
    onValueChange: (String) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = member.name?.takeIf { it.isNotBlank() } ?: member.id.orEmpty(),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.width(112.dp),
            singleLine = true,
            suffix = { Text("%") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
        )
    }
}

/**
 * Live total of the entered percentages, colored by validity (all fields
 * numeric, one per member, sum within 0.01 of 100).
 */
@Composable
private fun PercentageTotal(values: List<String>, expectedCount: Int) {
    val parsed = values.mapNotNull { it.trim().toDoubleOrNull() }
    val total = parsed.sum()
    val allFilled = values.size == expectedCount &&
        expectedCount > 0 &&
        values.all { it.trim().toDoubleOrNull() != null }
    val valid = allFilled && abs(total - 100.0) <= 0.01

    Text(
        text = "Total: %.2f%%".format(total),
        style = MaterialTheme.typography.bodyMedium,
        color = if (valid) MaterialTheme.colorScheme.onSurfaceVariant
        else MaterialTheme.colorScheme.error
    )
}
