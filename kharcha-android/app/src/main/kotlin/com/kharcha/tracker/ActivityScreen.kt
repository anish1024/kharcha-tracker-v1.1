package com.kharcha.tracker

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.kharcha.tracker.data.remote.dto.ActivityLogDto
import com.kharcha.tracker.data.remote.dto.GroupDto
import com.kharcha.tracker.data.remote.dto.GroupMemberDto
import com.kharcha.tracker.ui.dashboard.ActivityFeedState
import com.kharcha.tracker.ui.dashboard.ActivityTexts
import com.kharcha.tracker.ui.dashboard.ActivityUiState
import com.kharcha.tracker.ui.dashboard.ActivityViewModel

/**
 * Stage 7b activity screen: a group picker, an optional date-range filter,
 * and the group's audit-log feed (newest first, 20 per page with a pager).
 *
 * Row layout mirrors the web's activity table (When · Event · Actor ·
 * Details): a coloured event pill, the actor name ([ActivityTexts.actorName]
 * with the group's member list), the prettified detail
 * ([ActivityTexts.prettify]) and the timestamp. [onBack] is supplied by
 * [AppNavHost].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivityScreen(
    viewModel: ActivityViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val feedState by viewModel.feedState.collectAsState()

    var from by remember { mutableStateOf("") }
    var to by remember { mutableStateOf("") }
    var rangeError by remember { mutableStateOf<String?>(null) }

    // Selecting a different group resets the filter (and the VM's applied
    // range): clear the local fields too.
    LaunchedEffect((state as? ActivityUiState.Ready)?.selectedGroupId) {
        from = ""
        to = ""
        rangeError = null
    }

    // Re-fetch (silently) when the screen is (re-)entered. The VM guards the
    // initial entry as a no-op.
    ReloadOnResume { viewModel.refresh() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Activity") },
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
                is ActivityUiState.Loading -> ScreenLoading()

                is ActivityUiState.Error -> ScreenError(
                    message = s.message,
                    onRetry = { viewModel.load() }
                )

                is ActivityUiState.Ready -> if (s.groups.isEmpty()) {
                    // Web: "No groups yet — create one from the Groups screen first."
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "No groups yet — create one from the Groups screen first.",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                } else {
                    ActivityContent(
                        groups = s.groups,
                        selectedGroupId = s.selectedGroupId,
                        feedState = feedState,
                        from = from,
                        to = to,
                        rangeError = rangeError,
                        onFromChange = { from = it; rangeError = null },
                        onToChange = { to = it; rangeError = null },
                        onApplyRange = {
                            val fromValue = from.trim()
                            val toValue = to.trim()
                            val validFrom = fromValue.isEmpty() || ActivityTexts.isIsoDate(fromValue)
                            val validTo = toValue.isEmpty() || ActivityTexts.isIsoDate(toValue)
                            when {
                                !validFrom || !validTo ->
                                    rangeError = "Enter a valid date (yyyy-MM-dd)"
                                fromValue.isNotEmpty() && toValue.isNotEmpty() &&
                                    fromValue > toValue ->
                                    rangeError = "From date must be on or before To date."
                                else -> {
                                    val params = ActivityTexts.dateParams(fromValue, toValue)
                                    rangeError = null
                                    viewModel.applyDateRange(params?.dateFrom, params?.dateTo)
                                }
                            }
                        },
                        onSelectGroup = { viewModel.selectGroup(it) },
                        onGoToPage = { viewModel.goToPage(it) },
                        onRetryFeed = { viewModel.retryFeed() }
                    )
                }
            }
        }
    }
}

@Composable
private fun ActivityContent(
    groups: List<GroupDto>,
    selectedGroupId: String?,
    feedState: ActivityFeedState,
    from: String,
    to: String,
    rangeError: String?,
    onFromChange: (String) -> Unit,
    onToChange: (String) -> Unit,
    onApplyRange: () -> Unit,
    onSelectGroup: (String) -> Unit,
    onGoToPage: (Int) -> Unit,
    onRetryFeed: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        GroupPicker(
            groups = groups,
            selectedId = selectedGroupId,
            onSelect = onSelectGroup
        )
        DateFilter(
            from = from,
            to = to,
            rangeError = rangeError,
            onFromChange = onFromChange,
            onToChange = onToChange,
            onApply = onApplyRange
        )

        when (val f = feedState) {
            is ActivityFeedState.Idle -> FeedHint(
                text = "Pick a group to see its activity."
            )

            is ActivityFeedState.Loading -> Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }

            is ActivityFeedState.Error -> FeedHint(
                text = "Could not load activity: ${f.message}",
                isError = true,
                onRetry = onRetryFeed
            )

            is ActivityFeedState.Ready -> {
                FeedHeader(total = f.total)
                if (f.logs.isEmpty()) {
                    FeedHint(text = "No activity for this range.")
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(f.logs, key = { it.id ?: it.createdAt.orEmpty() }) { log ->
                            ActivityRow(log = log, members = f.members)
                        }
                    }
                }
                if (ActivityTexts.showPager(f.total)) {
                    ActivityPager(
                        page = f.page,
                        pageCount = ActivityTexts.pageCount(f.total),
                        onGoToPage = onGoToPage
                    )
                }
            }
        }
    }
}

/**
 * The group picker: the selected group's name over a dropdown of all the
 * user's groups (web `#activity-group-select`).
 */
@Composable
private fun GroupPicker(
    groups: List<GroupDto>,
    selectedId: String?,
    onSelect: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = groups.firstOrNull { it.id == selectedId }

    Box {
        ElevatedCard(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Group",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = selected?.name?.takeIf { it.isNotBlank() }
                            ?: "Select a group…",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                Icon(
                    imageVector = Icons.Filled.KeyboardArrowDown,
                    contentDescription = "Change group",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            groups.forEach { group ->
                DropdownMenuItem(
                    text = {
                        Text(group.name?.takeIf { it.isNotBlank() } ?: "Untitled group")
                    },
                    trailingIcon = {
                        if (group.id == selectedId) {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    },
                    onClick = {
                        expanded = false
                        group.id?.let { onSelect(it) }
                    }
                )
            }
        }
    }
}

/**
 * The date-range filter (web `#activity-from` / `#activity-to` + Apply).
 * [rangeError] is an inline field error from a failed validation.
 */
@Composable
private fun DateFilter(
    from: String,
    to: String,
    rangeError: String?,
    onFromChange: (String) -> Unit,
    onToChange: (String) -> Unit,
    onApply: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = from,
                onValueChange = onFromChange,
                label = { Text("From") },
                placeholder = { Text("yyyy-MM-dd") },
                singleLine = true,
                isError = rangeError != null,
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = to,
                onValueChange = onToChange,
                label = { Text("To") },
                placeholder = { Text("yyyy-MM-dd") },
                singleLine = true,
                isError = rangeError != null,
                modifier = Modifier.weight(1f)
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (rangeError != null) {
                Text(
                    text = rangeError,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            } else {
                Spacer(modifier = Modifier.width(1.dp))
            }
            OutlinedButton(onClick = onApply) {
                Text("Apply")
            }
        }
    }
}

/** Section heading with the web's "(N events)" count line. */
@Composable
private fun FeedHeader(total: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Activity",
            style = MaterialTheme.typography.titleMedium
        )
        val count = ActivityTexts.totalLabel(total)
        if (count.isNotEmpty()) {
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = count,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** A centred hint / empty / error line under the filter row. */
@Composable
private fun FeedHint(
    text: String,
    isError: Boolean = false,
    onRetry: (() -> Unit)? = null
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = if (isError) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
        if (onRetry != null) {
            Spacer(modifier = Modifier.height(12.dp))
            TextButton(onClick = onRetry) {
                Text("Try again")
            }
        }
    }
}

/**
 * One audit-log row: event pill + actor + timestamp, then the prettified
 * detail (the web table's Event / Actor / When / Details columns).
 */
@Composable
private fun ActivityRow(log: ActivityLogDto, members: List<GroupMemberDto>) {
    val resolver = remember(members) { ActivityTexts.memberNameResolver(members) }
    val event = (log.event ?: log.action)?.takeIf { it.isNotBlank() } ?: "?"

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                EventPill(event = event)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = ActivityTexts.actorName(log, resolver),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = Format.dateTime(log.createdAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = ActivityTexts.prettify(log, resolver),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

/**
 * The coloured event pill, mapping [ActivityTexts.eventPill] to a
 * Material3 container colour per category (web `event-pill--*` classes).
 */
@Composable
private fun EventPill(event: String) {
    val (background, content) = when (ActivityTexts.eventPill(event)) {
        "CREATE" -> MaterialTheme.colorScheme.primaryContainer to
            MaterialTheme.colorScheme.onPrimaryContainer
        "UPDATE" -> MaterialTheme.colorScheme.tertiaryContainer to
            MaterialTheme.colorScheme.onTertiaryContainer
        "DELETE" -> MaterialTheme.colorScheme.errorContainer to
            MaterialTheme.colorScheme.onErrorContainer
        "LOCK", "UNLOCK" -> MaterialTheme.colorScheme.secondaryContainer to
            MaterialTheme.colorScheme.onSecondaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant to
            MaterialTheme.colorScheme.onSurfaceVariant
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(background)
            .padding(horizontal = 8.dp, vertical = 2.dp)
    ) {
        Text(
            text = event.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = content
        )
    }
}

/**
 * The web's pager: Prev / "Page x of y" / Next (hidden when one page
 * suffices - the caller checks [ActivityTexts.showPager]).
 */
@Composable
private fun ActivityPager(
    page: Int,
    pageCount: Int,
    onGoToPage: (Int) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextButton(onClick = { onGoToPage(-1) }, enabled = page > 1) {
            Text("Prev")
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "Page $page of $pageCount",
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(modifier = Modifier.width(8.dp))
        TextButton(onClick = { onGoToPage(1) }, enabled = page < pageCount) {
            Text("Next")
        }
    }
}
