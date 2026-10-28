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
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.kharcha.tracker.core.util.Format
import com.kharcha.tracker.data.remote.dto.GroupDto
import com.kharcha.tracker.data.remote.dto.UserDto
import com.kharcha.tracker.ui.dashboard.DashboardUiState
import com.kharcha.tracker.ui.dashboard.DashboardViewModel

/**
 * Stage 5 dashboard: the signed-in user's profile card and the list of
 * groups they belong to. Tapping a group opens [GroupDetailScreen].
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Kharcha") },
                actions = {
                    TextButton(onClick = onSignOut) {
                        Text("Sign out")
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
                text = "Group creation and invitations arrive in the next stage.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
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
