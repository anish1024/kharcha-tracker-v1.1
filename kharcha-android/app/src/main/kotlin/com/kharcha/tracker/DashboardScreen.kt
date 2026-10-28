package com.kharcha.tracker

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.kharcha.tracker.core.util.Format
import com.kharcha.tracker.data.remote.dto.GroupDto
import com.kharcha.tracker.data.remote.dto.UserDto
import com.kharcha.tracker.ui.dashboard.DashboardUiState
import com.kharcha.tracker.ui.dashboard.DashboardViewModel
import com.kharcha.tracker.ui.dashboard.GroupActionKind
import com.kharcha.tracker.ui.dashboard.GroupActionUiState

/**
 * Stage 5 dashboard (profile card + group list) with Stage 6 group
 * actions: the overflow menu offers "Create group", "Join group" and
 * "Sign out". Created/joined groups merge into the list in place; a newly
 * created group also navigates straight to its detail screen.
 *
 * Render state comes from [DashboardViewModel]; navigation callbacks are
 * supplied by [AppNavHost] (see [Navigation.kt]).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    onGroupClick: (String) -> Unit,
    onSignOut: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val actionState by viewModel.actionState.collectAsState()
    val createdGroup by viewModel.createdGroup.collectAsState()

    var showCreateDialog by remember { mutableStateOf(false) }
    var showJoinDialog by remember { mutableStateOf(false) }

    // A freshly created group: open it, then clear the one-shot signal so
    // the effect cannot re-fire.
    LaunchedEffect(createdGroup) {
        val group = createdGroup ?: return@LaunchedEffect
        viewModel.consumeCreatedGroup()
        onGroupClick(group.id.orEmpty())
    }

    // Re-fetch (silently, in the background) whenever the screen is
    // (re-)entered, so create/join/leave actions made elsewhere are
    // reflected. The VM guards the initial-entry call as a no-op.
    ReloadOnResume { viewModel.refresh() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Kharcha") },
                actions = {
                    DashboardOverflowMenu(
                        onCreateGroup = { showCreateDialog = true },
                        onJoinGroup = { showJoinDialog = true },
                        onSignOut = onSignOut
                    )
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
                is DashboardUiState.Loading -> ScreenLoading()

                is DashboardUiState.Error -> ScreenError(
                    message = s.message,
                    onRetry = { viewModel.load() }
                )

                is DashboardUiState.Ready -> DashboardContent(
                    user = s.user,
                    groups = s.groups,
                    onGroupClick = onGroupClick
                )
            }
        }
    }

    if (showCreateDialog) {
        CreateGroupDialog(
            busy = actionState is GroupActionUiState.Busy,
            error = (actionState as? GroupActionUiState.Failed)
                ?.takeIf { it.kind == GroupActionKind.CREATE }
                ?.message,
            onDismiss = {
                viewModel.clearActionError()
                showCreateDialog = false
            },
            onSubmit = { name, currency ->
                viewModel.createGroup(name, currency)
            }
        )
    }

    if (showJoinDialog) {
        JoinGroupDialog(
            busy = actionState is GroupActionUiState.Busy,
            error = (actionState as? GroupActionUiState.Failed)
                ?.takeIf { it.kind == GroupActionKind.JOIN }
                ?.message,
            onDismiss = {
                viewModel.clearActionError()
                showJoinDialog = false
            },
            onSubmit = { groupId, inviteCode ->
                viewModel.joinGroup(groupId, inviteCode)
            }
        )
    }
}

/**
 * The top-bar overflow: group actions plus sign-out (moved here from the
 * top bar so the action row stays focused).
 */
@Composable
private fun DashboardOverflowMenu(
    onCreateGroup: () -> Unit,
    onJoinGroup: () -> Unit,
    onSignOut: () -> Unit
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
                text = { Text("Create group") },
                onClick = {
                    expanded = false
                    onCreateGroup()
                }
            )
            DropdownMenuItem(
                text = { Text("Join group") },
                onClick = {
                    expanded = false
                    onJoinGroup()
                }
            )
            DropdownMenuItem(
                text = { Text("Sign out") },
                onClick = {
                    expanded = false
                    onSignOut()
                }
            )
        }
    }
}

@Composable
private fun DashboardContent(
    user: UserDto?,
    groups: List<GroupDto>,
    onGroupClick: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (user != null) {
            item(key = "user") {
                UserCard(user = user)
            }
        }

        if (groups.isEmpty()) {
            item(key = "empty") {
                EmptyGroups()
            }
            return@LazyColumn
        }

        item(key = "heading") {
            Text(
                text = "Your groups (${groups.size})",
                style = MaterialTheme.typography.titleMedium
            )
        }

        items(groups, key = { it.id ?: it.name.orEmpty() }) { group ->
            GroupItem(group = group, onClick = { onGroupClick(group.id.orEmpty()) })
        }
    }
}

@Composable
private fun UserCard(user: UserDto) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            InitialsBadge(name = user.name?.takeIf { it.isNotBlank() } ?: user.email.orEmpty())
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    text = user.name?.takeIf { it.isNotBlank() } ?: "—",
                    style = MaterialTheme.typography.titleMedium
                )
                if (!user.email.isNullOrBlank()) {
                    Text(
                        text = user.email,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun InitialsBadge(name: String) {
    val initial = name.trim().firstOrNull()?.uppercase() ?: "?"
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = initial,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
}

@Composable
private fun GroupItem(group: GroupDto, onClick: () -> Unit) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = group.name?.takeIf { it.isNotBlank() } ?: "Untitled group",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = buildList {
                    group.currency?.takeIf { it.isNotBlank() }?.let { add(it) }
                    val count = group.members.size
                    add("$count member" + if (count == 1) "" else "s")
                }.joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            group.lastUpdated?.takeIf { it.isNotBlank() }?.let {
                val formatted = Format.date(it)
                if (formatted != it) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Updated $formatted",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyGroups() {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "No groups yet",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Create a group, or join one with an invite code, from the menu above.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * "Create group" dialog: name + currency. [busy] disables the actions and
 * [error] (a server message for a CREATE action) is shown inline.
 */
@Composable
private fun CreateGroupDialog(
    busy: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onSubmit: (name: String, currency: String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var currency by remember { mutableStateOf("INR") }
    var nameError by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("Create group") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        if (nameError != null) nameError = null
                    },
                    label = { Text("Group name") },
                    singleLine = true,
                    isError = nameError != null,
                    supportingText = {
                        if (nameError != null) Text(nameError!!)
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = currency,
                    onValueChange = { currency = it.uppercase().take(3) },
                    label = { Text("Currency (e.g. INR)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (error != null) {
                    ErrorText(error)
                }
            }
        },
        confirmButton = {
            DialogConfirmButton(
                busy = busy,
                busyLabel = "Creating…",
                idleLabel = "Create",
                onClick = {
                    val trimmed = name.trim()
                    if (trimmed.isEmpty()) {
                        nameError = "Enter a group name"
                    } else {
                        onSubmit(trimmed, currency.trim())
                    }
                }
            )
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !busy) {
                Text("Cancel")
            }
        }
    )
}

/**
 * "Join group" dialog: the group id (shared by a member) plus the invite
 * code. [busy] disables the actions and [error] (a server message for a
 * JOIN action) is shown inline.
 */
@Composable
private fun JoinGroupDialog(
    busy: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onSubmit: (groupId: String, inviteCode: String) -> Unit
) {
    var groupId by remember { mutableStateOf("") }
    var inviteCode by remember { mutableStateOf("") }
    var fieldError by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("Join group") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = groupId,
                    onValueChange = {
                        groupId = it
                        if (fieldError != null) fieldError = null
                    },
                    label = { Text("Group id") },
                    singleLine = true,
                    isError = fieldError != null,
                    supportingText = {
                        if (fieldError != null) Text(fieldError!!)
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = inviteCode,
                    onValueChange = { inviteCode = it },
                    label = { Text("Invite code") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (error != null) {
                    ErrorText(error)
                }
            }
        },
        confirmButton = {
            DialogConfirmButton(
                busy = busy,
                busyLabel = "Joining…",
                idleLabel = "Join",
                onClick = {
                    val id = groupId.trim()
                    val code = inviteCode.trim()
                    if (id.isEmpty() || code.isEmpty()) {
                        fieldError = "Enter the group id and the invite code"
                    } else {
                        onSubmit(id, code)
                    }
                }
            )
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !busy) {
                Text("Cancel")
            }
        }
    )
}

/** Confirm button with a small spinner while [busy]. */
@Composable
private fun DialogConfirmButton(
    busy: Boolean,
    busyLabel: String,
    idleLabel: String,
    onClick: () -> Unit
) {
    TextButton(onClick = onClick, enabled = !busy) {
        if (busy) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(if (busy) busyLabel else idleLabel)
    }
}

/** Server error text inside a dialog body. */
@Composable
private fun ErrorText(message: String) {
    Text(
        text = message,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error
    )
}

@Composable
internal fun ScreenLoading() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator()
    }
}

@Composable
internal fun ScreenError(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.error
        )
        Spacer(modifier = Modifier.height(16.dp))
        TextButton(onClick = onRetry) {
            Text("Try again")
        }
    }
}
