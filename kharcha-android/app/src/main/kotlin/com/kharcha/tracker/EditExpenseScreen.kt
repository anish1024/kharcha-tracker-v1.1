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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.kharcha.tracker.core.util.Format
import com.kharcha.tracker.ui.dashboard.EditDeleteUiState
import com.kharcha.tracker.ui.dashboard.EditExpenseFormState
import com.kharcha.tracker.ui.dashboard.EditExpenseUiState
import com.kharcha.tracker.ui.dashboard.EditExpenseViewModel
import com.kharcha.tracker.ui.dashboard.EditSubmitUiState
import com.kharcha.tracker.ui.dashboard.hasChanges

/**
 * Edit-expense screen (Stage 7a): date, category, description, and the
 * payer. The amount is shown read-only - the gateway's `ExpenseUpdate`
 * has no amount field, so it can't be changed here.
 *
 * Form state and validation live in [EditExpenseViewModel] (the pure
 * `validateEditExpenseForm` / `buildExpenseUpdateRequest` functions are
 * unit tested). Save success -> "Expense updated" snackbar + pop back;
 * save failure -> the server's message as a snackbar (cleared via
 * [EditExpenseViewModel.clearSubmitError] so a later failure re-fires
 * this effect). Delete is confirmed in a dialog; its failure is shown
 * inline in the dialog, and its success -> "Expense deleted" + pop back.
 *
 * Live validation is shown only after the first submit attempt, matching
 * the add-expense screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditExpenseScreen(
    viewModel: EditExpenseViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val form by viewModel.form.collectAsState()
    val submitState by viewModel.submitState.collectAsState()
    val deleteState by viewModel.deleteState.collectAsState()
    val validationError by viewModel.validationError.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    var attempted by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    // One-shot submit results: confirm with a snackbar, then act.
    LaunchedEffect(submitState) {
        when (val result = submitState) {
            is EditSubmitUiState.Success -> {
                snackbarHostState.showSnackbar(
                    "Expense updated",
                    duration = SnackbarDuration.Short
                )
                onBack()
            }

            is EditSubmitUiState.Failed -> {
                snackbarHostState.showSnackbar(
                    result.message,
                    duration = SnackbarDuration.Long
                )
                viewModel.clearSubmitError()
            }

            else -> Unit
        }
    }

    // One-shot delete result: the failure stays in the dialog (it reads
    // [deleteState] directly); success -> snackbar + pop back.
    LaunchedEffect(deleteState) {
        if (deleteState is EditDeleteUiState.Deleted) {
            snackbarHostState.showSnackbar(
                "Expense deleted",
                duration = SnackbarDuration.Short
            )
            onBack()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Edit expense") },
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
                is EditExpenseUiState.Loading -> ScreenLoading()

                is EditExpenseUiState.Error -> ScreenError(
                    message = s.message,
                    onRetry = { viewModel.load() }
                )

                is EditExpenseUiState.Ready -> FormBody(
                    state = s,
                    form = form,
                    validationError = validationError,
                    attempted = attempted,
                    submitting = submitState is EditSubmitUiState.Busy,
                    onDeleteClick = { showDeleteDialog = true },
                    viewModel = viewModel
                ) {
                    attempted = true
                    viewModel.submit()
                }
            }
        }
    }

    if (showDeleteDialog) {
        DeleteExpenseDialog(
            deleteState = deleteState,
            onDelete = { viewModel.delete() },
            onDismiss = {
                showDeleteDialog = false
                viewModel.clearDeleteError()
            }
        )
    }
}

/** One selectable payer: the signed-in user first, then the other members. */
private data class EditPayerOption(val id: String, val label: String)

@Composable
private fun FormBody(
    state: EditExpenseUiState.Ready,
    form: EditExpenseFormState,
    validationError: String?,
    attempted: Boolean,
    submitting: Boolean,
    onDeleteClick: () -> Unit,
    viewModel: EditExpenseViewModel,
    onSubmit: () -> Unit
) {
    // Payer options: me first, then the other members; the original payer
    // is always offered (it may not be in the member list if that fetch
    // failed).
    val payerOptions = buildList {
        val myId = state.myUserId
        if (myId != null) {
            add(EditPayerOption(myId, "You"))
        }
        state.members
            .filter { it.id != null && it.id != myId }
            .forEach { member ->
                val label = member.name?.takeIf { it.isNotBlank() }
                    ?: member.id
                    ?: "Member"
                add(EditPayerOption(member.id!!, label))
            }
        val initial = form.initialPayerId
        if (initial != null && none { it.id == initial }) {
            add(EditPayerOption(initial, "Current payer"))
        }
    }

    val changed = hasChanges(form)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Amount: fixed - the gateway doesn't accept a new amount on edit.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Amount",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "Can't be changed",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = Format.amount(form.amount, null),
                style = MaterialTheme.typography.titleMedium
            )
        }

        OutlinedTextField(
            value = form.date,
            onValueChange = { viewModel.setDate(it) },
            label = { Text("Date") },
            supportingText = { Text("YYYY-MM-DD; leave unchanged to keep the original") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = form.category,
            onValueChange = { viewModel.setCategory(it) },
            label = { Text("Category") },
            supportingText = {
                Text(
                    if (form.initialCategory.isNullOrBlank()) "Optional"
                    else "Can't be removed - keep it or enter a new one"
                )
            },
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = form.description,
            onValueChange = { viewModel.setDescription(it) },
            label = { Text("Description") },
            supportingText = { Text("Empty clears it") },
            modifier = Modifier.fillMaxWidth()
        )

        SectionTitle("Paid by")
        if (payerOptions.isEmpty()) {
            Text(
                text = "The original payer stays - member list unavailable.",
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

        if (attempted && validationError != null) {
            Text(
                text = validationError,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error
            )
        }

        Button(
            onClick = onSubmit,
            enabled = !submitting && changed,
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
            Text(if (submitting) "Saving…" else "Save changes")
        }

        if (!changed) {
            Text(
                text = "Change a field to enable saving.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        OutlinedButton(
            onClick = onDeleteClick,
            enabled = !submitting,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
        ) {
            Text("Delete expense")
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

/**
 * Delete confirmation: the server's refusal (group not OPEN, or a
 * PAID/SETTLED payout references the expense) is shown inline; the dialog
 * stays open on failure and the user can dismiss it or retry.
 */
@Composable
private fun DeleteExpenseDialog(
    deleteState: EditDeleteUiState,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    val busy = deleteState is EditDeleteUiState.Busy
    val error = (deleteState as? EditDeleteUiState.Failed)?.message
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("Delete expense?") },
        text = {
            Column {
                Text(
                    text = "This removes the expense from the group. This can't be undone.",
                    style = MaterialTheme.typography.bodyMedium
                )
                if (error != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDelete,
                enabled = !busy,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                if (busy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(if (busy) "Deleting…" else "Delete")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !busy
            ) {
                Text("Cancel")
            }
        }
    )
}
