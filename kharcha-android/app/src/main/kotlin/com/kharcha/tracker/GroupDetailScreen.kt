package com.kharcha.tracker

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.kharcha.tracker.core.util.Format
import com.kharcha.tracker.data.remote.dto.ExpenseDto
import com.kharcha.tracker.data.remote.dto.GroupMemberDto
import com.kharcha.tracker.data.remote.dto.PayoutDto
import com.kharcha.tracker.ui.dashboard.GroupDetailUiState
import com.kharcha.tracker.ui.dashboard.GroupDetailViewModel
import com.kharcha.tracker.ui.dashboard.InviteCodeUiState
import com.kharcha.tracker.ui.dashboard.LeaveUiState
import com.kharcha.tracker.ui.dashboard.LockUiState
import com.kharcha.tracker.ui.dashboard.PayoutAction
import com.kharcha.tracker.ui.dashboard.PayoutActionUiState
import com.kharcha.tracker.ui.dashboard.PayoutActions
import com.kharcha.tracker.ui.dashboard.SettleUiState
import kotlinx.coroutines.launch

/**
 * Group detail: header (name/currency), summary card (total spent, expense
 * count, the signed-in user's net balance), the group's members (Stage 6)
 * and the recent expenses.
 *
 * Stage 6 additions: the "Add expense" action button, the overflow menu
 * (copy invite code / leave group), and the invite-code and leave-group
 * dialogs. [onAddExpense] is supplied by [AppNavHost].
 *
 * Stage 7a: the settlement section (proposed payouts + "Settle group"),
 * the payouts section (role-gated actions from [PayoutActions] with
 * confirm dialogs), tappable expense rows ([onEditExpense] is supplied
 * by [AppNavHost]), and the lock/unlock overflow item.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupDetailScreen(
    groupId: String,
    viewModel: GroupDetailViewModel,
    onBack: () -> Unit,
    onAddExpense: () -> Unit,
    onEditExpense: (Int) -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val inviteState by viewModel.inviteCode.collectAsState()
    val leaveState by viewModel.leaveState.collectAsState()
    val settleState by viewModel.settleState.collectAsState()
    val payoutActionState by viewModel.payoutActionState.collectAsState()
    val lockState by viewModel.lockState.collectAsState()

    var showLeaveDialog by remember { mutableStateOf(false) }
    var payoutActionTarget by remember { mutableStateOf<Pair<PayoutDto, PayoutAction>?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // Invite code: a failure is a snackbar + reset (so a later attempt
    // re-fires this effect); a success drives the dialog below.
    LaunchedEffect(inviteState) {
        val message = (inviteState as? InviteCodeUiState.Failed)?.message
            ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message, duration = SnackbarDuration.Long)
        viewModel.dismissInviteCode()
    }

    // Leaving: close the confirm dialog, confirm with a snackbar, then pop
    // back to the dashboard (its refresh-on-resume drops the group).
    LaunchedEffect(leaveState) {
        if (leaveState !is LeaveUiState.Left) return@LaunchedEffect
        showLeaveDialog = false
        snackbarHostState.showSnackbar("Left the group", duration = SnackbarDuration.Short)
        onBack()
    }

    // Settle group (Stage 7a): snackbar the outcome, then reset the state
    // so a later attempt re-fires this effect.
    LaunchedEffect(settleState) {
        val message = when (val s = settleState) {
            is SettleUiState.Success -> s.message
            is SettleUiState.Failed -> s.message
            else -> return@LaunchedEffect
        }
        val success = settleState is SettleUiState.Success
        snackbarHostState.showSnackbar(
            message,
            duration = if (success) SnackbarDuration.Short else SnackbarDuration.Long
        )
        viewModel.clearSettleState()
    }

    // Payout action (Stage 7a): success -> snackbar + close the dialog.
    // A failure stays in [payoutActionState]; the open dialog shows it
    // inline and [clearPayoutActionState] happens on dismiss.
    LaunchedEffect(payoutActionState) {
        val success = payoutActionState as? PayoutActionUiState.Success
            ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(
            success.message,
            duration = SnackbarDuration.Short
        )
        payoutActionTarget = null
        viewModel.clearPayoutActionState()
    }

    // Lock/unlock (Stage 7a): snackbar the new status, then reset.
    LaunchedEffect(lockState) {
        val message = when (val s = lockState) {
            is LockUiState.Success -> if (s.nowLocked) "Group locked" else "Group unlocked"
            is LockUiState.Failed -> s.message
            else -> return@LaunchedEffect
        }
        val success = lockState is LockUiState.Success
        snackbarHostState.showSnackbar(
            message,
            duration = if (success) SnackbarDuration.Short else SnackbarDuration.Long
        )
        viewModel.clearLockState()
    }

    // Re-fetch (silently) when the screen is (re-)entered, e.g. returning
    // from the add-expense screen. The VM guards the initial entry as a no-op.
    ReloadOnResume { viewModel.refresh() }

    val title = (state as? GroupDetailUiState.Ready)
        ?.group
        ?.name
        ?.takeIf { it.isNotBlank() }
        ?: "Group"

    // Lock/unlock (Stage 7a) is only offered for the two statuses the
    // server allows the transition from: OPEN -> lock, LOCKED -> unlock.
    val lockLabel = when ((state as? GroupDetailUiState.Ready)?.group?.status) {
        "OPEN" -> "Lock group"
        "LOCKED" -> "Unlock group"
        else -> null
    }

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
                },
                actions = {
                    DetailOverflowMenu(
                        onCopyInviteCode = { viewModel.generateInviteCode() },
                        onLeaveGroup = { showLeaveDialog = true },
                        lockLabel = lockLabel,
                        onToggleLock = { viewModel.toggleLock() }
                    )
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddExpense,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Add expense") }
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
                is GroupDetailUiState.Loading -> ScreenLoading()

                is GroupDetailUiState.Error -> ScreenError(
                    message = s.message,
                    onRetry = { viewModel.load() }
                )

                is GroupDetailUiState.Ready -> DetailContent(
                    state = s,
                    settleBusy = settleState is SettleUiState.Busy,
                    payoutActionState = payoutActionState,
                    onSettleGroup = { viewModel.settleGroup() },
                    onPayoutAction = { payout, action -> payoutActionTarget = payout to action },
                    onEditExpense = onEditExpense
                )
            }
        }
    }

    // Invite-code dialog (success state only).
    (inviteState as? InviteCodeUiState.Ready)?.let { ready ->
        val group = (state as? GroupDetailUiState.Ready)?.group
        InviteCodeDialog(
            groupId = group?.id.orEmpty(),
            code = ready.code,
            onCopied = {
                viewModel.dismissInviteCode()
                scope.launch {
                    snackbarHostState.showSnackbar(
                        "Copied invite code",
                        duration = SnackbarDuration.Short
                    )
                }
            },
            onDismiss = { viewModel.dismissInviteCode() }
        )
    }

    if (showLeaveDialog) {
        LeaveGroupDialog(
            busy = leaveState is LeaveUiState.Busy || leaveState is LeaveUiState.Left,
            error = (leaveState as? LeaveUiState.Failed)?.message,
            onConfirm = { viewModel.leaveGroup() },
            onDismiss = {
                viewModel.resetLeaveState()
                showLeaveDialog = false
            }
        )
    }

    // Payout-action confirm dialog (Stage 7a): evidence optional, reason
    // required for MARK_FAILED / CANCEL. A failure stays inline in the
    // dialog until it is dismissed.
    payoutActionTarget?.let { (payout, action) ->
        PayoutActionDialog(
            payout = payout,
            action = action,
            busy = payoutActionState is PayoutActionUiState.Busy,
            error = (payoutActionState as? PayoutActionUiState.Failed)
                ?.takeIf { it.payoutId == payout.id }
                ?.message,
            onConfirm = { evidence, reason ->
                viewModel.performPayoutAction(payout.id.orEmpty(), action, evidence, reason)
            },
            onDismiss = {
                payoutActionTarget = null
                viewModel.clearPayoutActionState()
            }
        )
    }
}

/**
 * The top-bar overflow: "Copy invite code" (derives and opens the code
 * dialog), optionally "Lock group" / "Unlock group" (Stage 7a - only for
 * the status the transition applies to) and "Leave group" (opens the
 * confirm dialog).
 */
@Composable
private fun DetailOverflowMenu(
    onCopyInviteCode: () -> Unit,
    onLeaveGroup: () -> Unit,
    lockLabel: String?,
    onToggleLock: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(
                imageVector = Icons.Filled.MoreVert,
                contentDescription = "More options"
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            DropdownMenuItem(
                text = { Text("Copy invite code") },
                onClick = {
                    expanded = false
                    onCopyInviteCode()
                }
            )
            if (lockLabel != null) {
                DropdownMenuItem(
                    text = { Text(lockLabel) },
                    onClick = {
                        expanded = false
                        onToggleLock()
                    }
                )
            }
            DropdownMenuItem(
                text = { Text("Leave group") },
                onClick = {
                    expanded = false
                    onLeaveGroup()
                }
            )
        }
    }
}

@Composable
private fun DetailContent(
    state: GroupDetailUiState.Ready,
    settleBusy: Boolean,
    payoutActionState: PayoutActionUiState,
    onSettleGroup: () -> Unit,
    onPayoutAction: (PayoutDto, PayoutAction) -> Unit,
    onEditExpense: (Int) -> Unit
) {
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

        // Stage 7a: what the next settlement would create, plus the action.
        item(key = "settlement") {
            SettlementSection(
                proposed = state.proposedPayouts,
                members = state.members,
                myUserId = state.myUserId,
                currency = currency,
                busy = settleBusy,
                onSettle = onSettleGroup
            )
        }

        // Stage 7a: the group's payout records and their role-gated actions.
        if (state.payouts.isNotEmpty()) {
            item(key = "payouts-heading") {
                Text(
                    text = "Payouts (${state.payouts.size})",
                    style = MaterialTheme.typography.titleMedium
                )
            }
            itemsIndexed(state.payouts, key = { index, _ -> "payout-$index" }) { index, payout ->
                PayoutRow(
                    payout = payout,
                    members = state.members,
                    myUserId = state.myUserId,
                    currency = currency,
                    busy = payoutActionState is PayoutActionUiState.Busy &&
                        (payoutActionState as PayoutActionUiState.Busy).payoutId == payout.id,
                    onAction = { action -> onPayoutAction(payout, action) }
                )
            }
        }

        if (state.members.isNotEmpty()) {
            item(key = "members-heading") {
                Text(
                    text = "Members (${state.members.size})",
                    style = MaterialTheme.typography.titleMedium
                )
            }
            items(state.members, key = { member -> member.id ?: member.name.orEmpty() }) { member ->
                MemberRow(
                    member = member,
                    isMe = member.id != null && member.id == state.myUserId
                )
            }
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
            ExpenseRow(
                expense = expense,
                currency = currency,
                onClick = { onEditExpense(expense.transId) }
            )
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
private fun MemberRow(member: GroupMemberDto, isMe: Boolean) {
    val label = buildList {
        add(member.name?.takeIf { it.isNotBlank() } ?: member.id.orEmpty() ?: "Member")
        if (isMe) add(" (You)")
    }.joinToString("")

    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyLarge
                )
                member.email?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (isMe) {
                Text(
                    text = "You",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun ExpenseRow(expense: ExpenseDto, currency: String?, onClick: () -> Unit) {
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

    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
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

/**
 * Resolves a user id to a display name: the member's name when known,
 * "You" for the signed-in user, otherwise the raw id (the members fetch
 * may have failed - the row still renders).
 */
private fun memberName(members: List<GroupMemberDto>, myUserId: String?, id: String?): String {
    if (id.isNullOrBlank()) return "Unknown"
    val name = members.firstOrNull { it.id == id }?.name?.takeIf { it.isNotBlank() }
    return name ?: if (id == myUserId) "You" else id
}

/**
 * Stage 7a settlement section: what the next settlement would create
 * ("A pays B ₹X" per proposed payout) and the "Settle group" action
 * (a PERIODIC settlement, the server's default kind). [busy] is true
 * while the settle request is in flight.
 */
@Composable
private fun SettlementSection(
    proposed: List<PayoutDto>,
    members: List<GroupMemberDto>,
    myUserId: String?,
    currency: String?,
    busy: Boolean,
    onSettle: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Settlement",
                style = MaterialTheme.typography.titleMedium
            )
            if (proposed.isEmpty()) {
                Text(
                    text = "Nothing to pay out - the group's dues are even.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                proposed.forEach { payout ->
                    Text(
                        text = "${memberName(members, myUserId, payout.payorId)} pays " +
                            "${memberName(members, myUserId, payout.payeeId)} - " +
                            Format.amount(payout.amount, currency),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            Button(
                onClick = onSettle,
                enabled = !busy,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (busy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(if (busy) "Settling…" else "Settle group")
            }
        }
    }
}

/**
 * One payout record: payor -> payee, the amount, the status (and the
 * server's reason when it has one), plus the role-gated actions for the
 * signed-in user from [PayoutActions]. [busy] is true only while THIS
 * payout's action is in flight.
 */
@Composable
private fun PayoutRow(
    payout: PayoutDto,
    members: List<GroupMemberDto>,
    myUserId: String?,
    currency: String?,
    busy: Boolean,
    onAction: (PayoutAction) -> Unit
) {
    val actions = PayoutActions.actionsFor(payout, myUserId)
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${memberName(members, myUserId, payout.payorId)} pays " +
                            memberName(members, myUserId, payout.payeeId),
                        style = MaterialTheme.typography.titleSmall
                    )
                    val meta = buildList {
                        payout.status?.takeIf { it.isNotBlank() }?.let { add(it) }
                        payout.statusReason?.takeIf { it.isNotBlank() }?.let { add(it) }
                    }.joinToString(" · ")
                    if (meta.isNotBlank()) {
                        Text(
                            text = meta,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = Format.amount(payout.amount, currency),
                    style = MaterialTheme.typography.titleSmall
                )
            }
            if (actions.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    actions.forEach { action ->
                        TextButton(onClick = { onAction(action) }, enabled = !busy) {
                            Text(PayoutActions.label(action))
                        }
                    }
                }
            }
        }
    }
}

/**
 * Stage 7a confirm dialog for a [PayoutAction]: an optional evidence
 * note, and - for actions where the server requires one - a mandatory
 * reason (confirm is disabled until one is entered). [error] is the
 * server's refusal for this payout, shown inline; the dialog stays open
 * until dismissed.
 */
@Composable
private fun PayoutActionDialog(
    payout: PayoutDto,
    action: PayoutAction,
    busy: Boolean,
    error: String?,
    onConfirm: (evidence: String?, reason: String?) -> Unit,
    onDismiss: () -> Unit
) {
    val needsReason = PayoutActions.requiresReason(action)
    // Re-seed the fields whenever a different payout/action is targeted.
    var evidence by remember(payout.id, action) { mutableStateOf("") }
    var reason by remember(payout.id, action) { mutableStateOf("") }
    val confirmEnabled = !busy && (!needsReason || reason.isNotBlank())

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(PayoutActions.dialogTitle(action)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = PayoutActions.confirmText(action),
                    style = MaterialTheme.typography.bodyMedium
                )
                if (PayoutActions.allowsEvidence(action)) {
                    OutlinedTextField(
                        value = evidence,
                        onValueChange = { evidence = it },
                        label = { Text("Evidence") },
                        supportingText = { Text("Optional - e.g. a UPI reference") },
                        singleLine = true
                    )
                }
                if (needsReason) {
                    OutlinedTextField(
                        value = reason,
                        onValueChange = { reason = it },
                        label = { Text("Reason") },
                        supportingText = { Text("Required") },
                        singleLine = true
                    )
                }
                if (error != null) {
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(
                        evidence.trim().ifEmpty { null },
                        reason.trim().ifEmpty { null }
                    )
                },
                enabled = confirmEnabled
            ) {
                if (busy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(if (busy) "Working…" else PayoutActions.label(action))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !busy) {
                Text("Cancel")
            }
        }
    )
}

/**
 * Shows the derived invite code together with the group id (a joiner needs
 * both on the dashboard's "Join group" dialog). "Copy" copies the code to
 * the clipboard and reports via [onCopied].
 */
@Composable
private fun InviteCodeDialog(
    groupId: String,
    code: String,
    onCopied: () -> Unit,
    onDismiss: () -> Unit
) {
    val clipboard = LocalClipboardManager.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Invite code") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Share this code (and the group id) with the person you want to add.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Group id",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = groupId.takeIf { it.isNotBlank() } ?: "—",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Code",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = code,
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    clipboard.setText(AnnotatedString(code))
                    onCopied()
                }
            ) {
                Text("Copy")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

/**
 * "Leave group" confirm dialog. [busy] is true while the request runs or
 * after success (before the screen pops back); [error] is the server's
 * refusal message (e.g. an outstanding balance) shown inline.
 */
@Composable
private fun LeaveGroupDialog(
    busy: Boolean,
    error: String?,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("Leave group?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "You will no longer see this group or its expenses. " +
                        "Leaving is blocked until your balance is settled.",
                    style = MaterialTheme.typography.bodyMedium
                )
                if (error != null) {
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = !busy) {
                if (busy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(if (busy) "Leaving…" else "Leave")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !busy) {
                Text("Cancel")
            }
        }
    )
}
