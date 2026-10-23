# Android Frontend App Proposal - Kharcha Tracker

## Executive Summary

This proposal outlines the design and implementation plan for a native Android application for the Kharcha Tracker system. The app will provide a mobile-first experience with offline support, real-time sync, and material design UI, built using Kotlin, Jetpack Compose, and KMP (Kotlin Multiplatform) for cross-platform sharing of business logic and data models.

**Technology Stack:**
- **UI Framework**: Jetpack Compose (Material 3)
- **Architecture**: Clean Architecture / MVVM (Multiplatform)
- **Language**: Kotlin (Kotlin Multiplatform for shared modules)
- **Networking**: Ktor Client (multiplatform) or Retrofit (Android-only)
- **Database**: SQLite (local cache) + KSP for database access
- **State Management**: Kotlin Flow + ViewModel with sealed states
- **Dependency Injection**: Koin
- **Charts**: Compose M3 Charts or MPAndroidChart (via Compose UI binding)
- **Icons**: Material Icons (Material Design 3)
- **Build Tool**: Gradle (Kotlin DSL)

---

## 1. Architecture Overview

### 1.1 Architecture Layers

```
┌─────────────────────────────────────────────────────────┐
│                    Presentation Layer                   │
│  ┌─────────────┐ ┌─────────────┐ ┌─────────────┐      │
│  │    Home     │ │   Groups    │ │   Expenses   │      │
│  │   Screen    │ │    Screen   │ │   Screen    │      │
│  └─────────────┘ └─────────────┘ └─────────────┘      │
│  ┌─────────────┐ ┌─────────────┐ ┌─────────────┐      │
│  │  Settings   │ │  Subscription│ │ Activity    │      │
│  │   Screen    │ │   Screen    │ │   Screen    │      │
│  └─────────────┘ └─────────────┘ └─────────────┘      │
└─────────────────────────────────────────────────────────┘
                        ▼
┌─────────────────────────────────────────────────────────┐
│                   View Model Layer                      │
│  ┌─────────────┐ ┌─────────────┐ ┌─────────────┐      │
│  │   HomeVM    │ │  GroupsVM   │ │ ExpensesVM  │      │
│  └─────────────┘ └─────────────┘ └─────────────┘      │
│  ┌─────────────┐ ┌─────────────┐ ┌─────────────┐      │
│  │ SettingsVM  │ │ SubVM       │ │ ActivityVM  │      │
│  └─────────────┘ └─────────────┘ └─────────────┘      │
└─────────────────────────────────────────────────────────┘
                        ▼
┌─────────────────────────────────────────────────────────┐
│                   Domain Layer (KMP Shared)             │
│  ┌─────────────┐ ┌─────────────┐ ┌─────────────┐      │
│  │  Repository │ │  Repository │ │  Repository  │      │
│  │  Auth       │ │  Group      │ │  Expense     │      │
│  └─────────────┘ └─────────────┘ └─────────────┘      │
│  ┌─────────────┐ ┌─────────────┐ ┌─────────────┐      │
│  │  NotifRepo  │ │  SubRepo    │ │  Activity   │      │
│  └─────────────┘ └─────────────┘ └─────────────┘      │
└─────────────────────────────────────────────────────────┘
                        ▼
┌─────────────────────────────────────────────────────────┐
│                   Data Layer                            │
│  ┌─────────────┐ ┌─────────────┐ ┌─────────────┐      │
│  │  API Client │ │   DB Repo   │ │   Network   │      │
│  │ (Ktor)      │ │  (SQLite)   │ │  Request    │      │
│  └─────────────┘ └─────────────┘ └─────────────┘      │
└─────────────────────────────────────────────────────────┘
```

### 1.2 Navigation Architecture

```
MaterialDrawerLayout (Desktop/Tablet)
├── AppBar (top bar with logo, notifications badge)
│
├── Drawer (Sidebar) ──────┐
│   ├─ Dashboard
│   ├─ Groups            │
│   ├─ Expenses          │
│   ├─ Activity          │
│   ├─ Settings          │
│   └─ Subscription      │
│                         │
│                         ▼
└── TabBar (Mobile/Tablet Bottom)
    ├─ Dashboard
    ├─ Groups
    ├─ Expenses
    ├─ Activity
    ├─ Settings
    └─ Subscription
```

**Desktop (>1024px):** Side drawer + Top bar
**Tablet (640px-1024px):** Bottom tab bar, no drawer, top bar
**Mobile (<640px):** Bottom tab bar only

---

## 2. App Structure

```
kharcha-tracker-android/
├─ app/
│   ├─ src/main/
│   │   ├─ AndroidManifest.xml
│   │   ├─ res/
│   │   │   ├─ mipmap-mdpi/
│   │   │   ├─ mipmap-hdpi/
│   │   │   ├─ mipmap-xhdpi/
│   │   │   ├─ mipmap-xxhdpi/
│   │   │   └─ values/
│   │   │       ├─ strings.xml
│   │   │       ├─ colors.xml
│   │   │       ├─ dimens.xml
│   │   │       ├─ themes.xml
│   │   │       └─ ic_launcher.xml
│   │   ├─ kotlin/
│   │       │   └─ com/
│   │       │       └─ kharcha/
│   │       │           └─ tracker/
│   │       │               ├─ MainActivity.kt
│   │       │               └─ di/          # Koin modules
│   │       │                   └─ AppModule.kt
│   │       └─ core/                         # Shared core (KMP)
│   │           ├─ data/
│   │           │   ├─ local/                # DB operations
│   │           │   └─ remote/               # API client
│   │           ├─ domain/                   # Use cases
│   │           └─ model/                   # Shared data models
│   │   └─ composeApp/                       # Android-specific UI
│   │       ├─ src/main/
│   │       │   └─ kotlin/com/kharcha/
│   │       │       ├─ theme/
│   │       │       ├─ navigation/
│   │       │       ├─ screens/
│   │       │       ├─ viewmodels/
│   │       │       ├─ repositories/
│   │       │       ├─ utils/
│   │       │       └─ di/
│   │       └─ build.gradle.kts
│   └─ build.gradle.kts
├─ backend/  # Shared KMP backend business logic
│   ├─ src/commonMain/kotlin/com/kharcha/
│   │   ├─ auth_service/
│   │   │   ├─ models/
│   │   │   └─ auth_service.kt
│   │   ├─ group_service/
│   │   │   └─ group_service.kt
│   │   ├─ expense_service/
│   │   │   └─ expense_service.kt
│   │   ├─ notification_service/
│   │   │   └─ notification_service.kt
│   │   ├─ subscription_service/
│   │   │   └─ subscription_service.kt
│   │   ├─ invitation_service/
│   │   │   └─ invitation_service.kt
│   │   ├─ settlement_engine/
│   │   │   ├─ settlement_engine.kt
│   │   │   └─ matrix_row_key.kt
│   │   └─ storage/
│   │       ├─ schema/
│   │       └─ flat_file.kt
│   └─ build.gradle.kts
├─ backend-desktop/  # Desktop UI (Kotlin Desktop)
│   └─ build.gradle.kts
├─ shared-data/      # Pure data models shared between mobile/desktop
│   └─ build.gradle.kts
├─ kharcha-tracker.yaml  # API blueprint (copied from web)
└─ scripts/          # Gradle wrappers, CI scripts
```

---

## 3. Data Models (Kotlin Multiplatform)

These models are shared across mobile, desktop, and web (via serialization). Based on the YAML blueprint and implemented in the `backend` KMP module.

### 3.1 Core Models

```kotlin
// User and Authentication
data class User(
    val id: String,
    val email: String,
    val password_hash: String,
    val name: String,
    val avatar_url: String?,
    val trial_ends_at: Instant?,
    val plan: String,
    val plan_status: String,
    val plan_ends_at: Instant?,
    val limited_entries: Int,
    val is_active: Boolean,
    val created_at: Instant,
    val updated_at: Instant
)

data class AuthTokens(
    val access_token: String,
    val refresh_token: String,
    val token_type: String,
    val expires_in: Int
)

sealed class AuthState {
    object NotAuthenticated : AuthState()
    object Authenticating : AuthState()
    data class LoggedIn(val user: User, val tokens: AuthTokens) : AuthState()
    object SessionExpired : AuthState()
    data class Error(val code: String, val message: String) : AuthState()
}
```

### 3.2 Group Models

```kotlin
enum class GroupStatus {
    OPEN,
    LOCKED,
    SETTLED,
    UNLOCKED,
    PARTIAL_SETTLEMENT
}

data class Group(
    val id: String,
    val name: String,
    val currency: String,
    val status: GroupStatus,
    val invite_code: String,
    val invite_status: String,  // pending, accepted, rejected, expired
    val created_at: Instant,
    val updated_at: Instant
)

sealed class GroupStatusState {
    data class Active(val status: String) : GroupStatusState()
    data class Expired(val status: String, val expiryDate: Instant) : GroupStatusState()
    data class Limited(val status: String, val remaining: Int) : GroupStatusState()
}
```

### 3.3 Expense Models

```kotlin
enum class ExpenseCategory {
    DAILY,
    MONTHLY,
    FOOD,
    TRAVEL,
    VEHICLE,
    MEDICAL,
    SHOPPING    // Other categories as per API
}

data class Expense(
    val trans_id: String,
    val expn_desc: String,
    val expn_ctgry: ExpenseCategory,
    val date_time: Instant,
    val amount: Double,
    val status: String,  // Open, Paid, Settled, Deleted
    val paid_by: List<ExpensePayment>?,
    val split_mode: String,  // EQUAL, PERCENTAGE
    val expn_splt: Map<String, Double>,
    val group_id: String,
    val is_deleted: Boolean,
    val created_at: Instant,
    val updated_at: Instant,
    val created_by: String,
    val modified_by: String?
)

data class ExpensePayment(
    val member_id: String,
    val paid_amount: Double
)

data class ExpenseUpdate(
    val expn_desc: String? = null,
    val expn_ctgry: String? = null,
    val date_time: String? = null,
    val amount: Double? = null,
    val split_mode: String? = null,
    val expn_splt: Map<String, Double>? = null,
    val paid_by: List<ExpensePayment>? = null
)
```

### 3.4 Notification Models

```kotlin
enum class NotificationKind {
    GROUP_CREATED,
    GROUP_DELETED,
    GROUP_LOCKED,
    GROUP_UNLOCKED,
    MEMBER_ADDED,
    MEMBER_REMOVED,
    GROUP_INVITE_ACCEPTED,
    GROUP_INVITE_DECLINED,
    EXPENSE_ADDED,
    PAYOUT_REMINDER,
    PAYOUT_ESCALATED,
    SUBSCRIPTION_RENEWED,
    SUBSCRIPTION_EXPIRED
}

data class Notification(
    val id: String,
    val kind: NotificationKind,
    val user_id: String,
    val group_id: String?,
    val note: String,
    val is_read: Boolean,
    val created_at: Instant,
    val expires_at: Instant? = null
)
```

### 3.5 Settlement Models

```kotlin
data class MatrixRow(
    val expense_id: String,
    val payout_id: String?,
    val amount: Long,
    val share: Long
)

data class MatrixRowKey(
    val group_id: String,
    val expense_id: String?,
    val payout_id: String?
)

data class Payout(
    val id: String,
    val group_id: String,
    val payor_id: String,
    val payee_id: String,
    val amount: Long,
    val status: String,  // PENDING, PAID, SETTLED, FAILED, CANCELLED
    val paid_at: Instant?,
    val replaced_by: String?,
    val reason: String?,
    val created_at: Instant,
    val updated_at: Instant
)

data class SettlementRound(
    val id: String,
    val group_id: String,
    val status: String,  // PENDING, PAID, PARTIAL_SETTLEMENT, SETTLED, FAILED
    val created_at: Instant,
    val updated_at: Instant
)
```

---

## 4. UI Design (Jetpack Compose Mobile-First)

### 4.1 Design Principles

1. **Mobile-First**: All layouts designed for mobile first, responsive to tablet/desktop
2. **Material Design 3**: Use Material You theming with dynamic colors
3. **Bottom Navigation**: Primary navigation via bottom bar on mobile/tablet
4. **Side Drawer**: Additional navigation on desktop (hidden on mobile/tablet)
5. **Offline Support**: Cache data locally with SQLite for offline access
6. **Real-Time Sync**: Sync changes when online

### 4.2 App Shell Components

```kotlin
// Composable: AppShell (Top Bar + Bottom Bar)
@Composable
@Suppress("LEAKED_COMPOSABLE")
internal fun AppShell(
    currentRoute: String,
    onNavigationClick: (String) -> Unit,
    onNotificationClick: () -> Unit,
    notifications: List<Notification>,
    currentNotificationsCount: Int,
    subscriptionPlan: String,
    subscriptionEndsAt: Instant?
) {
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Top Bar (always visible)
        TopAppBar(
            title = RouteTitles[currentRoute] ?: "Kharcha Tracker",
            actions = {
                Row(
                    verticalAlignment = VerticalAlignment.CenterVertically
                ) {
                    // Subscription Status
                    SubscriptionStatusChip(
                        plan = subscriptionPlan,
                        endsAt = subscriptionEndsAt
                    )
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    // Notifications
                    NotificationsBadge(
                        count = currentNotificationsCount,
                        onNotificationClick = onNotificationClick
                    )
                }
            }
        )
        
        // Content Area
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(ColorSurface.surface)
        ) {
            LazyColumn(
                content = { items(count) { Item } }
            )
        }
        
        // Bottom Tab Bar (mobile/tablet only, hidden on desktop)
        TabRow(
            selectedTabIndex = 0,
            containerColor = ColorSurface.primary
        ) {
            Tab(
                selected = false,
                text = Text("Dashboard"),
                icon = { Icon(Icons.Strings.Dashboard, contentDescription = null) }
            )
            Tab(
                selected = false,
                text = Text("Groups"),
                icon = { Icon(Icons.Strings.People, contentDescription = null) }
            )
            Tab(
                selected = false,
                text = Text("Expenses"),
                icon = { Icon(Icons.Strings.AccountBalance, contentDescription = null) }
            )
            Tab(
                selected = false,
                text = Text("Activity"),
                icon = { Icon(Icons.Strings.Event, contentDescription = null) }
            )
            Tab(
                selected = false,
                text = Text("Settings"),
                icon = { Icon(Icons.Strings.Settings, contentDescription = null) }
            )
            Tab(
                selected = false,
                text = Text("Subscription"),
                icon = { Icon(Icons.Strings.Payments, contentDescription = null) }
            }
        }
    }
}
```

### 4.3 Login/Signup Screens

```kotlin
// Composable: LoginScreen
@Composable
fun LoginScreen(
    onSuccess: (LoginRequest) -> Unit,
    onSignupClick: () -> Unit,
    onMagicLinkClick: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Login") },
                navigationIcon = {
                    IconButton(onClick = onMagicLinkClick) {
                        Icon(Icons.Strings.Mail, contentDescription = "Magic link")
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .background(MaterialTheme.colorScheme.surface)) {
            
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp)
            ) {
                // Logo
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .align(Alignment.CenterHorizontally)
                        .background(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(24.dp)
                        )
                        .padding(16.dp)
                ) {
                    Text(
                        text = "Kharcha",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                
                Spacer(modifier = Modifier.height(32.dp))
                
                // Email field
                OutlinedTextField(
                    value = "",
                    onValueChange = {},
                    label = { Text("Email") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                
                // Password field
                OutlinedTextField(
                    value = "",
                    onValueChange = {},
                    label = { Text("Password") },
                    modifier = Modifier.fillMaxWidth(),
                    isPassword = true
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Login Button
                Button(
                    onClick = { 
                        if (emailTextField.value.isNotEmpty() && 
                            passwordTextField.value.isNotEmpty()) {
                            onSuccess(LoginRequest(
                                email = emailTextField.value,
                                password = passwordTextField.value
                            ))
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                ) {
                    Text("Login")
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Divider
                HorizontalDivider()
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Magic Link Button
                OutlinedButton(
                    onClick = onMagicLinkClick,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Continue with Email")
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Sign Up link
                Text(
                    text = "Don't have an account?",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Button(
                    onClick = onSignupClick,
                    modifier = Modifier.fillMaxWidth(),
                    content = { Text("Sign Up") }
                )
            }
        }
    }
}

// Composable: SignupScreen
@Composable
fun SignupScreen(
    onSuccess: (User) -> Unit,
    onLoginClick: () -> Unit
) {
    // Similar to LoginScreen but creates new user
    // Auto-login after signup
}
```

### 4.4 Dashboard Screen

```kotlin
// Composable: DashboardScreen
@Composable
fun DashboardScreen(
    user: User,
    groups: List<Group>,
    onGroupClick: (String) -> Unit,
    onGroupSettingsClick: (String) -> Unit,
    onAddGroupClick: () -> Unit,
    plan: Plan,
    trialEndsAt: Instant?,
    entriesRemaining: Int?,
    inviteCount: Int,
    onInviteClick: () -> Unit,
    expenses: List<Expense>
) {
    // Statistics Row
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        StatCard(
            title = "Groups",
            value = String(groups.size),
            icon = Icons.People,
            color = MaterialTheme.colorScheme.primary,
            onClick = { onGroupClick("") }
        )
        StatCard(
            title = "Expenses",
            value = String(expenses.size),
            icon = Icons.AccountBalance,
            color = MaterialTheme.colorScheme.secondary,
            onClick = { onGroupSettingsClick("") }
        )
        StatCard(
            title = "Invites",
            value = String(inviteCount),
            icon = Icons.Mail,
            color = MaterialTheme.colorScheme.tertiary,
            onClick = onInviteClick
        )
    }
    
    // Expense Summary Cards
    ExpenseSummaryCards(expenses)
    
    // Spending Trends Chart
    SpendingTrendsChart(expenses)
    
    // Recently Added Expenses
    RecentlyAddedExpenses(expenses)
}
```

### 4.5 Groups Screen

```kotlin
// Composable: GroupsScreen
@Composable
fun GroupsScreen(
    onAddGroupClick: () -> Unit,
    onJoinGroupClick: () -> Unit,
    onCreateGroupClick: () -> Unit,
    onInvitationClick: () -> Unit,
    groups: List<Group>,
    membersCache: Map<String, MemberInfo>
) {
    // Floating Action Button for new group
    FAB(
        onClick = onAddGroupClick,
        content = { Icon(Icons.Plus, contentDescription = "Add group") }
    )
    
    // Groups List
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 16.dp)
    ) {
        items(groups) { group ->
            GroupCard(
                group = group,
                onSettingsClick = { onGroupSettingsClick(group.id) },
                onEditClick = { onEditGroupClick(group.id) },
                onDeleteClick = { onDeleteGroupClick(group.id) },
                onAddMemberClick = { onAddMemberClick(group.id) },
                onExpensesClick = { onExpensesClick(group.id) },
                onSettleClick = { onSettleClick(group.id) },
                onActivityClick = { onActivityClick(group.id) },
                membersCache = membersCache
            )
        }
    }
}

// Composable: GroupCard
@Composable
fun GroupCard(
    group: Group,
    onSettingsClick: () -> Unit,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onAddMemberClick: () -> Unit,
    onExpensesClick: () -> Unit,
    onSettleClick: () -> Unit,
    onActivityClick: () -> Unit,
    membersCache: Map<String, MemberInfo>
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                Column(modifier = Modifier.weight(3f)) {
                    Text(
                        text = group.name,
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = group.currency,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                Spacer(modifier = Modifier.width(8.dp))
                
                // Status chip
                if (group.status != GroupStatus.Settled) {
                    StatusChip(
                        status = group.status
                    )
                }
                
                Spacer(modifier = Modifier.width(8.dp))
            }
            
            // Member count
            Text(
                text = "Members: ${group.members.size}",
                style = MaterialTheme.typography.bodySmall
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Members list
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 100.dp)
            ) {
                items(group.members.take(6)) { member ->
                    MemberChip(member, isOwner = member.id == currentUser.id)
                }
                
                if (group.members.size > 6) {
                    item {
                        Text(
                            text = "+${group.members.size - 6} more",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Expense section
            if (group.expenses.isNotEmpty()) {
                Text(
                    text = "Expenses:",
                    style = MaterialTheme.typography.titleSmall
                )
                
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    content = { items(group.expenses.take(5)) { expense ->
                        ExpenseListItem(expense)
                    }}
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Actions
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            ) {
                ActionButton(
                    text = "Add Member",
                    icon = Icons.PersonAdd,
                    onClick = onAddMemberClick
                )
                
                Spacer(modifier = Modifier.width(8.dp))
                
                ActionButton(
                    text = "Settle Up",
                    icon = Icons.AccountBalance,
                    onClick = onSettleClick
                )
                
                Spacer(modifier = Modifier.width(8.dp))
                
                ActionButton(
                    text = "Activity",
                    icon = Icons.Event,
                    onClick = onActivityClick
                )
                
                Spacer(modifier = Modifier.width(8.dp))
                
                // Copy invite code
                ActionButton(
                    text = group.invite_code,
                    icon = Icons.Link,
                    onClick = onCopyClick
                )
            }
        }
    }
}
```

### 4.6 Other Screens

Similar composable patterns for:
- **ExpensesScreen**: List and add/edit expenses
- **ActivityScreen**: Paginated activity log
- **SubscriptionScreen**: Plan selection and payment
- **SettingsScreen**: User settings and preferences

---

## 5. Navigation

### 5.1 Navigation Routes

```kotlin
sealed class MainRoute {
    object Login : MainRoute()
    object Signup : MainRoute()
    data class Dashboard(val group: Group? = null) : MainRoute()
    data class Groups(val groupId: String? = null) : MainRoute()
    data class Settings(val tab: SettingsTab? = null) : MainRoute()
    data class Subscription(val tab: SubscriptionTab? = null) : MainRoute()
    data class Expenses(val groupId: String? = null) : MainRoute()
    data class Activity(val groupId: String? = null) : MainRoute()
}

sealed class SettingsTab {
    object Profile
    object Notifications
    object Subscription
    object Security
    object Theme
    object About
}

sealed class SubscriptionTab {
    object Plans
    object Checkout
    object History
}
```

### 5.2 Navigation Host

```kotlin
// Desktop: DrawerLayout + NavigationHost
// Mobile: NavigationRail + NavigationHost

class KharchaTrackerActivity : ComponentActivity() {
    private val navHost by lazy {
        NavigationHost(
            navController = navController,
            containerColor = MaterialTheme.colorScheme.surface,
            windowInsets = WindowInsets(0, 56, 0, 0) // Leave room for status bar
        ) {
            content = {
                // Content based on route
            }
        }
    }
}
```

---

## 6. API Client (Ktor Client)

### 6.1 Base API Client

```kotlin
// Ktor Client for multiplatform
class ApiClient(
    private val baseUrl: String = "http://127.0.0.1:8000",
    private val storage: Storage = StorageManager.instance
) {
    private val ktorClient = HttpClient(baseUrl) {
        install(CallLogging) { level = Level.DEBUG }
        install(Cio) { timeout = 30.seconds }
        install(HttpCache) { maxStale = 1.hours }
        install(Auth) { 
            bearer { 
                token = { getAuthHeader() }
            }
        }
        install(Compress)
    }
    
    suspend fun <T> apiCall(
        path: String,
        method: String = "GET",
        body: Any? = null,
        params: Map<String, String> = emptyMap()
    ): Result<T> {
        return try {
            val url = "$baseUrl/api/v1/$path"
            val request = when (method.uppercase()) {
                "GET" -> ktorClient.get(url) {
                    setHeaders(params)
                    load(url) { headers() }
                }
                "POST" -> ktorClient.post(url) {
                    setHeaders(params)
                    body = body
                    load(url) { headers() }
                }
                "PUT" -> ktorClient.put(url) { ... }
                "PATCH" -> ktorClient.patch(url) { ... }
                "DELETE" -> ktorClient.delete(url) { ... }
                else -> throw IllegalStateException("Unsupported method")
            }
            
            Result.Success(
                statusCode = request.status.value,
                headers = request.headers,
                body = tryParseResponse(request)
            )
        } catch (e: Exception) {
            Result.Error(
                code = when {
                    e is IOException -> "NETWORK_ERROR"
                    e is AuthError -> "AUTH_ERROR"
                    else -> "UNKNOWN_ERROR"
                },
                message = e.message ?: "Unknown error",
                original = e
            )
        }
    }
}
```

### 6.2 Authentication Management

```kotlin
// AuthManager - handles token management
class AuthManager(
    private val apiClient: ApiClient,
    private val tokenStore: TokenStore
) {
    val currentAuth: StateFlow<AuthState> = derivedSharedFlow { authState }
    
    suspend fun login(email: String, password: String): Result<Base> {
        return apiClient.post("auth/login", body = LoginRequest(email, password))
    }
    
    suspend fun signup(name: String, email: String, password: String): Result<User> {
        return apiClient.post("auth/signup", body = SignupRequest(name, email, password))
    }
    
    suspend fun refreshToken(): Result<Base> {
        return apiClient.post("auth/refresh", body = RefreshTokenRequest())
    }
    
    suspend fun requestMagicLink(email: String): Result<MagicLinkResponse> {
        return apiClient.post("auth/magic-link", body = MagicLinkRequest(email))
    }
    
    suspend fun verifyMagicLink(token: String): Result<Base> {
        return apiClient.post("auth/magic-link/verify", body = MagicLinkVerifyRequest(token))
    }
    
    suspend fun logout() {
        tokenStore.clear()
        refreshTokens(0) // Trigger re-authentication
    }
    
    private fun apiFetch(path: String, method: String, body: Any?): 
            Call<T> = when (method) {
                "GET" -> ktorClient.get("$baseUrl/api/v1/$path")
                "POST" -> ktorClient.post("$baseUrl/api/v1/$path") { body = body }
                // etc.
            }
}

// State machine for authentication
sealed class AuthState {
    object Unauthenticated : AuthState()
    object Authenticating : AuthState()
    object LoggedIn : AuthState()
    object Expired : AuthState()
    data class ProcessingRefresh(val promise: Deferred<RefreshResult>) : AuthState()
    data class Refreshing val result: RefreshResult ) : AuthState()
    data class Error(val code: String, val message: String) : AuthState()
    
    val isLogged: Boolean = when(this) {
        is LoggedIn -> true
        else -> false
    }
    
    val isExpired: Boolean = when(this) {
        is Expired -> true
        else -> false
    }
}
```

---

## 7. State Management

### 7.1 SharedState (KMP)

```kotlin
// SharedState class for managing global app state
data class SharedState(
    val authState: AuthState = AuthState.Unauthenticated,
    val groups: List<GroupRecord> = emptyList(),
    val currentUser: UserRecord? = null,
    val selectedGroupId: String? = null,
    val currentExpense: ExpenseRecord? = null,
    val notifications: List<NotificationRecord> = emptyList(),
    val subscription: SubscriptionStatus = SubscriptionStatus.Unknown,
    val activity: List<AuditLogRecord> = emptyList(),
    val appVersion: String = BUILD_CONFIG.appVersion
)

// SharedState composable for Android
@Serializable
data class SharedStateUi(
    val currentUser: User? = null,
    val groups: List<GroupViewModel> = emptyList(),
    val selectedGroupId: String? = null,
    val notificationList: List<NotificationViewModel> = emptyList(),
    val unreadNotificationsCount: Int = 0,
    val subscription: SubscriptionStatus = SubscriptionStatus.Unknown,
    val expenses: List<ExpenseViewModel> = emptyList(),
    val activity: List<ActivityViewModel> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)
```

### 7.2 ViewModel Pattern

```kotlin
// GroupsViewModel
class GroupsViewModel(
    private val repository: GroupRepository,
    private val navigation: NavHostController<NavHostGraph>,
    private val authManager: AuthManager
) : ViewModel() {

    private val _groupsState = MutableStateFlow(GroupsState())
    val groupsState: StateFlow<GroupsState> = _groupsState.asStateFlow()

    fun loadGroups() = viewModelScope.launch {
        _groupsState.value = GroupsState(
            loading = true,
            groups = emptyList(),
            error = null
        )
        try {
            val groups = repository.getGroups()
            _groupsState.value = GroupsState(
                loading = false,
                groups = groups,
                error = null
            )
        } catch (e: Exception) {
            _groupsState.value = GroupsState(
                loading = false,
                groups = emptyList(),
                error = e.message
            )
        }
    }
}

// GroupsState sealed class
sealed class GroupsState {
    object Loading : GroupsState()
    data class Success(val groups: List<Group>) : GroupsState()
    object Empty : GroupsState()
    data class Error(val message: String) : GroupsState()
}
```

---

## 8. Local Database

### 8.1 Room Database Schema

```kotlin
@Database(entities = [UserEntity, GroupEntity, ExpenseEntity, PayoutEntity,
                     NotificationEntity, InvitationEntity, SettlementRoundEntity],
          version = 1, exportSchema = true)
abstract class KharchaTrackerDatabase : RoomDatabase() {
    abstract val userDao: UserDao
    abstract val groupDao: GroupDao
    abstract val expenseDao: ExpenseDao
    abstract val payoutDao: PayoutDao
    abstract val notificationDao: NotificationDao
    abstract val invitationDao: InvitationDao
    abstract val settlementRoundDao: SettlementRoundDao
}
```

### 8.2 DAO Interfaces

```kotlin
interface GroupDao {
    @Query("SELECT * FROM groups WHERE group_id = :groupId ORDER BY created_at DESC")
    suspend fun getGroupById(groupId: String): Group?
    
    @Query("SELECT * FROM groups ORDER BY activity_timestamp DESC")
    suspend fun getGroupsForUser(userId: String): List<Group>
    
    @Query("SELECT * FROM groups WHERE invite_code = :inviteCode")
    suspend fun getGroupByInviteCode(inviteCode: String): Group?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGroup(group: Group): Long
    
    @Update
    suspend fun updateGroup(group: Group): Int
    
    @Delete
    suspend fun deleteGroup(group: Group): Int
    
    @Query("SELECT * FROM group_members WHERE group_id = :groupId")
    suspend fun getGroupMembers(groupId: String): List<GroupMember>
    
    @Query("SELECT * FROM group_expenses WHERE group_id = :groupId")
    suspend fun getGroupExpenses(groupId: String): List<Expense>
}
```

---

## 9. Screens Implementation

### 9.1 Login/Signup Flow

```kotlin
// AuthenticationFlow
sealed class AuthenticationFlow {
    object Login : AuthenticationFlow()
    object Signup : AuthenticationFlow()
    object MagicLink : AuthenticationFlow()
    object Result(val user: User, val tokens: AuthTokens) : AuthenticationFlow()
    data class Error(val code: String, val message: String) : AuthenticationFlow()
}
```

### 9.2 Main Navigation

```kotlin
// MainNavigation
sealed class MainNavigation {
    object Dashboard : MainNavigation()
    object Groups : MainNavigation()
    object Expenses : MainNavigation()
    object Activity : MainNavigation()
    object Settings : MainNavigation()
    object Subscription : MainNavigation()
    data class GroupDetail(val groupId: String) : MainNavigation()
    data class ExpenseDetail(val expenseId: String) : MainNavigation()
    data class ActivityDetail(val activityId: String) : MainNavigation()
    data class NotificationDetail(val notificationId: String) : MainNavigation()
    data class Settlement(val groupId: String) : MainNavigation()
    data class PayoutDetail(val payoutId: String) : MainNavigation()
}
```

---

## 10. Permissions & Background

### 10.1 Required Permissions

```kotlin
// AndroidManifest.xml permissions
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
<uses-permission android:name="android.permission.SCHEDULE_EXACT_ALARM" />
<uses-permission android:name="android.permission.USE_EXACT_ALARM" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
```

### 10.2 Background Sync Service

```kotlin
// WorkManager for background sync
class SyncWorker(
    context: Context,
    params: WorkerParameters
) : Worker(context, params) {

    override fun doWork() {
        val token = getAuthToken()
        if (token == null || isTokenExpired(token)) {
            sendToLogin()
            return
        }
        
        syncNotifications()
        syncGroupMembers()
        syncExpenses()
    }
}

// Foreground service for real-time sync
class SyncForegroundService(
    context: Context,
    params: Bundle
) : Service(context, params) {

    override fun onCreate() {
        startForeground(NOTIFICATION_ID, SyncNotification())
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        scheduleWork()
        return START_STICKY
    }
}
```

---

## 11. Feature Implementation Priority

| Phase | Feature | Priority | Notes |
|-------|---------|---------|-------|
| 1 | Auth (Login/Signup/Logout) | P0 | Required for all |
| 2 | Groups (CRUD, Members) | P0 | Core functionality |
| 3 | Subscription Management | P0 | Required for continued use |
| 4 | Expense Entry | P0 | Primary app purpose |
| 5 | Expense List/Edit | P1 | Improve entry experience |
| 6 | Activity/Audit Log | P1 | Track history |
| 7 | Settlement Panel | P1 | Financial reconciliation |
| 8 | Push Notifications | P2 | Real-time updates |
| 9 | Magic Link SSO | P2 | Alternative login |
| 10 | Offline Mode | P2 | Work without internet |

---

## 12. Implementation Roadmap

### Phase 1: Foundation (Weeks 1-2)
- [ ] Project setup with KMP
- [ ] Core data models (User, Group, Expense, etc.)
- [ ] Room database schema
- [ ] Network layer (API client)
- [ ] Dependency injection (Koin)
- [ ] Theme setup (Material 3)

### Phase 2: Authentication (Weeks 3-4)
- [ ] Login screen
- [ ] Signup screen
- [ ] Token management (access/refresh)
- [ ] Magic link SSO
- [ ] Session persistence

### Phase 3: Groups (Weeks 5-7)
- [ ] Groups list screen
- [ ] Create group
- [ ] Join via invite code
- [ ] Group details
- [ ] Member management

### Phase 4: Expenses (Weeks 8-10)
- [ ] Expense entry screen
- [ ] Custom splits
- [ ] Expense list
- [ ] Edit/delete expenses

### Phase 5: Dashboard (Weeks 11-12)
- [ ] Dashboard screen
- [ ] Expense summary cards
- [ ] Spending trends chart
- [ ] Recent activity

### Phase 6: Settlement (Weeks 13-15)
- [ ] Settlement preview
- [ ] Payout management
- [ ] Settlement rounds

### Phase 7: Activity (Weeks 16-17)
- [ ] Activity log screen
- [ ] Activity filtering
- [ ] Group activity

### Phase 8: Settings (Weeks 18-19)
- [ ] User profile
- [ ] Notifications
- [ ] Theme customization

### Phase 9: Polish (Week 20)
- [ ] Bug fixes
- [ ] Performance optimization
- [ ] Analytics integration
- [ ] Testing

---

## 13. UI Component Library

### Core Components
- `KharchaTopAppBar` - Custom top bar
- `KharchaBottomBar` - Custom bottom navigation
- `KharchaDrawer` - Side drawer for desktop
- `KharchaCard` - Card component
- `KharchaButton` - Button variants (filled, tonal, outlined)
- `KharchaIconButton` - Icon button
- `KharchaTextField` - Custom text field
- `KharchaSwitch` - Toggle switch
- `KharchaChip` - Status chips

### Layout Components
- `KharchaGrid` - Grid layout
- `KharchaRow` - Horizontal scroll
- `KharchaColumn` - Vertical scroll

### Form Components
- `ExpenseForm` - Expense entry form
- `SplitDialog` - Split percentage picker
- `AddMemberDialog` - Add member form
- `EditExpenseDialog` - Edit expense form

### Chart Components
- `ExpenseChart` - Bar/Pie chart for expenses
- `TrendsChart` - Spending trends over time
- `DuesChart` - Outstanding dues chart

---

## 14. Technical Considerations

### 14.1 Build Configuration

```kotlin
// build.gradle.kts for app module
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.ksp)
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.kharcha.tracker"
    
    defaultConfig {
        applicationId = "com.kharcha.tracker"
        minSdk = 24
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0.0"
        vectorDrawables {
            useSupportLibrary = true
        }
    }
    
    buildFeatures {
        compose = true
        kotlinParcelable = true
    }
    
    composeOptions {
        kotlinCompilerExtensionVersion = libs.versions.kotlin.get()
    }
}

dependencies {
    implementation("androidx.core:core-ktx")
    implementation(platform("androidx.compose:compose-bom"))
    implementation("androidx.compose:compose-ui")
    implementation("androidx.compose:compose-ui-tooling")
    implementation("androidx.compose:compose-ui-material3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx")
    implementation("androidx.activity:activity-compose")
    
    implementation("io.coil-kt:coil-compose")
    implementation("androidx.datastore:datastore-preferences")
    implementation("com.google.accompanist:accompanist-systemuicontroller")
    implementation("com.google.accompanist:accompanist-permissions")
    implementation("com.google.accompanist:accompanist-pager")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json")
    implementation("io.ktor:ktor-client-android")
    implementation("io.ktor:ktor-client-content-negotiation")
    implementation("io.ktor:ktor-serialization-kotlinx-json")
    
    implementation("androidx.room:room-runtime")
    ksp("androidx.room:room-compiler")
    
    implementation("io.insert-koin:koin-android")
    implementation("io.insert-koin:koin-androidx-compose")
    
    implementation("androidx.hilt:hilt-navigation-compose")
}
```

### 14.2 Desktop Build

```kotlin
// desktop module build.gradle.kts
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.compose)
}

compose.desktop {
    application {
        mainClass = "com.kharcha.trackerdesktop.MainKt"
        nativeDistributions {
            // Configure distribution
        }
    }
}
```

---

## 15. Testing Strategy

### 15.1 Unit Tests
- Repository implementations
- Business logic validation
- Utility functions

### 15.2 Compose Tests
- Layout tests
- Composable tests with `createComposeRule`
- Permission tests

### 15.3 Integration Tests
- Navigation flow tests
- API integration tests with TestFlight/TestRunner

---

## 16. Risk Assessment

### High Priority Risks
1. **Offline-first design complexity** - Need robust caching and sync logic
2. **Large dataset performance** - Need efficient query optimization
3. **Cross-platform code sharing** - KMP may have limitations
4. **Material 3 learning curve** - Less mature than Material 2

### Mitigation Strategies
1. Implement incremental sync with queue management
2. Use pagination and lazy loading
3. Start with Android-only, then share code
4. Leverage Material Design guidelines

---

## 17. Deliverables

1. **Android App** - Complete mobile application
2. **Desktop App** - Shared UI for desktop
3. **KMP Modules** - Shared data models and business logic
4. **API Client** - Multiplatform network layer
5. **Documentation** - Usage and developer documentation
6. **Test Suite** - Comprehensive test coverage
7. **Release Scripts** - Build, test, package scripts

---

## 18. Next Steps

Before implementation begins:

1. **Review the API contracts** - Ensure all endpoints are accessible from mobile
2. **Verify data model completeness** - Check for any mobile-specific requirements
3. **Confirm design system** - Approve Material 3 theme colors
4. **Plan for native platform-specific features** - Camera, NFC, biometric auth
5. **Determine chart implementation strategy** - Custom Compose charts vs MPAndroidChart

---

*This proposal covers the architecture, components, and roadmap for building a native Android frontend application for the Kharcha Tracker system, with mobile-first responsive design principles.*

I'll be ready to implement this upon your approval.