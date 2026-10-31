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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kharcha.tracker.data.local.ThemeMode
import com.kharcha.tracker.data.local.ThemePreferenceStore
import com.kharcha.tracker.data.remote.dto.NotificationDto
import com.kharcha.tracker.data.remote.dto.SubscriptionStatusDto
import com.kharcha.tracker.ui.dashboard.ActivationState
import com.kharcha.tracker.ui.dashboard.MarkReadState
import com.kharcha.tracker.ui.dashboard.ProfileSaveState
import com.kharcha.tracker.ui.dashboard.SettingsTexts
import com.kharcha.tracker.ui.dashboard.SettingsUiState
import com.kharcha.tracker.ui.dashboard.SettingsViewModel
import kotlinx.coroutines.launch

/**
 * Stage 7b settings screen: profile editing (name + avatar URL, email
 * read-only), the subscription banner + plan catalog with mock activation,
 * the notification list (mark-all-read), the theme preference and sign-out.
 *
 * The plan strings are the web UI's verbatim (see [SettingsTexts]); the
 * theme chips write to [ThemePreferenceStore], which
 * [com.kharcha.tracker.MainActivity] consumes app-wide. [onSignOut] is
 * [com.kharcha.tracker.ui.auth.AuthViewModel.signOut], supplied by
 * [AppNavHost].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    onSignOut: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val profileState by viewModel.profileState.collectAsState()
    val activationState by viewModel.activationState.collectAsState()
    val markReadState by viewModel.markReadState.collectAsState()

    var planTarget by remember { mutableStateOf<SettingsTexts.PlanOption?>(null) }
    var showSignOutDialog by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }

    // Profile save: snackbar the outcome, then reset so a later attempt
    // re-fires this effect.
    LaunchedEffect(profileState) {
        val message = when (val s = profileState) {
            is ProfileSaveState.Saved -> "Profile saved"
            is ProfileSaveState.Failed -> s.message
            else -> return@LaunchedEffect
        }
        val success = profileState is ProfileSaveState.Saved
        snackbarHostState.showSnackbar(
            message,
            duration = if (success) SnackbarDuration.Short else SnackbarDuration.Long
        )
        viewModel.resetProfileState()
    }

    // Plan activation: success closes the dialog + snackbars; a failure
    // stays inline in the open dialog (reset on dismiss).
    LaunchedEffect(activationState) {
        if (activationState !is ActivationState.Activated) return@LaunchedEffect
        planTarget = null
        snackbarHostState.showSnackbar("Plan activated", duration = SnackbarDuration.Short)
        viewModel.resetActivationState()
    }

    // Mark all read: snackbar the count, then reset.
    LaunchedEffect(markReadState) {
        val message = when (val s = markReadState) {
            is MarkReadState.Done -> "Marked ${s.count} notification" +
                if (s.count == 1) "" else "s" + " as read"
            is MarkReadState.Failed -> s.message
            else -> return@LaunchedEffect
        }
        val success = markReadState is MarkReadState.Done
        snackbarHostState.showSnackbar(
            message,
            duration = if (success) SnackbarDuration.Short else SnackbarDuration.Long
        )
        viewModel.resetMarkReadState()
    }

    // Re-fetch (silently) when the screen is (re-)entered. The VM guards the
    // initial entry as a no-op.
    ReloadOnResume { viewModel.refresh() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
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
                is SettingsUiState.Loading -> ScreenLoading()

                is SettingsUiState.Error -> ScreenError(
                    message = s.message,
                    onRetry = { viewModel.load() }
                )

                is SettingsUiState.Ready -> SettingsContent(
                    profileName = s.profile.name.orEmpty(),
                    profileAvatar = s.profile.avatarUrl.orEmpty(),
                    profileEmail = s.profile.email.orEmpty(),
                    subscription = s.subscription,
                    notifications = s.notifications,
                    profileBusy = profileState is ProfileSaveState.Busy,
                    markReadBusy = markReadState is MarkReadState.Busy,
                    onSaveProfile = { name, avatar -> viewModel.saveProfile(name, avatar) },
                    onChoosePlan = { option -> planTarget = option },
                    onMarkAllRead = { viewModel.markAllNotificationsRead() },
                    onSignOut = { showSignOutDialog = true }
                )
            }
        }
    }

    planTarget?.let { plan ->
        PlanConfirmDialog(
            plan = plan,
            busy = activationState is ActivationState.Busy,
            error = (activationState as? ActivationState.Failed)?.message,
            onConfirm = { viewModel.activatePlan(plan.plan) },
            onDismiss = {
                viewModel.resetActivationState()
                planTarget = null
            }
        )
    }

    if (showSignOutDialog) {
        SignOutDialog(
            onConfirm = {
                showSignOutDialog = false
                onSignOut()
            },
            onDismiss = { showSignOutDialog = false }
        )
    }
}

@Composable
private fun SettingsContent(
    profileName: String,
    profileAvatar: String,
    profileEmail: String,
    subscription: SubscriptionStatusDto?,
    notifications: List<NotificationDto>,
    profileBusy: Boolean,
    markReadBusy: Boolean,
    onSaveProfile: (name: String, avatarUrl: String?) -> Unit,
    onChoosePlan: (SettingsTexts.PlanOption) -> Unit,
    onMarkAllRead: () -> Unit,
    onSignOut: () -> Unit
) {
    var name by remember { mutableStateOf(profileName) }
    var avatar by remember { mutableStateOf(profileAvatar) }
    var nameError by remember { mutableStateOf<String?>(null) }

    // Re-seed the fields whenever the profile's identity changes (a new
    // session); saves keep the same id, so in-flight edits are not clobbered.
    LaunchedEffect(profileName, profileAvatar) {
        name = profileName
        avatar = profileAvatar
        nameError = null
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item(key = "profile") {
            ProfileCard(
                name = name,
                avatar = avatar,
                email = profileEmail,
                nameError = nameError,
                busy = profileBusy,
                onNameChange = {
                    name = it
                    if (nameError != null) nameError = null
                },
                onAvatarChange = { avatar = it },
                onSave = {
                    val trimmed = name.trim()
                    if (trimmed.isEmpty()) {
                        nameError = "Enter a name"
                    } else {
                        onSaveProfile(trimmed, avatar.trim().ifBlank { null })
                    }
                }
            )
        }

        if (subscription != null) {
            item(key = "subscription") {
                SubscriptionSection(
                    subscription = subscription,
                    onChoosePlan = onChoosePlan
                )
            }
        }

        item(key = "notifications-heading") {
            Text(
                text = "Notifications",
                style = MaterialTheme.typography.titleMedium
            )
        }
        if (notifications.isEmpty()) {
            item(key = "notifications-empty") {
                Text(
                    text = "No notifications yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            items(notifications, key = { it.id ?: it.createdAt.orEmpty() }) { note ->
                NotificationRow(note = note)
            }
            val unread = notifications.count { !it.isRead }
            if (unread > 0) {
                item(key = "mark-read") {
                    Button(
                        onClick = onMarkAllRead,
                        enabled = !markReadBusy,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (markReadBusy) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text(if (markReadBusy) "Marking…" else "Mark all as read")
                    }
                }
            }
        }

        item(key = "appearance") {
            AppearanceCard()
        }

        item(key = "session") {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Session",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "Logging out clears your session on this device.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(
                        onClick = onSignOut,
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        )
                    ) {
                        Text("Log out")
                    }
                }
            }
        }
    }
}

/**
 * The profile card: name + avatar URL editable, email read-only, with the
 * Save action. [nameError] is the inline "Enter a name" validation.
 */
@Composable
private fun ProfileCard(
    name: String,
    avatar: String,
    email: String,
    nameError: String?,
    busy: Boolean,
    onNameChange: (String) -> Unit,
    onAvatarChange: (String) -> Unit,
    onSave: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Profile",
                style = MaterialTheme.typography.titleMedium
            )
            OutlinedTextField(
                value = name,
                onValueChange = onNameChange,
                label = { Text("Name") },
                singleLine = true,
                isError = nameError != null,
                supportingText = {
                    if (nameError != null) Text(nameError!!)
                },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = avatar,
                onValueChange = onAvatarChange,
                label = { Text("Avatar URL") },
                supportingText = { Text("Optional - a public image URL") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = email,
                onValueChange = {},
                label = { Text("Email") },
                readOnly = true,
                supportingText = { Text("Email can't be changed") },
                modifier = Modifier.fillMaxWidth()
            )
            Button(onClick = onSave, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                if (busy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(if (busy) "Saving…" else "Save")
            }
        }
    }
}

/**
 * The subscription banner (coloured by active / expired / trial) and the
 * plan catalog with mock activation (web `#view-subscription`).
 */
@Composable
private fun SubscriptionSection(
    subscription: SubscriptionStatusDto,
    onChoosePlan: (SettingsTexts.PlanOption) -> Unit
) {
    val (bannerColor, bannerContent) = when {
        subscription.planStatus == "active" ->
            MaterialTheme.colorScheme.primaryContainer to
                MaterialTheme.colorScheme.onPrimaryContainer
        subscription.isExpired ->
            MaterialTheme.colorScheme.errorContainer to
                MaterialTheme.colorScheme.onErrorContainer
        else ->
            MaterialTheme.colorScheme.secondaryContainer to
                MaterialTheme.colorScheme.onSecondaryContainer
    }
    val active = SettingsTexts.activePlan(subscription)

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(bannerColor)
                .padding(12.dp)
        ) {
            Text(
                text = SettingsTexts.subscriptionBanner(subscription),
                style = MaterialTheme.typography.bodyMedium,
                color = bannerContent
            )
        }

        SettingsTexts.PLANS.forEach { plan ->
            PlanCard(
                plan = plan,
                isCurrent = active == plan.plan,
                onChoose = { onChoosePlan(plan) }
            )
        }

        Text(
            text = SettingsTexts.PAYMENTS_FINE_PRINT,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * One plan card from the catalog (web `plan-card`; the featured one carries
 * the "Best value" badge). [isCurrent] tags the active plan.
 */
@Composable
private fun PlanCard(
    plan: SettingsTexts.PlanOption,
    isCurrent: Boolean,
    onChoose: () -> Unit
) {
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
                Text(
                    text = plan.name,
                    style = MaterialTheme.typography.titleMedium
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (plan.featured) {
                        PlanBadge(text = "Best value", featured = true)
                    }
                    if (isCurrent) {
                        PlanBadge(text = "Current plan", featured = false)
                    }
                }
            }
            Text(
                text = "${plan.price} ${plan.period}",
                style = MaterialTheme.typography.titleSmall
            )
            plan.features.forEach { feature ->
                Text(
                    text = "• $feature",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Button(onClick = onChoose) {
                Text("Choose ${plan.name}")
            }
        }
    }
}

/** The small "Best value" / "Current plan" tag on a plan card. */
@Composable
private fun PlanBadge(text: String, featured: Boolean) {
    val (background, content) = if (featured) {
        MaterialTheme.colorScheme.primaryContainer to
            MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.tertiaryContainer to
            MaterialTheme.colorScheme.onTertiaryContainer
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(background)
            .padding(horizontal = 8.dp, vertical = 2.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = content
        )
    }
}

/**
 * One notification row: the text ([SettingsTexts.notificationText]) plus the
 * date; unread rows are bold with a dot (web `notif-item--unread`).
 */
@Composable
private fun NotificationRow(note: NotificationDto) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(
                        if (note.isRead) {
                            androidx.compose.ui.graphics.Color.Transparent
                        } else {
                            MaterialTheme.colorScheme.primary
                        }
                    )
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = SettingsTexts.notificationText(note),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (note.isRead) FontWeight.Normal else FontWeight.SemiBold
                )
                Text(
                    text = SettingsTexts.formatDate(note.createdAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * The theme preference (System / Light / Dark). Writes to
 * [ThemePreferenceStore]; [com.kharcha.tracker.MainActivity] reacts live.
 */
@Composable
private fun AppearanceCard() {
    val context = LocalContext.current
    val themeStore = remember(context) { ThemePreferenceStore(context) }
    val current by themeStore.theme.collectAsState(initial = ThemeMode.SYSTEM)
    val scope = rememberCoroutineScope()

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Appearance",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = "Follow the device setting, or force light or dark.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ThemeMode.entries.forEach { mode ->
                    FilterChip(
                        selected = current == mode,
                        onClick = { scope.launch { themeStore.setTheme(mode) } },
                        label = {
                            Text(
                                when (mode) {
                                    ThemeMode.SYSTEM -> "System"
                                    ThemeMode.LIGHT -> "Light"
                                    ThemeMode.DARK -> "Dark"
                                }
                            )
                        }
                    )
                }
            }
        }
    }
}

/**
 * The "Choose <plan>" confirm dialog (mock payment). [error] is the
 * server's 422 message shown inline; the dialog stays open until dismissed
 * (the [com.kharcha.tracker.GroupDetailScreen] leave-group pattern).
 */
@Composable
private fun PlanConfirmDialog(
    plan: SettingsTexts.PlanOption,
    busy: Boolean,
    error: String?,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("Choose ${plan.name}?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "${plan.price} ${plan.period}. " +
                        SettingsTexts.PAYMENTS_FINE_PRINT,
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
                Text(if (busy) "Activating…" else "Choose ${plan.name}")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !busy) {
                Text("Cancel")
            }
        }
    )
}

/** The sign-out confirm dialog (web "Session" card text). */
@Composable
private fun SignOutDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Log out?") },
        text = {
            Text(
                text = "Logging out clears your session on this device.",
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Log out")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
