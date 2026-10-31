# Android Frontend Proposal — Kharcha Tracker

## Executive Summary

This document proposes converting the existing **Web-based Kharcha Tracker** into a **native Android Kotlin** application. The proposal is based on comprehensive analysis of:

1. **Blueprint** (`kharcha-tracker/kharcha-tracker.yaml`) - the canonical source of truth
2. **README.md** - current app state and features
3. **implementation_log.md** - complete implementation history

---

## 1. Current Architecture Analysis

### 1.1 Backend Service (Existing - No Changes)
| Component | Technology | Purpose |
|-----------|------------|---------|
| API Gateway | FastAPI (Python) | REST API, authentication, request handling |
| Storage Adapters | FlatFile/SQLite/PostgreSQL | Data persistence |
| Services | Pure Python modules | Auth, Groups, Expenses, Settlement, Notifications, Invitations |

**API Endpoints** (15+ routes across services):
- Auth: `POST /auth/login`, `POST /auth/signup`, `POST /auth/refresh`, `POST /auth/magic-link`, `POST /auth/magic-link/verify`
- Groups: `POST /groups`, `GET /groups`, `GET /groups/{id}`, `POST /groups/{id}/invite`, `POST /groups/{id}/invite/accept`, `GET /groups/{id}/members`, `POST /groups/{id}/invitations`, `GET /groups/{id}/invitations`, `DELETE /groups/{id}`, `DELETE /groups/{id}/members/me`, `POST /groups/{id}/expenses`, `GET /groups/{id}/expenses`, `GET /groups/{id}/settle`, `POST /groups/{id}/settle/me`, `GET /groups/{id}/settlement-rounds`, `GET /groups/{id}/payouts`, `POST /groups/{id}/payouts/{pid}/{action}`, `POST /groups/{id}/payments`
- Users: `GET /users/me`, `PATCH /users/me`, `GET /users/me/subscription`, `POST /subscription`
- Expenses: `POST /api/v1/expenses`, `GET /api/v1/expenses`, `GET /api/v1/expenses/{trans_id}`, `PATCH /api/v1/expenses/{trans_id}`, `DELETE /api/v1/expenses/{trans_id}`
- Settlement: `GET /groups/{id}/settle`, `POST /groups/{id}/settle/me`, `POST /groups/{id}/payouts/{pid}/{paid,settled,failed,cancel,remind,escalate,expenses}`, `POST /groups/{id}/payments`, `GET /users/me/payouts`
- Activity: `GET /api/v1/groups/{group_id}/activity`, `GET /api/v1/users/me/notifications`
- Activity (Root): `GET /api/v1/activity`
- Invitations: `GET /api/v1/users/me/invitations`, `POST /api/v1/invitations/{id}/accept`, `POST /api/v1/invitations/{id}/decline`

### 1.2 Frontend Web App (Existing - To Beported)
| Layer | Files | Purpose |
|-------|-------|---------|
| HTML | `frontend_service/index.html` | Single-page app with hash router, 6 views |
| CSS | `frontend_service/css/app.css` | Mobile-first styles, 3 breakpoints |
| JS | `frontend_service/js/api.js` | Token store, API fetch wrapper, error handling |
| JS | `frontend_service/js/app.js` | Router, handlers, all view logic |
| JS | `frontend_service/js/icons.js` | SVG icon set (20+ icons) |
| JS | `frontend_service/js/charts.js` | Pure JS chart builders (bar, pie) |

**Views** (Hash-based routing):
1. `#/login` - Login form (email + magic link)
2. `#/signup` - Signup form
3. `#/magic?token=...` - Magic link verification
4. `#/dashboard` - Groups + stats + quick actions
5. `#/groups` - Group cards list with actions
6. `#/expenses` - Expenses list + add form
7. `#/subscription` - Plan cards + status banner
8. `#/activity` - Activity timeline + audit log
9. `#/settings` - Profile form

### 1.3 Key Features Implemented
- **Authentication**: Login, signup, refresh tokens, magic-link SSO
- **User Management**: Profile (name, email, avatar), subscription (trial, plans)
- **Groups**: Create, join by invite code, invite by email, leave, delete, add members, list members
- **Expenses**: Add with EQUAL/PERCENT/FIXED/AUTO splits, edit (description/category/date/amount/payments), delete
- **Settlement**: Matrix-based matrix, preview, initiate, individual settlement, payout lifecycle (PENDING→PAID→SETTLED), remind, escalate, ad-hoc payments
- **Invitations**: Email invite (pending→accept/reject), declined invites
- **Notifications**: Badge, list, mark-read
- **Activity/Audit Log**: Group picker sorted by activity, date range filter, pagination, actor names
- **Dashboard**: Group summaries, expense totals, charts (bar + pie)

---

## 2. Android App Requirements

### 2.1 Platform Targets
| Device Type | Target | Rationale |
|-------------|--------|-----------|
| Mobile | 360px × 640px to 412px × 892px | Default mobile layouts |
| Tablet (Portrait) | 600px × 1004px to 768px × 1024px | Adjusted mobile layouts |
| Tablet (Landscape) | 1024px × 600px to 1280px × 800px | 3-column layouts |
| Desktop (Fallback) | 1280px+ | Full 4-column layouts |

### 2.2 Design Principles
1. **Mobile-First**: All layouts start mobile-friendly
2. **Responsive**: Scale to tablet and desktop
3. **Offline-Ready**: Cache data for offline access
4. **Push Notifications**: Real-time notifications via Firebase Cloud Messaging
5. **Dark/Light Mode**: System preference detection
6. **Accessibility**: TalkBack support, semantic markup

### 2.3 Key Modifications for Mobile
| Web Feature | Android Equivalent |
|-------------|-------------------|
| Hash router (`#view`) | NavGraph (BottomBar navigation) |
| CSS breakpoints | Resources qualifiers (`values/`, `sw600dp/`, `land/`) |
| Form inputs | `OutlinedTextField` |
| Cards | `Card` with elevation |
| Tables | `LazyRow` or `LazyColumn` (no full tables on mobile) |
| Modals | `Dialog` / `BottomSheet` |
| SVG icons | VectorDrawables (auto-scaled) |
| Inline actions | `ActionRow` / FAB (Floating Action Button) |
| Charts | MPAndroidChart (pre-built) |

---

## 3. Architecture Proposal

### 3.1 Project Structure
```
kharcha-tracker-android/
├── app/
│   └── src/
│       ├── main/
│       │   ├── java/com/kharcha/tracker/
│       │   │   ├── di/                 # Hilt modules
│       │   │   ├── ui/                 # ViewModels, Screens, Components
│       │   │   │   ├── auth/           # Auth, MagicLink
│       │   │   │   ├── users/          # Profile, Subscription
│       │   │   │   ├── groups/         # Groups, Members, Invitations
│       │   │   │   ├── expenses/       # Expenses, AddExpense
│   │   │   │   ├── settlement/         # Settlement, Payouts
│   │   │   │   ├── activity/           # ActivityLog, Audit
│   │   │   │   └── common/            # Shared, Extensions
│   │   │   └── network/                # API, TokenManager
│       │   ├── res/
│       │   │   ├── mipmap-hdpi/        # Icons
│   │   │   ├── values/                 # Strings, Colors, Themes
│   │   │   │   ├── themes.xml          # Light + Dark themes
│   │   │   │   └── strings.xml
│   │   │   ├── xml/
│   │   │   │   └── network_security_config.xml
│   │   │   └── drawable/
│   │   │       └── ic_* (VectorDrawables)
│   │   └── AndroidManifest.xml
│       └── test/
│           └── java/com/kharcha/tracker/
│
├── api-client/
│   └── src/
│       └── main/
│           └── java/com/kharcha/tracker/
│               └── api/
│                   ├── models/          # Pydantic models → POJOs
│                   └── service/         # Retrofit interfaces
│
├── storage/
│   └── src/
│       └── main/
│           └── java/com/kharcha/tracker/
│               └── storage/
│                   └── local/           # Room DB
│                   └── cloud/           # Sync logic
│
├── gradle/
│   └── wrapper/
│
├── build.gradle.kts
├── settings.gradle.kts
├── proguard-rules.pro
├── google-services.json                # For FCM (if needed)
└── local.properties                   # API URL override (for dev)
```

### 3.2 Tech Stack
| Layer | Technology | Rationale |
|-------|------------|---------|
| Language | Kotlin | Official Android language, modern features |
| UI Toolkit | Jetpack Compose | Declarative, modern, Material 3 |
| Architecture | MVVM + Clean Arch | Separation of concerns |
| DI | Hilt | Lightweight, auto-injection |
| Networking | Retrofit + OkHttp | Async HTTP calls |
| JSON Serialization | Moshi | Modern, type-safe |
| Local Storage | Room | SQLite wrapper, type-safe queries |
| Dependency Injection | Hilt | Integrated with Jetpack Compose |
| Navigation | Navigation Compose | Type-safe nav args |
| Networking | Retrofit + OkHttp | Async HTTP calls |
| JSON | Moshi | Modern, type-safe |
| Local Storage | Room | SQLite wrapper |
| Push Notifications | Firebase FCM | Reliable push |
| Charts | MPAndroidChart | Lightweight, Android-native |

### 3.3 Key Libraries
| Category | Library | Version | Usage |
|----------|---------|---------|-------|
| UI | `androidx.activity.compose:activity-compose` | 1.9.0 | Activity lifecycle |
| | `androidx.compose.material3:material3` | 1.3.0 | Material 3 theming |
| | `androidx.compose.material:material-icons-extended` | 1.7.0 | Iconography |
| Architecture | `androidx.lifecycle:lifecycle-runtime-compose` | 2.8.0 | Compose lifecycle |
| Networking | `com.squareup.retrofit2:retrofit` | 2.11.0 | HTTP client |
| | `com.squareup.okhttp3:okhttp` | 4.12.0 | HTTP engine |
| | `com.jakewhartor.retrofit:retrofit-kotlinx-serialization-converter` | N/A | KotlinX JSON |
| JSON | `com.squareup.moshi:moshi-kotlin` | 1.15.0 | Type-safe JSON |
| Local DB | `androidx.room:room-runtime` | 2.6.1 | SQLite wrapper |
| | `androidx.room:room-compiler` | 2.6.1 | Code generation |
| DI | `com.google.dagger.hilt-android` | 2.54 | DI framework |
| | `androidx.hilt:hilt-navigation-compose` | 1.2.0 | Nav injection |
| Navigation | `androidx.navigation:navigation-compose` | 2.8.0 | Type-safe nav |
| Image Load | `co.coil-kt:coil-compose` | 2.6.0 | Image loading |
| Push | `com.google.firebase:firebase-messaging-ktx` | 24.1.0 | FCM binding |
| Charts | `com.github.PhilJay:MPAndroidChart` | 3.1.0 | Charts |
| Coroutines | `org.jetbrains.kotlinx:kotlinx-coroutines-android` | 1.9.0 | Async |
| Preferences | `androidx.datastore:datastore-preferences` | 1.1.1 | Secure preferences |

---

## 4. Data Model Mapping (Web → Android)

### 4.1 Pydantic Models → Kotlin POJOs + Data Classes

| Web Model | Android Equivalent | Notes |
|-----------|-------------------|-------|
| `User` | `User` (data class) | 1:1 mapping |
| `UserRecord` | `UserRecord` (data class + Room Entity) | DB entity |
| `Group` | `Group` (data class) | 1:1 mapping |
| `GroupRecord` | `GroupRecord` (data class + Room Entity) | DB entity |
| `Expense` | `Expense` (data class) | 1:1 mapping |
| `ExpenseRecord` | `ExpenseRecord` (data class + Room Entity) | DB entity |
| `ExpensePayment` | `ExpensePayment` (data class) | 1:1 mapping |
| `SettlementRound` | `SettlementRound` (data class) | 1:1 mapping |
| `Payout` | `Payout` (data class) | 1:1 mapping |
| `Invitation` | `Invitation` (data class) | 1:1 mapping |
| `AuditLog` | `AuditLog` (data class) | 1:1 mapping |
| `Notification` | `Notification` (data class) | 1:1 mapping |
| `Base` (Auth) | `AuthResponse` (sealed interface) | Login/Refresh/Magic |
| `InvitationRecord` | `InvitationRecord` (Room Entity) | DB entity |
| `InviteCode` | `InviteCode` (data class) | Derived, computed |

### 4.2 Enum Mappings
```kotlin
enum class AuthStatus { NONE, ACTIVE }

enum class Plan { NONE, MONTHLY, SIX_MONTH, YEARLY, LIFETIME }
enum class PlanStatus { NONE, ACTIVE }
enum class InvitationStatus { PENDING, ACCEPTED, REJECTED, EXPIRED }
enum class GroupStatus { OPEN, LOCKED, SETTLED, PARTIAL_SETTLEMENT }
enum class ExpenseCategory { MEALS, TRANSPORT, QUARTERLY, TRAVEL, ENTERTAINMENT, EQUIPMENT, ACCOMMODATION, MEDICAL, PROFESSIONAL, OTHER, SETTLEMENT } // SETTLEMENT is legacy
enum class ActivityKind { CREATE, UPDATE, DELETE, LOCK, UNLOCK, ACCEPT_INVITE, REJECT_INVITE, JOIN, LEAVE, ADD_MEMBER, REMOVE_MEMBER, EXPIRE_INVITE, ADHOC_PAYMENT, EXPENSE, EXPENSE_UPDATE, EXPENSE_DELETE, PAYMENT }
enum class PayoutStatus { PENDING, PAID, SETTLED, FAILED, CANCELLED }
enum class NotificationKind { NEW_MESSAGE, GROUP_CREATED, GROUP_JOINED, GROUP_LEFT, GROUP_LOCKED, GROUP_UNLOCKED, GROUP_DELETED, EXPENSE, EXPENSE_UPDATE, EXPENSE_DELETE, PAYMENT, REJECT_INVITE, ACCEPT_INVITE, GROUP_INVITE_ACCEPTED, GROUP_INVITE_DECLINED, GROUP_DELETED_SELF, PAYMENT_REMINDER, PAYMENT_ESCALATION, SUBSCRIPTION_UPDATE }
```

### 4.3 Room Database Schema

```
User ─── UserRecord
│
├── Groups
│   ├── GroupMemberships (user_id, group_id)
│   ├── GroupsMembers (user_id, group_id, name, email, avatar_url, role)
│   ├── Invitations
│   ├── Expenses
│   ├── ExpensePayments
│   ├── SettlementRounds
│   ├── Payouts
│   └── AuditLogs
│
└── Subscription
    └── Invitations
```

### 4.4 API Service Interface (Retrofit)

```kotlin
interface ApiService {
    // Authentication
    @POST("auth/login")
    suspend fun login(@Body credentials: LoginRequest): AuthResponse
    
    @POST("auth/signup")
    suspend fun signup(@Body request: SignupRequest): AuthResponse
    
    @POST("auth/refresh")
    suspend fun refresh(@Body refreshToken: String): AuthResponse
    
    @POST("auth/magic-link")
    suspend fun requestMagicLink(@Body email: String): MagicLinkResponse
    
    @POST("auth/magic-link/verify")
    suspend fun verifyMagicLink(@Body token: String): AuthResponse
    
    // Users
    @GET("users/me")
    suspend fun getCurrentUser(): User
    
    @PATCH("users/me")
    suspend fun updateUser(@Body update: UserUpdate): User
    
    @GET("users/me/subscription")
    suspend fun getSubscriptionStatus(): SubscriptionStatus
    
    @POST("subscription")
    suspend fun activatePlan(@Body request: ActivatePlanRequest): SubscriptionStatus
    
    // Groups
    @GET("groups")
    suspend fun listGroups(
        @Query("sort") sort: String?,
        @Query("offset") offset: Int? = null,
        @Query("limit") limit: Int? = null
    ): List<Group>
    
    @POST("groups")
    suspend fun createGroup(@Body request: CreateGroupRequest): Group
    
    @GET("groups/{id}")
    suspend fun getGroup(@Path("id") id: String): Group
    
    @POST("groups/{id}/invite")
    suspend fun generateInviteCode(@Path("id") id: String): InviteCode
    
    @POST("groups/{id}/invite/accept")
    suspend fun joinGroup(@Path("id") id: String, @Body inviteCode: String): Group
    
    @POST("groups/{id}/invitations")
    suspend fun inviteMember(@Path("id") id: String, @Body request: InviteMemberRequest): Invitation
    
    @GET("groups/{id}/invitations")
    suspend fun listInvitations(@Path("id") id: String): List<Invitation>
    
    @DELETE("groups/{id}")
    suspend fun deleteGroup(@Path("id") id: String): Unit
    
    @DELETE("groups/{id}/members/me")
    suspend fun leaveGroup(@Path("id") id: String): Unit
    
    @GET("groups/{id}/members")
    suspend fun listGroupMembers(@Path("id") id: String): List<GroupMember>
    
    @POST("groups/{id}/members")
    suspend fun addMember(@Path("id") id: String, @Body email: String): Unit
    
    @GET("groups/{id}/expenses")
    suspend fun listGroupExpenses(
        @Path("id") id: String,
        @Query("offset") offset: Int? = null,
        @Query("limit") limit: Int? = null
    ): List<Expense>
    
    // Expenses
    @POST("expenses")
    suspend fun addExpense(@Body request: ExpenseRequest): Expense
    
    @GET("expenses")
    suspend fun listExpenses(
        @Query("group_id") groupId: String?,
        @Query("trans_id") transId: String?,
        @Query("offset") offset: Int? = null,
        @Query("limit") limit: Int? = null
    ): List<Expense>
    
    @GET("expenses/{trans_id}")
    suspend fun getExpense(@Path("trans_id") transId: String): Expense
    
    @PATCH("expenses/{trans_id}")
    suspend fun updateExpense(@Path("trans_id") transId: String, @Body request: ExpenseUpdate): Expense
    
    @DELETE("expenses/{trans_id}")
    suspend fun deleteExpense(@Path("trans_id") transId: String): Unit
    
    // Settlement
    @GET("groups/{id}/settle")
    suspend fun previewSettlement(@Path("id") id: String): SettlementPreview
    
    @POST("groups/{id}/settle/me")
    suspend fun settleMyDues(@Path("id") id: String): SettlementRound
    
    @GET("groups/{id}/settlement-rounds")
    suspend fun listSettlementRounds(
        @Path("id") id: String,
        @Query("offset") offset: Int? = null,
        @Query("limit") limit: Int? = null
    ): List<SettlementRound>
    
    @GET("groups/{id}/payouts")
    suspend fun listPayouts(
        @Path("id") id: String,
        @Query("offset") offset: Int? = null,
        @Query("limit") limit: Int? = null
    ): List<Payout>
    
    @POST("groups/{id}/payouts/{pid}/{action}")
    suspend fun performPayoutAction(
        @Path("id") id: String,
        @Path("pid") pid: String,
        @Path("action") action: String
    ): Payout
    
    @POST("groups/{id}/payments")
    suspend fun recordPayment(
        @Path("id") id: String,
        @Body request: RecordPaymentRequest
    ): Payout
    
    @GET("users/me/payouts")
    suspend fun listUserPayouts(
        @Query("offset") offset: Int? = null,
        @Query("limit") limit: Int? = null
    ): List<Payout>
    
    // Activity
    @GET("groups/{id}/activity")
    suspend fun getGroupActivity(
        @Path("id") id: String,
        @Query("date_from") dateFrom: String?,
        @Query("date_to") dateTo: String?,
        @Query("offset") offset: Int? = null,
        @Query("limit") limit: Int? = null
    ): List<AuditLog>
    
    @GET("activity")
    suspend fun getRootActivity(
        @Query("date_from") dateFrom: String?,
        @Query("date_to") dateTo: String?,
        @Query("offset") offset: Int? = null,
        @Query("limit") limit: Int? = null,
        @Query("group_id") groupId: String? = null
    ): List<AuditLog>
    
    // Notifications
    @GET("users/me/notifications")
    suspend fun getNotifications(
        @Query("unread_only") unreadOnly: Boolean? = null,
        @Query("offset") offset: Int? = null,
        @Query("limit") limit: Int? = null
    ): List<Notification>
    
    @POST("users/me/notifications/read")
    suspend fun markAllNotificationsRead()
}
```

---

## 5. UI Screens (Mobile-First)

### 5.1 Splash Screen
```kotlin
@Composable
fun SplashScreen(
    onComplete: () -> Unit,
    modifier: Modifier = Modifier
)
```
- Brand logo
- "Loading..." indicator
- Theme detection

### 5.2 Login Screen
```kotlin
@Composable
fun LoginScreen(
    onLogin: (LoginRequest) -> Unit,
    onMagicLinkRequested: (String) -> Unit,
    onBackToSignup: () -> Unit,
    modifier: Modifier = Modifier
)
```
- Email input
- Password input
- "Login" button
- "Continue with email" button (magic link)
- "Don't have an account? Sign up"

### 5.3 Signup Screen
```kotlin
@Composable
fun SignupScreen(
    onSignup: (SignupRequest) -> Unit,
    modifier: Modifier = Modifier
)
```
- Name input
- Email input
- Password input (with strength indicator)
- Confirm password
- "Sign up" button
- "Already have an account? Login"

### 5.4 Magic Link Screen
```kotlin
@Composable
fun MagicLinkScreen(
    onVerify: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
)
```
- "Enter magic code" input
- "Verify Code" button
- Hint: "We sent the magic link to your email"

### 5.5 Dashboard Screen
```kotlin
@Composable
fun DashboardScreen(
    onNavigateTo: (String) -> Unit,
    modifier: Modifier = Modifier
)
```
- Welcome greeting
- Stat cards (groups count, invites, etc.)
- Subscription status banner
- Quick group selector
- Recent activity preview
- Floating action button (New Group)

### 5.6 Groups Screen (Main)
```kotlin
@Composable
fun GroupsScreen(
    onGroupSelected: (String) -> Unit,
    onAddGroup: () -> Unit,
    onInviteGroup: (String) -> Unit,
    onJoinGroup: (InviteCode) -> Unit,
    onJoinInvitation: (Invitation) -> Unit,
    onLeaveGroup: (String) -> Unit,
    onSettleUp: (String) -> Unit,
    modifier: Modifier = Modifier
)
```
- "My Groups" header
- Group cards list (name, currency, members count, status, expenses, settlement)
- Each card:
  - Name + currency
  - Member count (chip)
  - Actions: New code, Copy, Invite email, Expenses, Settle up
  - Delete/Leave (owner only)
- FAB: Add new group

### 5.7 Expense Screen (Per Group)
```kotlin
@Composable
fun ExpenseScreen(
    expenses: List<Expense>,
    onAddExpense: () -> Unit,
    onEditExpense: (Expense) -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
)
```
- Group name header
- FAB: Add expense
- Expense list (scrollable)
  - Date, category, amount, payer(s)
  - Actions: Edit, Delete
- Edit expense modal

### 5.8 Add/Edit Expense Modal
```kotlin
@Composable
fun AddExpenseModal(
    onSave: (Expense) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
)
```
- Description input
- Amount input
- Category dropdown
- Date picker
- Payer selection
- Split mode: Equal / Custom (slider for each member)
- "Add" button

### 5.9 Settlement Screen (Per Group)
```kotlin
@Composable
fun SettlementScreen(
    onPreview: () -> Unit,
    onSettleMyDues: () -> Unit,
    onSettlePeriodic: () -> Unit,
    onFinalSettle: () -> Unit,
    onRecordPayment: (Payment) -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
)
```
- Group status header
- Net dues table (You vs Others)
- Open payout list
  - Payor, Payee, Amount, Status, Actions
  - Action buttons (based on status): Mark Paid, Confirm, Remind, Fail, Cancel, Escalate
- Ad-hoc payment form
- Settlement summary

### 5.10 Subscription Screen
```kotlin
@Composable
fun SubscriptionScreen(
    modifier: Modifier = Modifier
)
```
- Current plan status card
  - Plan name, status icon
  - Trial end date (if applicable)
  - "Unlimited" or entries remaining
- Plan cards
  - Monthly, 6 Month, Yearly, Lifetime
  - Price + benefits
  - "Subscribe" button
- Description and plan details

### 5.11 Invitations Screen
```kotlin
@Composable
fun InvitationsScreen(
    onAccept: (String) -> Unit,
    onDecline: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
)
```
- Invite from: name, email, group name
- Invite code
- Status indicator
- Accept/Decline buttons

### 5.12 Activity Screen
```kotlin
@Composable
fun ActivityScreen(
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
)
```
- Date range picker
- Group picker (sorted by recent activity)
- Activity feed
  - Timestamp, kind pill, actor, details
  - Events: Created group, Joined, Added member, etc.
- Pagination controls (Prev/Next)
- "Load more" indicator

### 5.13 Notifications Screen
```kotlin
@Composable
fun NotificationsScreen(
    modifier: Modifier = Modifier
)
```
- Notification list
  - Type icon, time, content, unread dot
- Date separators
- Refresh button

### 5.14 Settings Screen
```kotlin
@Composable
fun SettingsScreen(
    onLogout: () -> Unit,
    modifier: Modifier = Modifier
)
```
- Profile section
  - Name, email, avatar (editable)
  - Subscription status
- Preferences section
  - Language
  - Theme (system/light/dark)
  - Push notifications toggle
- About section
  - Version
  - "Terms of Service" link
  - "Privacy Policy" link
- Logout button

### 5.15 Root Nav Graph
```kotlin
val navGraph = composedNavHost(
    navController = navController,
    startDestination = "auth"
) {
    // Auth screens
    navigate("login") -> LoginScreen
    navigate("signup") -> SignupScreen
    navigate("magic") -> MagicLinkScreen
    
    // Authenticated screens
    navigate("dashboard") -> DashboardScreen
    navigate("groups") -> GroupsScreen
    navigate("expenses") -> ExpenseScreen
    navigate("settlement") -> SettlementScreen
    navigate("subscription") -> SubscriptionScreen
    navigate("invitations") -> InvitationsScreen
    navigate("activity") -> ActivityScreen
    navigate("notifications") -> NotificationsScreen
    navigate("settings") -> SettingsScreen
}
```

---

## 6. Navigation Structure

### 6.1 Bottom Bar (Authenticated, Mobile/Tablet Portrait)
```
┌─────────────────────────────────┐
│  [Groups] [Expenses] [Settle] [✓]│
│                                 │
│  Dashboard                       │
│  ┌───────────────────────────┐  │
│  │ Welcome, John             │  │
│  │ [Stats...]                │  │
│  └───────────────────────────┘  │
│                                 │
│  ┌───────────────────────────┐  │
│  │ Group 1                   │  │
│  │ ──────────────             │  │
│  │ Expenses / Settle / New +  │  │
│  └───────────────────────────┘  │
│                                 │
│  ┌───────────────────────────┐  │
│  │ Group 2                   │  │
│  │ ──────────────             │  │
│  │ Expenses / Settle / New +  │  │
│  └───────────────────────────┘  │
│                                 │
│  [Bell](badge) [Settings]       │
└─────────────────────────────────┘
```

### 6.2 Bottom Bar (Tablet Landscape, Desktop)
```
┌─────────────────────────────────┐
│ [≡] [Groups] [Expenses] [Settle] │
│                                 │
│  Dashboard                       │
│  ┌─────────────────────────────┐│
│  │ ──────────────               ││
│  │ [Topbar]         [X] [Y]     ││
│  │ ──────────────               ││
│  │ [Card...]                   ││
│  └─────────────────────────────┘│
│                                 │
│  [Bell] [Settings]               │
└─────────────────────────────────┘
```

### 6.3 Tabs (Groups Screen)
```
┌─────────────────────────────────┐
│ [≡] [Groups] [Expenses] [Settle] │
│                                 │
│  Group 1                        │
│  ┌─────────────────────────────┐│
│  │ Owner: ...   |  |  |  |     ││
│  │ ──────────────               ││
│  │ [Expenses] [Settle] [New +]  ││
│  └─────────────────────────────┘│
│                                 │
│  Group 2                        │
│  ┌─────────────────────────────┐│
│  └─────────────────────────────┘│
│                                 │
│  [Bell] [Settings]              │
└─────────────────────────────────┘
```

### 6.4 Tabs (Expenses Screen)
```
┌─────────────────────────────────┐
│ [≡] [Groups] [Expenses] [Settle] │
│                                 │
│  Group 1                        │
│  ┌─────────────────────────────┐│
│  │ Expense 1                   ││
│  │ ──────────────               ││
│  │ [Edit] [Delete]             ││
│  │ ──────────────               ││
│  │ Expense 2                   ││
│  └─────────────────────────────┘│
│                                 │
│  ┌─────────────────────────────┐│
│  │ Description | Amount        ││
│  │ ──────────────               ││
│  │ [Category ▼] [Payer ▼]      ││
│  │ [Equal Split]               ││
│  │ ──────────────               ││
│  │ [Add Expense]               ││
│  └─────────────────────────────┘│
│                                 │
│  [Bell] [Settings]              │
└─────────────────────────────────┘
```

### 6.5 Settlement Panel
```
┌─────────────────────────────────┐
│ [≡] [Groups] [Expenses] [Settle] │
│                                 │
│  ┌─────────────────────────────┐│
│  │ Net Dues (You: 500, Others: )││
│  │ ──────────────               ││
│  │ [Settle My Dues]            ││
│  └─────────────────────────────┘│
│                                 │
│  ┌─────────────────────────────┐│
│  │ Payout: Alice → Bob         ││
│  │ ──────────────               ││
│  │ 500.00                      ││
│  │ [Pending] [Confirm]         ││
│  └─────────────────────────────┘│
│                                 │
│  ┌─────────────────────────────┐│
│  │ Payout: Charlie → You      ││
│  │ ──────────────               ││
│  │ 500.00                      ││
│  │ [Pending] [Confirm]         ││
│  └─────────────────────────────┘│
│                                 │
│  ┌─────────────────────────────┐│
│  │ Ad-Hoc: Bob → Dave         ││
│  │ ──────────────               ││
│  │ [Add]                       ││
│  └─────────────────────────────┘│
│                                 │
│  [Bell] [Settings]              │
└─────────────────────────────────┘
```

---

## 7. Storage Architecture

### 7.1 Local Storage (Room)

```kotlin
// Entities
@Entity(tableName = "user_record")
data class UserEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "email") val email: String,
    @ColumnInfo(name = "avatar_url") val avatarUrl: String? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "trial_ends_at") val trialEndsAt: Long? = null,
    @ColumnInfo(name = "plan") val plan: String,
    @ColumnInfo(name = "plan_status") val planStatus: String,
    @ColumnInfo(name = "plan_ends_at") val planEndsAt: Long? = null,
    @ColumnInfo(name = "limited_entries") val limitedEntries: Int = 0,
    @ColumnInfo(name = "is_deleted") val isDeleted: Boolean = false,
    @ColumnInfo(name = "deleted_at") val deletedAt: Long? = null
)

@Entity(tableName = "group_record")
data class GroupEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "currency") val currency: String,
    @ColumnInfo(name = "status") val status: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "locked_at") val lockedAt: Long? = null,
    @ColumnInfo(name = "unlocked_at") val unlockedAt: Long? = null,
    @ColumnInfo(name = "deleted_at") val deletedAt: Long? = null,
    @ColumnInfo(name = "invite_code") val inviteCode: String? = null,
    @ColumnInfo(name = "locked_by") val lockedBy: String? = null,
    @ColumnInfo(name = "unlocked_by") val unlockedBy: String? = null,
    @ColumnInfo(name = "deleted_by") val deletedBy: String? = null,
    @ColumnInfo(name = "owner_id") val ownerId: String = "",
    @ColumnInfo(name = "is_deleted") val isDeleted: Boolean = false
)

@Entity(tableName = "expense_record")
data class ExpenseEntity(
    @PrimaryKey(autoGenerate = true) val id: Int,
    @ColumnInfo(name = "trans_id") val transId: String,
    @ColumnInfo(name = "group_id") val groupId: String,
    @ColumnInfo(name = "expn_desc") val expnDesc: String,
    @ColumnInfo(name = "expn_ctgry") val expnCtgry: String,
    @ColumnInfo(name = "date_time") val dateTime: Long,
    @ColumnInfo(name = "expn_amnt") val expnAmnt: Int,
    @ColumnInfo(name = "split_type") val splitType: String,
    @ColumnInfo(name = "expn_splt") val expnSplt: String,
    @ColumnInfo(name = "is_deleted") val isDeleted: Boolean = false,
    @ColumnInfo(name = "deleted_at") val deletedAt: Long? = null,
    @ColumnInfo(name = "deleted_by") val deletedBy: String? = null
)

@Entity(tableName = "payout_record")
data class PayoutEntity(
    @PrimaryKey(autoGenerate = true) val id: Int,
    @ColumnInfo(name = "payor_id") val payorId: String,
    @ColumnInfo(name = "payee_id") val payeeId: String,
    @ColumnInfo(name = "payor_share") val payorShare: Int,
    @ColumnInfo(name = "payee_share") val payeeShare: Int,
    @ColumnInfo(name = "status") val status: String,
    @ColumnInfo(name = "paid_at") val paidAt: Long? = null,
    @ColumnInfo(name = "settled_at") val settledAt: Long? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "failed_at") val failedAt: Long? = null,
    @ColumnInfo(name = "reminder_sent_at") val reminderSentAt: Long? = null,
    @ColumnInfo(name = "escalated_at") val escalatedAt: Long? = null,
    @ColumnInfo(name = "is_deleted") val isDeleted: Boolean = false,
    @ColumnInfo(name = "deleted_at") val deletedAt: Long? = null,
    @ColumnInfo(name = "deleted_by") val deletedBy: String? = null,
    @ColumnInfo(name = "replaces_id") val replacesId: String? = null,
    @ColumnInfo(name = "payout_expense_ids") val payoutExpenseIds: String? = null,
    @ColumnInfo(name = "linked_by") val linkedBy: String? = null,
    @ColumnInfo(name = "linked_at") val linkedAt: Long? = null
)

@Entity(tableName = "settlement_round_record")
data class SettlementRoundEntity(
    @PrimaryKey(autoGenerate = true) val id: Int,
    @ColumnInfo(name = "group_id") val groupId: String,
    @ColumnInfo(name = "is_member_specific") val isMemberSpecific: Boolean,
    @ColumnInfo(name = "round_num") val roundNum: Int,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "user_id") val userId: String?,
    @ColumnInfo(name = "status") val status: String,
    @ColumnInfo(name = "completed_at") val completedAt: Long? = null,
    @ColumnInfo(name = "completed_by") val completedBy: String?,
    @ColumnInfo(name = "is_deleted") val isDeleted: Boolean = false,
    @ColumnInfo(name = "deleted_at") val deletedAt: Long? = null,
    @ColumnInfo(name = "deleted_by") val deletedBy: String? = null
)

@Entity(tableName = "invitation_record")
data class InvitationEntity(
    @PrimaryKey(autoGenerate = true) val id: Int,
    @ColumnInfo(name = "group_id") val groupId: String,
    @ColumnInfo(name = "inviter_id") val inviterId: String,
    @ColumnInfo(name = "invitee_email") val inviteeEmail: String,
    @ColumnInfo(name = "invite_code") val inviteCode: String,
    @ColumnInfo(name = "status") val status: String,
    @ColumnInfo(name = "expires_at") val expiresAt: Long,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "is_deleted") val isDeleted: Boolean = false,
    @ColumnInfo(name = "deleted_at") val deletedAt: Long? = null,
    @ColumnInfo(name = "deleted_by") val deletedBy: String? = null
)

@Entity(tableName = "audit_log")
data class AuditLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Int,
    @ColumnInfo(name = "entity_type") val entityType: String,
    @ColumnInfo(name = "entity_id") val entityId: String,
    @ColumnInfo(name = "group_id") val groupId: String? = null,
    @ColumnInfo(name = "actor_id") val actorId: String?,
    @ColumnInfo(name = "actor_name") val actorName: String? = null,
    @ColumnInfo(name = "kind") val kind: String,
    @ColumnInfo(name = "old_value") val oldValue: String? = null,
    @ColumnInfo(name = "new_value") val newValue: String? = null,
    @ColumnInfo(name = "new_amount") val newAmount: Int? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "is_deleted") val isDeleted: Boolean = false,
    @ColumnInfo(name = "deleted_at") val deletedAt: Long? = null,
    @ColumnInfo(name = "deleted_by") val deletedBy: String? = null
)

@Entity(tableName = "notification_record")
data class NotificationEntity(
    @PrimaryKey(autoGenerate = true) val id: Int,
    @ColumnInfo(name = "user_id") val userId: String,
    @ColumnInfo(name = "kind") val kind: String,
    @ColumnInfo(name = "message") val message: String,
    @ColumnInfo(name = "is_read") val isRead: Boolean = false,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "is_deleted") val isDeleted: Boolean = false,
    @ColumnInfo(name = "deleted_at") val deletedAt: Long? = null,
    @ColumnInfo(name = "deleted_by") val deletedBy: String? = null
)

// Group Memberships (junction table)
@Entity(tableName = "group_memberships")
data class GroupMembershipEntity(
    @PrimaryKey(autoGenerate = true) val id: Int,
    @ColumnInfo(name = "user_id") val userId: String,
    @ColumnInfo(name = "group_id") val groupId: String,
    @ColumnInfo(name = "role") val role: String = "MEMBER",
    @ColumnInfo(name = "joined_at") val joinedAt: Long = 0,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "is_deleted") val isDeleted: Boolean = false,
    @ColumnInfo(name = "deleted_at") val deletedAt: Long? = null,
    @ColumnInfo(name = "deleted_by") val deletedBy: String? = null
)
```

### 7.2 Sync Strategy

```kotlin
sealed class SyncOperation {
    data class Upload(val localChanges: List<Change>) : SyncOperation()
    data class Download(val remoteChanges: List<Change>) : SyncOperation()
    data class Conflict(val local: Change, val remote: Change) : SyncOperation()
}

object SyncManager {
    fun sync(): Result<Unit> {
        return try {
            // Upload local changes (offline → server)
            // Download remote changes (server → local)
            // Merge conflicts (last-write-wins with conflict markers)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
```

### 7.3 Sync Flow

```
┌─────────────┐      ┌─────────────┐      ┌─────────────┐
│   Local DB  │ ←─── │ Sync Service │ ←─── │   Remote API│
│   (Room)    │      │             │      │   (Retrofit)│
└─────────────┘      └─────────────┘      └─────────────┘
        │                   │                    │
        │    Upload Queue    │                    │
        │                   │    Download Queue   │
        │                   │                    │
        └───────────────────┘                    │
                                                │
         [Last Sync Time]                      [Offline]
```

---

## 8. Networking Layer

### 8.1 TokenManager
```kotlin
class TokenManager @Inject constructor(
    private val context: Context
) {
    private val prefs: SharedPreferences by lazy {
        ContextCompat.getSharedPrefs(context)
    }
    
    val accessToken: String?
        get() = prefs.getString(AccessTokenKey, null)
    
    val refreshToken: String?
        get() = prefs.getString(RefreshTokenKey, null)
    
    val tokenExpiry: Date?
        get() {
            val json = prefs.getString("kharcha_token_json", null) ?: return null
            return try {
                val data = Moshi.Builder().add(KotlinxJsonJsonAdapter()).build().parse(json, Map::class.java)
                val exp = data["exp"]?.value?.toString()?.toLongOrNull()?.div(1000)
                Date(exp)
            } catch (e: Exception) {
                null
            }
        }
    
    fun saveSession(authResponse: AuthResponse) {
        prefs.edit().apply {
            putString(AccessTokenKey, authResponse.data?.accessToken)
            putString(RefreshTokenKey, authResponse.data?.refreshToken)
            putString("kharcha_token_json", Moshi.Builder().build().toJson(authResponse.data))
        }.apply()
    }
    
    fun logout() {
        prefs.edit().clear().apply()
    }
    
    fun isExpired(): Boolean {
        return tokenExpiry == null || tokenExpiry.time <= System.currentTimeMillis() - TOKEN_EXPIRY_WINDOW_MS
    }
}
```

### 8.2 ApiClient
```kotlin
@KonanClient
class ApiClient @Inject constructor(
    private val tokenManager: TokenManager,
    private val baseUrl: String
) {
    private val http = OkHttpClient.Builder()
        .addInterceptor(AuthInterceptor(tokenManager))
        .retryOnConnectionFailure(true)
        .build()
    
    private val retrofit = Retrofit.Builder()
        .baseUrl(baseUrl)
        .addConverterFactory(MoshiConverterFactory.create())
        .client(http)
        .build()
    
    val apiService: ApiService = retrofit.create(ApiService::class.java)
    
    suspend fun apiCall(endpoint: String): Result<Response<*>?> {
        return try {
            // Add token if auth endpoint
            val token = tokenManager.accessToken
            val request = Request.Builder().url(endpoint)
            token?.let {
                request.header("Authorization", "Bearer $it")
            }
            // Execute
            val response = http.newCall(request.build()).execute()
            // Check for 401 and refresh
            if (response.code == 401) {
                val refreshResponse = apiService.refresh(tokenManager.refreshToken!!)
                if (refreshResponse.isSuccessful) {
                    tokenManager.saveSession(refreshResponse.body()!!)
                } else {
                    // Session expired
                    tokenManager.logout()
                    return Result.failure(SessionExpiredException())
                }
            }
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
```

---

## 9. Implementation Phases

### Phase 1: Foundation
- Set up project structure (Jetpack Compose + Hilt + Retrofit + Room + Navigation)
- Configure build.gradle.kts dependencies
- Create shared constants (Plans, AuthStatus, GroupStatus, etc.)
- Setup TokenManager
- Implement basic auth flows (login, signup, magic-link)

### Phase 2: User Management
- User profile screen
- Subscription screen with plan cards
- Sync logic for user data

### Phase 3: Groups
- Groups list screen (bottom bar tab)
- Group detail screen (implied)
- Group creation flow
- Group deletion confirmation
- Member management (add, leave)
- Invite flows (email invite, join by code)

### Phase 4: Expenses
- Expense screen (per group)
- Add expense flow
- Edit expense modal
- Expense list
- Payment management

### Phase 5: Settlement
- Settlement screen (per group)
- Preview settlement
- Settlement initiation (individual, periodic, final)
- Payout management
- Ad-hoc payment flow
- Link expenses flow

### Phase 6: Invitations & Notifications
- Invitations screen
- Notification panel (top bar)
- Notification list
- Push notification handling

### Phase 7: Activity & Audit
- Activity screen
- Date range filtering
- Group picker
- Event table
- Pagination

### Phase 8: Dashboard & Charts
- Dashboard screen
- Expense summary cards
- Bar chart (spending by category)
- Pie chart (category breakdown)
- Date range selector

### Phase 9: Offline Sync & Refinement
- Complete offline mode
- Sync conflict resolution
- Push notification integration
- UI refinements
- Accessibility audit

---

## 10. Project Setup Commands

### 10.1 Android Studio / Gradle
```bash
# New project
android studio create project ...

# OR clone and open
git clone <repository>
cd kharcha-tracker-android
# Open in Android Studio as existing project
```

### 10.2 Dependencies (app/build.gradle.kts)
```kotlin
dependencies {
    // Jetpack Compose
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation(platform("androidx.compose:compose-bom:2024.04.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.material:material-icons-core")
    
    // Lifecycle
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.0")
    
    // Navigation
    implementation("androidx.navigation:navigation-compose:2.8.0")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")
    
    // Networking
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.jakewhartor.retrofit:retrofit-kotlinx-serialization-converter:0.8.0")
    implementation("com.squareup.moshi:moshi-kotlin:1.15.0")
    implementation("com.squareup.moshi:moshi-kotlin-codegen:1.15.0")
    
    // Local Storage
    implementation("androidx.room:room-runtime:2.6.1")
    annotationProcessor("androidx.room:room-compiler:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    
    // DI
    implementation("com.google.dagger:hilt-android:2.54")
    kapt("com.google.dagger:hilt-compiler:2.54")
    implementation("androidx.hilt:hilt-navigation:1.2.0")
    
    // Images
    implementation("co.coil-kt:coil-compose:2.6.0")
    
    // Push
    implementation("com.google.firebase:firebase-bom:33.1.0")
    implementation("com.google.firebase:firebase-messaging-ktx")
    implementation("com.google.firebase:firebase-analytics-ktx")
    
    // Charts
    implementation("com.github.PhilJay:MPAndroidChart:v3.1.0")
    
    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.9.0")
    
    // DataStore
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    
    // Date
    implementation("androidx.core:core-ktx:1.13.0")
    implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.5.0")
    
    // Other
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.0")
    implementation("androidx.activity:activity:1.9.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.core:core:1.13.0")
    implementation("androidx.constraintlayout:constraintlayout:2.2.0")
    implementation("com.google.android.material:material:1.12.0")
    
    // Testing
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
}
```

---

## 11. Timeline Estimate

| Phase | Duration | Deliverables |
|-------|----------|--------------|
| 1. Foundation | 3-4 weeks | Project structure, auth, first login |
| 2. User Management | 2-3 weeks | Profile, subscription, sync |
| 3. Groups | 3-4 weeks | CRUD, members, invites |
| 4. Expenses | 4-5 weeks | Full expense management |
| 5. Settlement | 5-6 weeks | Complex matrix logic, payouts |
| 6. Invitations & Notifications | 2-3 weeks | Push, badges, real-time |
| 7. Activity & Audit | 2-3 weeks | Timeline, filtering, pagination |
| 8. Dashboard & Charts | 3-4 weeks | Visual summaries |
| 9. Refinement & Release | 3-4 weeks | Polish, testing, release |

**Total: ~30-40 weeks (7-9 months)**

---

## 12. Risks & Mitigations

### Risk 1: Complexity of Settlement Engine
- **Impact**: High
- **Mitigation**: Thorough unit testing, step-by-step implementation, clear logging

### Risk 2: Offline Sync Conflicts
- **Impact**: Medium
- **Mitigation**: Last-write-wins strategy, conflict markers, user notification

### Risk 3: API Compatibility
- **Impact**: Medium
- **Mitigation**: Keep POJOs synchronized with Pydantic models, use code generation

### Risk 4: Performance (Large Groups)
- **Impact**: Medium
- **Mitigation**: Pagination, lazy loading, data caching

### Risk 5: Push Notification Reliability
- **Impact**: Low
- **Mitigation**: FCM fallback, local notification when offline

---

## 13. Next Steps

1. Review this proposal
2. Approve or request changes
3. Upon approval:
   - Initialize Android project
   - Set up dependency versions
   - Generate Kotlin data classes from Pydantic models (using KotlinPoet or similar)
   - Implement Phase 1 (Foundation)
   - Run iterative delivery

---

**Awaiting your approval to proceed with implementation.**
