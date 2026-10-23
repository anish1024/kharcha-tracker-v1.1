# Android Frontend Implementation Plan — Kharcha Tracker v2.0

**Date:** October 2026  
**Status:** Awaiting Approval  
**Version:** Draft v1.0

---

## Executive Summary

This document proposes a comprehensive Android application for **Kharcha Tracker**, a group expense tracking and settlement system currently deployed as a Progressive Web App (PWA). The Android app will provide **feature parity** with the existing web frontend while being optimized for native Android experiences.

**Current State:** Kharcha Tracker is a mobile-first SPA built with vanilla HTML/CSS/JS, with all core features implemented (authentication, groups, expenses, settlement, invitations, notifications, activity logs).

**Goal:** Migrate to a native Android application using Kotlin that maintains all existing functionality while providing an optimized mobile experience with offline support, push notifications, and background sync.

---

## 1. Market Research: Existing Android Expense Tracking Apps

### 1.1 Competitive Landscape

| App | Key Features | Architecture | Use Case |
|-----|-------------|--------------|----------|
| **Splitwise** | Expense tracking, bill splitting, debt settlement, friends/family groups | Native | Personal relationships, recurring friends groups |
| **Expensify** | Receipt scanning, corporate expense management, reimbursement automation | Hybrid/Native | Business expense management |
| **Zoho Expense** | Receipt OCR, custom workflows, approval routing, employee management | Native + Cloud | SME business expense management |
| **Wesplit** | Split bill by item, custom percentages, settlement tracking | Native | Quick casual bill splitting |
| **MyExpenses (Kotlin)** | Expense categories, monthly reports, simple tracking | Native | Simple personal finance tracking |

### 1.2 Key Learnings

1. **Offline-First:** Successful expense apps support offline mode with sync capabilities
2. **Push Notifications:** Critical for real-time collaboration (invites, settlement reminders)
3. **Native Components:** Better performance, access to device features (camera, location)
4. **Material Design:** Adopt Material You (Material 3) for modern aesthetic and ergonomics
5. **Background Sync:** Essential for collaborative apps with shared state

---

## 2. Technical Architecture Recommendations

### 2.1 Recommended Stack

| Component | Technology | Rationale |
|-----------|------------|----------|
| **Language** | Kotlin | Official Android language, modern coroutine support |
| **UI Toolkit** | Jetpack Compose | Modern declarative UI, material 3 integration, better developer experience |
| **Architecture** | MVVM + Clean Architecture | Separation of concerns, testability, maintainability |
| **Async/AI** | Kotlin Coroutines + Flow | Non-blocking async, reactive streams |
| **Databinding** | DataStore (Preferences + Proto) | Native storage, type-safe, Jetpack 2.x |
| **Networking** | Retrofit + OkHttp | Mature Retrofit 2.x/3.x, support for token auth |
| **JSON** | Kotlinx Serialization | Type-safe serialization, faster than Gson |
| **Local DB** | Room | Official Android persistence solution |
| **Hilt/DI** | Hilt | Standard Android DI solution |
| **Testing** | JUnit + Espresso + Robolectric | Unit, UI, and offline tests |

### 2.2 Architecture Layers

```
┌─────────────────────────────────────────────────────────┐
│                    PRESENTATION                        │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐    │
│  │   Screens   │  │   Compose   │  │   Effects   │    │
│  │  (Jetpack   │  │   Layouts   │  │   Events    │    │
│  │   Compose)  │  │   Modals    │  │   Actions   │    │
│  └─────────────┘  └─────────────┘  └─────────────┘    │
│                    ▼      ▲          ▲                │
│              ViewModels (Kotlin)                      │
├─────────────────────────────────────────────────────────┤
│                     DOMAIN                             │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐    │
│  │  Business   │  │  Auth       │  │  Services   │    │
│  │   Logic     │  │  UseCases   │  │  Repository │    │
│  └─────────────┘  └─────────────┘  └─────────────┘    │
│                    ▼           ▼         ▼           │
│              Repository Layer (Interface)             │
├─────────────────────────────────────────────────────────┤
│                    DATA                                │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐    │
│  │   Room DB   │  │  DataStore  │  │ Retrofit API│    │
│  │  Persistence│  │  Preferences│  │  Network    │    │
│  └─────────────┘  └─────────────┘  └─────────────┘    │
└─────────────────────────────────────────────────────────┘
```

### 2.3 Key Design Decisions

1. **Offline-First Storage:**
   - All data cached locally in Room DB
   - Changes applied locally, synced when online
   - Conflict resolution on sync

2. **Real-Time Sync:**
   - Background sync using WorkManager
   - Firebase Cloud Messaging for push notifications
   - Optimistic UI updates with deferred API calls

3. **Authentication Flow:**
   - Tokens stored in encrypted DataStore
   - Auto-refresh tokens in background
   - Biometric auth for session unlock

---

## 3. Feature Parity Mapping

### 3.1 Core Modules (Existing Web → Android)

| Web Module | Android Package | Key Features |
|------------|-----------------|--------------|
| `auth_service` | `com.kharcha.auth` | Login, Signup, Token refresh, Magic link, SSO |
| `group_service` | `com.kharcha.groups` | Create, Join, Invite, Members, Delete, Lock/Unlock |
| `expense_service` | `com.kharcha.expenses` | Add, Edit, Delete, List expenses |
| `subscription_service` | `com.kharcha.subscription` | Plan activation, status, entry limits |
| `invitation_service` | `com.kharcha.invitation` | Email invites, Accept/Decline |
| `notification_service` | `com.kharcha.notifications` | Push notifications, local alerts |
| `settlement_engine` | `com.kharcha.settlement` | Matrix calculation, payout generation |

### 3.2 Views Mapping

| Web View (SPA) | Android Screen (Jetpack Compose) | Compose Component |
|----------------|----------------------------------|-------------------|
| Dashboard | DashboardScreen | `Composable` with summary cards |
| Groups | GroupsScreen | `@Composable` with group cards |
| Settings | SettingsScreen | Settings screen with user prefs |
| Subscription | SubscriptionScreen | Plan cards with purchase flow |
| Expenses | ExpensesScreen | Expense list + Add form |
| Activity | ActivityScreen | Timeline / Activity feed |
| Magic Link | (New) LoginScreen | Enhanced with magic link |
| Settlement Panel | (New) SettlementScreen | Settlement calculations + actions |

---

## 4. Detailed Android App Structure

### 4.1 Module Breakdown

```
KharchaTracker/
├── KharchaTracker/                    # App root (module name)
│   ├── app/                          # Main app module
│   │   ├── data/                     # Data layer
│   │   │   ├── local/                # Local data sources
│   │   │   │   ├── database/         # Room database
│   │   │   │   │   ├── entity/       # Data entities
│   │   │   │   │   ├── dao/          # Data access objects
│   │   │   │   │   └── Database.kt
│   │   │   │   └── datastore/        # Encrypted preferences
│   │   │   │       └── Preferences.kt
│   │   │   └── remote/               # Remote data sources
│   │   │       └── api/              # Retrofit API interface
│   │   │           └── KharchaApi.kt
│   │   │
│   │   ├── domain/                   # Domain layer
│   │   │   ├── usecases/            # Business logic use cases
│   │   │   │   ├── auth/            # Login, Signup, Refresh
│   │   │   │   ├── group/           # CRUD groups
│   │   │   │   ├── expense/         # CRUD expenses
│   │   │   │   ├── subscription/    # Subscription management
│   │   │   │   ├── invitation/      # Invitation handling
│   │   │   │   ├── notification/    # Notification operations
│   │   │   │   └── settlement/      # Settlement calculations
│   │   │   └── model/               # Domain models (mirrors DTOs)
│   │   │
│   │   ├── presentation/             # Presentation layer
│   │   │   ├── di/                  # Hilt modules
│   │   │   │   ├── AppModule.kt
│   │   │   │   ├── NetworkModule.kt
│   │   │   │   └── RepositoryModule.kt
│   │   │   │
│   │   │   ├── screens/             # Top-level screens
│   │   │   │   ├── auth/            # Login, Signup, MagicLink
│   │   │   │   ├── dashboard/       # Dashboard screen
│   │   │   │   ├── groups/          # Groups screen
│   │   │   │   ├── expenses/        # Expenses screen
│   │   │   │   ├── activity/        # Activity/Log screen
│   │   │   │   ├── settings/        # Settings screen
│   │   │   │   ├── subscription/    # Subscription screen
│   │   │   │   └── settlement/      # Settlement screen
│   │   │   │
│   │   │   └── components/          # Reusable Composables
│   │   │       ├── common/          # Buttons, Cards, Inputs, Modals
│   │   │       ├── group/           # Group cards, member chips
│   │   │       ├── expense/         # Expense rows, forms
│   │   │       ├── settlement/      # Settlement panels
│   │   │       ├── notification/    # Notification items
│   │   │       └── activity/        # Activity table items
│   │   │
│   │   └── navigation/              # Navigation graph
│   │       └── NavGraph.kt
│   │
│   ├── domain-model/                 # Shared domain models
│   │   └── src/
│   │       └── main/kotlin/
│   │           ├── user/            # UserRecord, UserProfile
│   │           ├── group/          # Group, Member, Invitation
│   │           ├── expense/        # Expense, ExpensePayment
│   │           ├── settlement/     # Payout, SettlementRound
│   │           └── model/          # Base model classes
│   │
│   └── core/                        # Shared core utilities
│       ├── auth/                   # Token management
│       ├── network/                # Network utilities, interceptors
│       ├── storage/                # Storage abstractions
│       ├── ui/                     # Common UI components
│       └── constants/              # App constants, enums
│
├── kharcha-backend/                 # Backend API (existing)
│   └── api/
│       └── KharchaApi.kt            # Auto-generated from Retrofit
│
├── build.gradle.kts                # App build config
├── buildservices.gradle.kts        # Backend build config
└── settings.gradle.kts             # Project settings
```

### 4.2 Database Schema (Room Entities)

Mirror the existing SQLite schema with proper Android Room types:

```kotlin
@Entity(tableName = "user_record")
data class UserRecord(
    @PrimaryKey(autoGenerate = true) val id: Int,
    @ColumnInfo(name = "user_id") val userId: String,
    val name: String,
    val email: String,
    val phone_number: String?,
    val avatar_url: String?,
    val trial_ends_at: String?,
    val plan: String,
    val plan_status: String,
    val plan_ends_at: String?,
    val limited_entries: Int = 0,
    val is_deleted: Boolean = false,
    @ColumnInfo(name = "last_login_at") val lastLoginAt: String,
    @ColumnInfo(name = "created_at") val createdAt: String,
    @ColumnInfo(name = "updated_at") val updatedAt: String
)

@Entity(tableName = "group_record")
data class GroupRecord(
    @PrimaryKey(autoGenerate = true) val id: Int,
    @ColumnInfo(name = "group_id") val groupId: String,
    val name: String,
    val currency: String,
    val member_count: Int,
    val status: String,
    val invite_code: String,
    val owner_id: String,
    val owner_name: String,
    val is_locked: Boolean,
    val is_deleted: Boolean = false,
    val settle_for: String?,
    @ColumnInfo(name = "created_at") val createdAt: String,
    @ColumnInfo(name = "updated_at") val updatedAt: String
)

@Entity(tableName = "expense_record")
data class ExpenseRecord(
    @PrimaryKey(autoGenerate = true) val id: Int,
    @ColumnInfo(name = "trans_id") val transId: Int,
    @ColumnInfo(name = "group_id") val groupId: String,
    @ColumnInfo(name = "expn_desc") val expnDesc: String,
    @ColumnInfo(name = "expn_ctgry") val expnCtgry: String,
    val amount: BigDecimal,
    @ColumnInfo(name = "date_time") val dateTime: String,
    @ColumnInfo(name = "status") val status: String,
    @ColumnInfo(name = "paid_by") val paidBy: List<ExpensePayment>?,
    val share_map: Map<String, Int>,
    val is_deleted: Boolean = false,
    @ColumnInfo(name = "created_at") val createdAt: String,
    @ColumnInfo(name = "updated_at") val updatedAt: String
)

@Entity(tableName = "payout_record")
data class PayoutRecord(
    @PrimaryKey(autoGenerate = true) val id: Int,
    @ColumnInfo(name = "payout_id") val payoutId: String,
    val amount: BigDecimal,
    val status: String,
    @ColumnInfo(name = "payer_id") val payerId: String,
    @ColumnInfo(name = "payer_name") val payerName: String,
    @ColumnInfo(name = "payee_id") val payeeId: String,
    @ColumnInfo(name = "payee_name") val payeeName: String,
    @ColumnInfo(name = "paid_at") val paidAt: String?,
    @ColumnInfo(name = "settled_at") val settledAt: String?,
    val failure_reason: String?,
    val cancel_reason: String?,
    val is_deleted: Boolean = false,
    val replaces_ids: List<String>?,
    @ColumnInfo(name = "created_at") val createdAt: String,
    @ColumnInfo(name = "updated_at") val updatedAt: String
)
```

### 4.3 Datastore (Encrypted Preferences)

```kotlin
data class AuthPreferences(
    val access_token: String,
    val refresh_token: String,
    val token_type: String,
    val token_expiry: Long,
    val user: UserProfile,
    val magic_token: String?,
    val last_sync: Long
)

sealed class ProfilePreferences {
    data object Name : ProfilePreferences()
    data object Email : ProfilePreferences()
    data object Phone : ProfilePreferences()
    data object AvatarUrl : ProfilePreferences()
}
```

---

## 5. Screen-by-Screen Implementation Plan

### 5.1 Authentication Flow

```
┌─────────────────────────────────────────────────────────┐
│                  Splash Screen                          │
│  (Check cached credentials, show logo)                  │
└─────────────────────────────────────────────────────────┘
                      │
                      ▼
┌─────────────────────────────────────────────────────────┐
│                  Login Screen                           │
│  ┌─────────────────────┐                               │
│  │  [Logo]             │  "Welcome back!"              │
│  └─────────────────────┘                               │
│  Email: [            ]  Password: [            ]      │
│  [Login Button]                                          │
│                                                      │
│  ──────────────────────────────────────                │
│  Or                                                   │
│  [Magic Link - Email]  → [Send Code]                  │
│  "Check your email for the login code"                 │
│                                                      │
│  ──────────────────────────────────────                │
│  [Don't have an account? Sign up]                     │
│  [Forgot Password?]                                   │
└─────────────────────────────────────────────────────────┘
                      │
           ┌──────────┼──────────┐
           ▼          ▼          ▼
    ┌──────────┐  ┌──────────┐  ┌──────────┐
    │   Login  │  │  Magic   │  │   Signup  │
    │   Screen │  │  Verify  │  │   Screen  │
    └──────────┘  └──────────┘  └──────────┘
           │          │          │
           └──────────┼──────────┘
                      ▼
        ┌─────────────────────┐
        │      Dashboard      │
        └─────────────────────┘
```

### 5.2 Dashboard Screen

```
┌─────────────────────────────────────────────────────────┐
│  [TopBar] Group Selector | Profile | [Bell] [Menu]       │
├─────────────────────────────────────────────────────────┤
│                                                          │
│  ┌─────────────────┐  ┌─────────────────┐              │
│  │  Stat Cards     │  │  Subscription    │              │
│  │  • Total Groups │  │  Your Plan      │              │
│  │  • Total Users  │  │  Trial Status    │              │
│  │  • Open Groups  │  │  ─────────────   │              │
│  │  • Pending      │  │  Plan Name      │              │
│  │    Invites      │  │  Renewal Date   │              │
│  └─────────────────┘  └─────────────────┘              │
│                                                          │
├─────────────────────────────────────────────────────────┤
│  Dashboard Content:                                     │
│                                                          │
│  ┌─────────────────────────────────┐                    │
│  │  Recent Activity Feed           │                    │
│  │  ┌────┬────┬────────┬─────────┐ │                    │
│  │  │Time│Type│ Actor   │ Details  │ │                    │
│  │  ├────┼────┼────────┼─────────┤ │                    │
│  │  │...│...│...       │...       │ │                    │
│  └─────────────────────────────────┘                    │
│                                                          │
│  ┌─────────────────────────────────┐                    │
│  │  Expense Summary by Category    │                    │
│  │  [Bar Chart: Weekly Spending]   │                    │
│  └─────────────────────────────────┘                    │
│                                                          │
│  ┌─────────────────────────────────┐                    │
│  │  Quick Actions                  │                    │
│  │  [＋ New Group] [＋ Add Expense] │                    │
│  └─────────────────────────────────┘                    │
│                                                          │
│  [TabBar] Dashboard | Groups | Expenses | Activity | ... │
└─────────────────────────────────────────────────────────┘
```

### 5.3 Groups Screen

```
┌─────────────────────────────────────────────────────────┐
│  [TopBar] "Groups"                                        │
│                                                          │
│  Groups List:                                            │
│  ┌─────────────────────────────────────────────────────┐│
│  │  [Avatar] Group Name       [Status Pill] [⋮]        ││
│  │  • Owner Name          • Member Count              ││
│  │  • Currency              [New Code]    [Copy]      ││
│  │  • [Expense Items]      [Edit] [Settle] [Leave]    ││
│  └─────────────────────────────────────────────────────┘│
│  ┌─────────────────────────────────────────────────────┐│
│  │  [Avatar] Group Name       [Status Pill] [⋮]        ││
│  │  • Owner Name          • Member Count              ││
│  │  • Currency              [New Code]    [Copy]      ││
│  │  • [Expense Items]      [Edit] [Settle] [Leave]    ││
│  └─────────────────────────────────────────────────────┘│
│                                                          │
├─────────────────────────────────────────────────────────┤
│  Group Actions:                                          │
│  [＋ Create New Group]    [📨 Invite by Email]           │
│                                                          │
├─────────────────────────────────────────────────────────┤
│  [TabBar] Dashboard | Groups | Expenses | Activity | ... │
└─────────────────────────────────────────────────────────┘
```

**Group Card Component:**

```kotlin
@Composable
fun GroupCard(
    group: Group,
    memberCount: Int,
    onEdit: () -> Unit,
    onSettle: () -> Unit,
    onNewExpense: () -> Unit,
    onInvite: () -> Unit,
    onCopyInviteCode: () -> Unit,
    onLeave: () -> Unit,
    onDelete: () -> Unit,
    onTapExpense: () -> Unit,
    onToggleSettle: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialColor.PrimaryLight)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Group header with name, status, owner
            // Expense list
            // Settle panel toggle
            // Quick actions row
        }
    }
}
```

---

## 6. UI/UX Design Principles

### 6.1 Design System (Material You / Material 3)

```
Colors:
- Primary: ColorScheme.primary
- Secondary: ColorScheme.secondary
- Tertiary: ColorScheme.tertiary
- Surface: MaterialColor.Surface
- Background: MaterialColor.SurfaceVariant

Typography:
- Display Large: Headings
- Headline Large: Page titles
- Headline Medium: Card titles
- Title Large: Section titles
- Body Large: Body text
- Body Medium: Secondary text
- Label Small: Caption, disabled states

Shape:
- Extra Small: 4.dp (small chips, pills)
- Small: 8.dp (table cells, modal corners)
- Medium: 12.dp (card corners, buttons)
- Large: 24.dp (fab, modal corners)

Elevation:
- None: 0.dp
- Small: 2.dp
- Medium: 4.dp
- Large: 8.dp

Animation:
- Enter: SlideIn, FadeIn
- Exit: SlideOut, FadeOut
- Pulse: Ripple effect
- Shake: For error feedback
```

### 6.2 Mobile-First Design Breakpoints

| Breakpoint | Width | Layout Strategy |
|------------|-------|-----------------|
| Mobile | < 600dp | Single column, stacked cards |
| Tablet | 600-900dp | Two columns, grid layouts |
| Desktop | > 900dp | Multi-column, side panel |

For Android:
- Default: Mobile-first
- Tablet support: Use `densityInDp` to detect and adapt layout

### 6.3 Touch Targets

- Minimum touch target: **48x48 dp** (Material spec)
- Button padding: At least 8dp on all sides
- Tab bar items: Full width or minimum 64dp

### 6.4 Navigation

- Bottom navigation bar for main screens (5 items max)
- Top app bar for navigation back
- Floating Action Button for primary actions
- Navigation drawer for global actions

### 6.5 Form Elements

```kotlin
// Text fields
@Composable
fun TextField(
    label: String,
    hint: String,
    value: String,
    onValueChange: (String) -> Unit,
    error: String? = null
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.3f),
        isError = error != null,
        supportingText = { if (error != null) { Text(error) } },
        singleLine = true
    )
}

// Radio group for categories
@Composable
fun CategoryRadioGroup(
    selectedCategory: ExpenseCategory?,
    onSelectCategory: (ExpenseCategory) -> Unit
) {
    RadioGroup(
        selectedId = selectedCategory?.ordinal ?: 0,
        onCheckedChange = { position ->
            val category = ExpenseCategory.values()[position]
            onSelectCategory(category)
        }
    ) {
        for (category in ExpenseCategory.values()) {
            Radio(
                text = { Text(category.name) },
                id = category.ordinal,
                modifier = Modifier.fillMaxWidth(0.2f)
            )
        }
    }
}

// Amount input
@Composable
fun AmountInput(
    amount: BigDecimal,
    currency: String,
    onAmountChange: (String) -> Unit
) {
    OutlinedTextField(
        value = amount.toString(),
        onValueChange = onAmountChange,
        label = { Text("Amount") },
        modifier = Modifier.fillMaxWidth(0.8f),
        textStyle = TextStyle(color = MaterialColor.Primary)
    )
}
```

### 6.5 Modals & Dialogs

```kotlin
@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialColor.Primary
                )
            ) {
                Text("Confirm")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun ExpenseModal(
    expense: ExpenseRecord?,
    onAddExpense: () -> Unit,
    onEditExpense: () -> Unit,
    onDeleteExpense: (String) -> Unit,
    onClose: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onClose
    ) {
        // Expense form
        // Add/Edit/Delete expense rows
    }
}
```

---

## 7. Key Implementation Phases

### Phase 1: Foundation (Weeks 1-2)

**Goal:** Set up the project structure and core infrastructure

**Deliverables:**
- [ ] Initialize project with Kotlin 2.0, Jetpack Compose BOM
- [ ] Configure Gradle with Compose Multiplatform (optional for shared code)
- [ ] Setup Hilt dependency injection
- [ ] Setup Retrofit for API client
- [ ] Setup Room database
- [ ] Setup DataStore for preferences
- [ ] Setup Firebase for push notifications
- [ ] Create Hilt modules
- [ ] Implement API client with base model classes
- [ ] Implement local repository interfaces
- [ ] Configure network interceptors (auth, offline caching)

### Phase 2: Authentication (Weeks 3-4)

**Goal:** Implement login, signup, and session management

**Deliverables:**
- [ ] Splash screen (offline check, cached credentials)
- [ ] Login screen (email/password + magic link)
- [ ] Signup screen
- [ ] Magic link verification
- [ ] Session management (auto-logout on expiry)
- [ ] Logout functionality
- [ ] Offline auth storage
- [ ] Token refresh mechanism
- [ ] Unit tests for auth logic

### Phase 3: User Management (Week 5)

**Goal:** Implement user profile and settings

**Deliverables:**
- [ ] Settings screen
- [ ] User profile view
- [ ] Subscription status view
- [ ] Subscription upgrade flow
- [ ] Profile editing
- [ ] Theme settings (Material You colors)
- [ ] Privacy settings

### Phase 4: Group Management (Weeks 6-7)

**Goal:** Create, join, and manage groups

**Deliverables:**
- [ ] Groups list screen
- [ ] Group creation form
- [ ] Group detail screen
- [ ] Group member management
- [ ] Invite flow (email + code)
- [ ] Group join by invite code
- [ ] Accept/Decline invitation
- [ ] Group lock/unlock
- [ ] Group delete/leave
- [ ] Offline support with sync

### Phase 5: Expense Management (Weeks 8-9)

**Goal:** Full expense CRUD with split payments

**Deliverables:**
- [ ] Expenses list screen
- [ ] Add expense form
- [ ] Edit expense form
- [ ] Delete expense
- [ ] Split payment calculator
- [ ] Expense detail view
- [ ] Offline support
- [ ] Export to CSV

### Phase 6: Settlement (Weeks 10-11)

**Goal:** Implement settlement calculations and actions

**Deliverables:**
- [ ] Settlement calculation (matrix)
- [ ] Settlement detail screen
- [ ] Settlement action panel
- [ ] Payout list
- [ ] Payout actions (pay, confirm, remind, escalate)
- [ ] Record payment
- [ ] Ad-hoc payment
- [ ] Settlement round view
- [ ] Offline settlement with conflict handling

### Phase 7: Activity Log (Week 12)

**Goal:** Real-time activity tracking

**Deliverables:**
- [ ] Activity timeline screen
- [ ] Activity filters (date range)
- [ ] Activity details
- [ ] Activity export
- [ ] Real-time updates
- [ ] Offline activity cache

### Phase 8: Notifications (Week 13)

**Goal:** Push notification integration

**Deliverables:**
- [ ] Firebase Cloud Messaging setup
- [ ] Notification channels
- [ ] Notification display
- [ ] Notification handling
- [ ] Mark as read
- [ ] Notification preferences

### Phase 9: Testing & Polish (Week 14)

**Goal:** Comprehensive testing and UI polish

**Deliverables:**
- [ ] UI tests with Espresso
- [ ] Integration tests
- [ ] Performance optimization
- [ ] Accessibility (TalkBack)
- [ ] Security audit
- [ ] Beta testing with users
- [ ] Release to Play Store

---

## 8. Offline-First Strategy

### 8.1 Data Sync Architecture

```
Offline Mode:
1. Cache data locally in Room DB
2. Enable app usage without network
3. Queue mutations (Create, Update, Delete)
4. Sync when online (background WorkManager)
5. Resolve conflicts (last-write-wins with timestamp)
6. Push server changes to local DB
7. Retry failed mutations
```

### 8.2 Conflict Resolution

```kotlin
sealed class SyncConflict {
    data object LastWriteWins : SyncConflict()
    data class ManualMerge(val conflictType: String) : SyncConflict()
}

// Implementation
suspend fun resolveConflict(
    localVersion: Expensed,
    serverVersion: Expensed
): Expensed {
    return if (localVersion.updatedAt > serverVersion.updatedAt) {
        localVersion
    } else {
        serverVersion
    }
}
```

### 8.3 Sync Queue

```kotlin
@Entity(tableName = "sync_queue")
data class SyncQueueItem(
    @PrimaryKey(autoGenerate = true) val id: Int,
    val type: SyncType, // LOCAL_TO_SERVER, SERVER_TO_LOCAL
    val action: String, // CREATE, UPDATE, DELETE
    val resourceType: String, // EXPENSE, GROUP, PAYMENT
    val resourceIds: List<String>,
    val payload: Map<String, Any>,
    val attemptCount: Int,
    val lastAttemptAt: Long,
    val nextRetryAt: Long,
    val status: SyncStatus, // PENDING, QUEUED, SYNCED, FAILED
    val localId: String, // Reference to local DB
    val serverId: String? // Reference to server DB
)
```

### 8.4 Background Sync Worker

```kotlin
class SyncWorker @ServiceConstructor(
    private val context: Context,
    private val apiClient: KharchaApi,
    private val database: KharchaDatabase
) : Worker() {

    override fun doWork(ctx: WorkerParams) {
        val isMetered = NetworkIngressCalculator.calculateConnectionCost(
            NetworkType.fromProto(connectionInfo.capability)
        )
        
        if (!isMetered) {
            ctx.setRequirementDataRequirement(DataRequirement.REQUIRED)
        }
        
        // Sync queued mutations
        syncLocalToServer()
        // Pull server changes
        syncServerToLocal()
    }
}
```

---

## 9. Security Considerations

### 9.1 Authentication & Token Storage

```kotlin
// Encrypted DataStore
data class TokenStore(
    val accessToken: String,
    val refreshToken: String,
    val magicToken: String?,
    val permissions: List<String>
)

// Token refresh interceptor
class AuthInterceptor {
    private val tokenStore = SecureStorage.getTokens()
    private val cache = LruCache<String, Response>()
    
    override suspend fun intercept(
        chain: Interceptor.Chain<Request>,
        token: Token
    ): Result<Response> {
        val request = chain.request.apply {
            headers = chain.request.headers +
                Header("Authorization", "Bearer $token")
        }
        
        val response = chain.proceed(request)
        
        // Extract new tokens from response
        if (response.statusCode == 401) {
            val refreshResponse = apiClient.refreshToken(token.refreshToken)
            token = TokenStore(
                accessToken = refreshResponse.accessToken,
                refreshToken = refreshResponse.refreshToken
            )
        }
        
        // Cache successful response for offline
        if (response.statusCode == 200) {
            cache.put(request.path, response)
        }
        
        return Result.success(response)
    }
}
```

### 9.2 Data Encryption

```kotlin
// Use Android Keystore for key storage
class SecureStorage @Inject constructor(
    private val keystoreManager: KeystoreManager,
    private val dataStore: DataStore<Preferences>
) {
    fun getSensitiveData(): EncryptedPreferences {
        return dataStore.data
            .access { prefs ->
                Encrypter.decrypt(prefs)
            }
    }
    
    fun encryptData(data: Preferences): EncryptedPreferences {
        val key = createEncryptedKey()
        return dataStore.data.copy()
            .access { original ->
                Encrypter.encrypt(original, key)
            }
    }
}
```

### 9.3 Biometric Authentication

```kotlin
@Composable
fun BiometricAuth(
    title: String,
    message: String,
    onAuthenticate: () -> Unit,
    onFallback: () -> Unit
) {
    BiometricPrompt(
        title = { Text(title) },
        negativeButton = {
            TextButton(onClick = onFallback) {
                Text("Use Password")
            }
        },
        positiveButton = BiometricPrompt.PromptInfo.Builder()
            .setConfirmationText(message)
            .setAllowedAuthenticators(
                setOf(
                    Authenticator.TYPE_BIOMETRIC,
                    Authenticator.TYPE_FINGERPRINT
                )
            )
            .build(),
        executor = { authenticationInfo, result ->
            if (result.authenticated) {
                onAuthenticate()
            }
        }
    )
}
```

---

## 10. Push Notifications Integration

### 10.1 Notification Channels

```kotlin
// Define notification channels
val CHANNEL_GROUP_INVITE = NotificationChannel(
    "group_invite",
    "Group Invitations",
    NotificationManager.IMPORTANCE_HIGH,
    enableLights = true,
    enableVibration = true
)

val CHANNEL_GROUP_UPDATE = NotificationChannel(
    "group_update",
    "Group Updates",
    NotificationManager.IMPORTANCE_DEFAULT
)

val CHANNEL_EXPENSE = NotificationChannel(
    "expense",
    "Expense Reminders",
    NotificationManager.IMPORTANCE_LOW
)

val CHANNEL_SETTLEMENT = NotificationChannel(
    "settlement",
    "Settlement Alerts",
    NotificationManager.IMPORTANCE_HIGH
)
```

### 10.2 Notification Handlers

```kotlin
// Firebase Cloud Messaging
class MessageService @Inject constructor(
    private val fcmMessagingService: FirebaseMessaging
) {
    private val token: String = fcmMessagingService.token
        .orEmpty()
    
    init {
        fcmMessagingService.tokenRefreshCallback = {
            updateFcmToken(it)
        }
    }
}

// Notification Processor
class NotificationProcessor @Inject constructor(
    private val notificationService: NotificationService,
    private val appExecutors: AppExecutors
) {
    fun processNotification(notification: Map<String, Any>) {
        val kind = notification["kind"] as String
        
        when (kind) {
            "GROUP_INVITE_ACCEPTED",
            "GROUP_INVITE_DECLINED" -> {
                showInviteNotification(notification)
            }
            "SETTLEMENT_UPDATED" -> {
                showSettlementNotification(notification)
            }
            else -> {
                showGenericNotification(notification)
            }
        }
    }
    
    private fun handleAction(notification: Notification, action: String) {
        when (action) {
            "ACCEPT_INVITE" -> {
                // Navigate to accept invite screen
                val deepLink = "kharcha://invite/accept/${notification.groupId}"
                startActivity(Intent(Uri.parse(deepLink)))
            }
            "PAY_AMOUNT" -> {
                // Open payout payment sheet
                val amount = notification["amount"] as String
                openPaymentSheet(amount)
            }
        }
    }
}
```

---

## 11. Testing Strategy

### 11.1 Unit Tests (Espresso)

```kotlin
@Test
fun `calculateSplitReturnsEqualPercentages`() = runTest {
    val result = SplitCalculator.calculateEqualSplit(memberCount = 4)
    assertEquals(25, result.percentages[0])
    assertEquals(25, result.percentages[1])
    assertEquals(25, result.percentages[2])
    assertEquals(25, result.percentages[3])
}
```

### 11.2 Integration Tests (Espresso)

```kotlin
@Test
fun `userCanCreateGroupAndAddExpense`() = launchActivity<GroupsScreen>()
    .use {
        // Tap Create Group FAB
        onView(withId(R.id.fab_create_group))
            .perform(click())
        
        // Enter group name and currency
        // Tap OK
        
        // Tap New Expense button
        onView(withId(R.id.btn_add_expense))
            .perform(click())
        
        // Enter expense details
        // Add expense
        
        // Verify expense appears in list
        onView(withId(R.id.recycler_expenses))
            .check(matches(withItemDataContaining("Test Expense")))
    }
```

### 11.3 Offline Tests

```kotlin
@Test
fun `offlineModeAllowsAddExpense`() = runRobotTest {
    // Turn off network
    disableNetwork()
    
    // Navigate to add expense
    // Fill form
    // Submit
    
    // Verify toast for offline mode
    checkToastContains("Syncing changes when online...")
    
    // Turn on network
    enableNetwork()
    
    // Verify expense synced
    checkExpenseExistsInDashboard()
}
```

### 11.4 UI Tests

```kotlin
@Test
fun `loginScreenShowsLoginFormAndMagicLink`() {
    // Verify login form visibility
    onView(withId(R.id.view_login))
        .check(isDisplayed())
    
    // Verify magic link button
    onView(withId(R.id.btn_magic_link))
        .check(isDisplayed())
}
```

---

## 12. API Integration

### 12.1 Retrofit API Interface

```kotlin
@Headers("Content-Type: application/json")
interface KharchaApi {
    @GET("/api/v1/users/me")
    suspend fun getMyProfile(): Result<UserProfile>
    
    @POST("/api/v1/auth/login")
    suspend fun login(
        @Body request: LoginRequest
    ): Result<AuthResponse>
    
    @POST("/api/v1/auth/signup")
    suspend fun signup(
        @Body request: SignupRequest
    ): Result<AuthResponse>
    
    @POST("/api/v1/auth/refresh")
    suspend fun refreshToken(
        @Body refreshToken: String
    ): Result<AuthResponse>
    
    // Group endpoints
    @GET("/api/v1/groups")
    suspend fun getGroups(
        @Query("sort") sort: String? = null,
        @Query("limit") limit: Int? = null,
        @Query("offset") offset: Int? = null
    ): Result<List<Group>>
    
    // Expense endpoints
    @POST("/api/v1/expenses")
    suspend fun addExpense(
        @Body request: AddExpenseRequest
    ): Result<Expense>
    
    // Settlement endpoints
    @POST("/api/v1/groups/{groupId}/settle")
    @Path("groupId") groupId: String
    suspend fun initiateSettlement(
        @Body request: SettleRequest
    ): Result<SettlementRound>
}

// Data classes (Kotlinx Serialization)
data class LoginRequest(
    val email: String,
    val password: String
)

data class AuthResponse(
    val accessToken: String,
    val refreshToken: String,
    val user: UserProfile
)

@Serializable
data class UserProfile(
    val id: String,
    val name: String,
    val email: String,
    val trialEndsAt: String?,
    val plan: String,
    val planEndsAt: String?,
    val planStatus: String,
    val limitedEntries: Int = 0,
    val lastLoginAt: String,
    val createdAt: String
)
```

### 12.2 Offline API Client

```kotlin
class OfflineApiService : KharchaApi {
    private val remoteApi: KharchaApi
    private val cache = LruCache<String, CachedResponse>(50)
    private val syncQueue = PriorityQueue<SyncQueueItem>()
    
    override suspend fun getGroups(
        sort: String?,
        limit: Int?,
        offset: Int?
    ): Result<List<Group>> {
        // Check cache first
        return cacheGroups(sort, limit, offset)
    }
    
    suspend fun addExpenseAsync(
        request: AddExpenseRequest
    ) {
        // Queue for offline
        val queueItem = SyncQueueItem(
            type = SyncType.LOCAL_TO_SERVER,
            action = "CREATE",
            resourceType = "EXPENSE",
            resourceIds = listOf(request.expenseId),
            payload = mapOf("data" to request),
            status = SyncStatus.PENDING
        )
        
        syncQueue.add(queueItem)
        
        // Return cached local version
        val localExpense = database.getExpense(request.expenseId)
        Result.success(localExpense)
    }
    
    fun syncBackground() {
        // Process queue items
        while (syncQueue.isNotEmpty()) {
            val item = syncQueue.poll()
            when (item.status) {
                SyncStatus.SYNCED -> continue
                SyncStatus.FAILED -> {
                    if (item.attemptCount >= MAX_RETRIES) {
                        // Notify user
                        notifySyncError(item)
                        continue
                    }
                    // Retry
                }
            }
        }
    }
}
```

---

## 13. Performance Considerations

### 13.1 Composable Performance

```kotlin
// Use @Composable for recompose optimization
@Composable
fun ExpenseList(
    expenses: List<Expense>,
    onExpenseClick: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .nested()
    ) {
        items(
            count = expenses.size,
            key = { it.id }
        ) { expense ->
            ExpenseItem(
                expense = expense,
                onClick = { onExpenseClick(expense.id) },
                modifier = Modifier
                    .fillMaxWidth()
                    .itemScope()
            )
        }
    }
}

// Use memoization for expensive operations
@Composable
fun ExpenseSummary(expenses: List<Expense>): Map<String, BigDecimal> {
    val summary = remember(expenses) {
        expenses.associateBy { it.category }
            .mapValues { (_, categoryExpenses) ->
                categoryExpenses.sumOf { it.amount }
            }
    }
    return summary
}
```

### 13.2 Image Loading

```kotlin
// Use Compose image loading with caching
@Composable
fun AvatarImage(
    url: String?,
    fallbackId: String
) {
    Image(
        painter = rememberAsyncImagePainter(
            model = url?.let { LoadUri(url) }
                ?: rememberAsyncImagePainter(
                    model = LoadUri("default_avatar_@$fallbackId")
                )
        ),
        contentDescription = "User avatar",
        modifier = Modifier
            .size(40.dp)
            .asyncImage()
    )
}
```

---

## 14. Risk Assessment

### 14.1 Technical Risks

| Risk | Probability | Impact | Mitigation |
|------|-------------|--------|------------|
| Offline sync conflicts | Medium | High | Implement conflict resolution, user notifications |
| Large data sets | Medium | Medium | Pagination, lazy loading |
| Animation performance | Low | Medium | Use Compose animations, lazy composable |
| API changes | High | Medium | Abstract API in Retrofit interface |

### 14.2 Feature Gaps

| Feature | Status | Notes |
|---------|--------|-------|
| Receipt scanning | Planned | Integrate Google ML Kit for OCR |
| Push notifications | Phase 8 | Requires Firebase setup |
| Offline support | Phase 1 | Must be built into architecture |

### 14.3 Timeline Risks

| Risk | Probability | Mitigation |
|------|-------------|------------|
| Backend API changes | High | Implement backend first, then Android |
| Firebase limits | Medium | Test with realistic notification volume |
| Play Store review | Medium | Submit beta first, fix critical issues |

---

## 15. Approval Requirements

### 15.1 Pre-Approval Checklist

- [ ] Backend API is stable and documented
- [ ] Database schema is finalized
- [ ] Authentication flow is verified end-to-end
- [ ] Token storage and refresh mechanism is secure
- [ ] API versioning strategy is defined

### 15.2 Implementation Milestones

| Milestone | Deliverables | Timeline | Approval Point |
|-----------|--------------|----------|----------------|
| Foundation | Project structure, DI, API client | Week 2 | After setup |
| Auth | Login, Signup, Sessions | Week 4 | After login flow works |
| Groups | CRUD, Invite, Members | Week 7 | After E2E |
| Expenses | CRUD, Split, Offline | Week 9 | After testing |
| Settlement | Matrix, Payouts | Week 11 | After testing |
| Activity | Timeline, Sync | Week 12 | After testing |
| Polish | Notifications, Beta | Week 14 | Before release |

### 15.3 Resource Requirements

| Role | Count | Duration |
|------|-------|----------|
| Kotlin Developer | 1 | 14 weeks |
| UI Designer | 0.5 | 4 weeks |
| QA Tester | 0.5 | 3 weeks |
| Android Tester | 0.5 | 2 weeks |

### 15.4 Dependencies Required

```yaml
dependencies:
  com.google.android.hilt:
    - hilt-core: 1.1.0
    - hilt-navigation-compose: 1.1.0
  
  com.google.accompanist:
    - permission: 0.33.0
    - navigation: 0.31.2
  
  com.google.firebase:
    - cloud-messaging: 24.0.0
    - crashlytics: 19.0.0
    - analytics: 22.0.0
  
  com.squareup.retrofit2:
    - retrofit: 2.9.0
    - converter-kotlinx-serialization: 2.9.0
    - okhttp: 4.12.0
  
  com.google.android.material:
    - material: 1.12.0
  
  io.coil-kt:
    - coil-compose: 2.5.0
  
  com.google.accompanist:
    - permission-coil: 0.33.0
  
  androidx.lifecycle:
    - runtime-compose: 2.8.0
  
  io.coil-kt:
    - coil-network-legacy: 2.5.0
  
  com.jakewharton.rust:
    - android: 0.47.0
```

---

## 16. Next Steps

To proceed with Android implementation:

1. **Review and approve this plan**
2. **Confirm backend API stability**
3. **Confirm design system (Material You)**
4. **Confirm offline strategy**
5. **Approve development resources**
6. **Approve timeline**

**Alternative options:**
- Start with a simpler subset of features (Phases 1-5 only)
- Use cross-platform approach (Jetpack Compose Multiplatform for shared code)
- Start with Firebase as a PWA backend instead of REST API

---

## 17. Appendix

### A1. Color Palette Suggestion

```kotlin
// Material You Primary
ColorScheme(
    primary = Color(0xFF6750A4),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEADDFF),
    onPrimaryContainer = Color(0xFF311B92),
    secondary = Color(0xFF625B71),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE8DEF8),
    onSecondaryContainer = Color(0xFF414255),
    tertiary = Color(0xFF7D5260),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFD8E4),
    onTertiaryContainer = Color(0xFF542635)
)

// Secondary Container
ColorScheme(
    background = Color(0xFF1C1B1F),
    onBackground = Color.White,
    surface = Color(0xFF1C1B1F),
    onSurface = Color.White,
    surfaceContainer = Color(0xFF232226),
    onSurfaceVariant = Color(0xFFE3E0E8),
    onSurfaceVariant = Color(0xFF49454F),
    outline = Color(0xFF938F99)
)
```

### A2. API Rate Limits

- **Web:** 100 requests/minute (with auth)
- **Mobile:** 1000 requests/minute (with auth)
- **Rate limiting header:** `X-RateLimit-Limit`, `X-RateLimit-Remaining`

### A3. Version Compatibility

- **Minimum SDK:** API 24 (Android 7.0) for Jetpack Compose
- **Recommended SDK:** API 29 (Android 10) for modern features
- **Play Store policy:** Target API 30+ for best release chances

### A4. Testing Devices

- **Min:** Android 10 (API 29)
- **Recommended:** Android 12+ (API 31+)
- **Tested on:** Pixel devices + Android 10+ devices

### A5. Build Variants

```kotlin
buildTypes {
    release {
        proguardFiles("proguard-android-optimize.txt", "proguard-rules.pro")
        shrinkResources true
        shrinkCode true
        minifyEnabled true
    }
    
    debug {
        applicationIdSuffix ".debug"
        versionNameSuffix "-debug"
    }
}

productFlavors {
    prod {
        buildConfigField("String", "API_URL", "\"https://api.kharcha.com\"")
    }
    
    staging {
        buildConfigField("String", "API_URL", "\"https://staging-api.kharcha.com\"")
    }
}
```

---

**End of Android Frontend Implementation Plan**

**Next Step:** Please review and provide approval to proceed with Phase 1 implementation (Foundation setup).
