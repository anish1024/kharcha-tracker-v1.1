# Android Frontend App Proposal - Kharcha Tracker

## Executive Summary

This proposal outlines the design and implementation plan for a native Android application for the Kharcha Tracker system. The app will provide a mobile-first experience with offline support, real-time sync, and Material Design 3 UI, built using Kotlin and Jetpack Compose. The app will communicate with the Python backend API server via REST API, following a separate monolith architecture.

**Technology Stack:**
- **UI Framework**: Jetpack Compose (Material 3)
- **Architecture**: Clean Architecture / MVVM
- **Language**: Kotlin
- **Networking**: Retrofit (REST API client)
- **Database**: SQLite + Room (local cache)
- **State Management**: Kotlin Flow + ViewModel with sealed states
- **Dependency Injection**: Koin
- **Charts**: Compose M3 Charts or MPAndroidChart
- **Icons**: Material Icons (Material Design 3)
- **Build Tool**: Gradle (Kotlin DSL)

---

## 1. Architecture Overview

### 1.1 Architecture Layers

```
┌─────────────────────────────────────────────────────────┐
│                   Presentation Layer                    │
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
│                   Domain Layer                         │
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
│                   Data Layer                           │
│  ┌─────────────┐ ┌─────────────┐ ┌─────────────┐      │
│  │  API Client │ │   DB Repo   │ │   Network   │      │
│  │ (Retrofit)  │ │  (Room)     │ │  Request    │      │
│  └─────────────┘ └─────────────┘ └─────────────┘      │
└─────────────────────────────────────────────────────────┘
                         ▼
┌─────────────────────────────────────────────────────────┐
│                   Python Backend API Server           │
│  ┌──────────────────────────────────────────────────┐  │
│  │  FastAPI/Flask REST API                          │  │
│  │  ┌─────────────────────────────────────────────┐│  │
│  │  │  /api/v1/auth     │  /api/v1/groups         ││  │
│  │  │  /api/v1/expenses │  /api/v1/settlements   ││  │
│  │  └─────────────────────────────────────────────┘│  │
│  └──────────────────────────────────────────────────┘  │
│                       │                                 │
│  ┌────────────────────┴─────────────────────────────┐  │
│  │  Python Data Models (Pydantic)                    │  │
│  │  └───────────────────────────────────────────────┘  │
│  │  │  Database: SQLite/PostgreSQL                   │  │
│  └───────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────┘
```

### 1.2 Navigation Architecture

```
MaterialDrawerLayout (Desktop/Tablet)
── AppBar (top bar with logo, notifications badge)
│
── Drawer (Sidebar) ──────┐
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

## 2. App Structure (Android Only)

```
android/
├─ app/
│   ├─ src/main/
│   │   ├─ AndroidManifest.xml
│   │   ├─ res/
│   │   │   ├─ mipmap-mdpi/
│   │   │   ├─ mipmap-hdpi/
│   │   │   ├─ mipmap-xhdpi/
│   │   │   ├─ mipmap-xxhdpi/
│   │   │   ├─ mipmap-xxxhdpi/
│   │   │   └─ values/
│   │   │       ├─ strings.xml
│   │   │       ├─ colors.xml
│   │   │       ├─ dimens.xml
│   │   │       ├─ themes.xml
│   │   │       └─ ic_launcher.xml
│   │   ├─ kotlin/
│   │       │   └─ com/
│   │       │   └─ kharcha/
│   │       │           └─ tracker/
│   │       │               ├─ MainActivity.kt
│   │       │               └─ di/          # Koin modules
│   │       │                   └─ AppModule.kt
│   │       └─ core/                         # Shared core (Android only)
│   │           ├─ data/
│   │           │   ├─ local/                # DB operations (Room/SQLite)
│   │           │   └─ remote/               # API client (Retrofit)
│   │           ├─ domain/                   # Use cases (clean architecture)
│   │           └─ model/                   # Data models (from Python API responses)
│   │   └─ composeApp/                       # Android-specific UI (Jetpack Compose)
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
└─ python_app/  # Python backend server (separate monolith - not included in Android)
    └─ app/
        └─ backend_logic/  # Business logic
            └─ services/
```

### Architecture Note

**Separate Monoliths Architecture:**

1. **Python Backend Server** (`python_app/app/`):
   - API endpoints (FastAPI/Flask)
   - Python data models (Pydantic)
   - Business logic
   - Database operations (SQLite/PostgreSQL)

2. **Android App** (`android/app/`):
   - Mobile UI (Jetpack Compose)
   - Retrofit API client calls Python API endpoints
   - Local SQLite/Room database for offline caching
   - State management with ViewModel and Flow

**Data Model Synchronization:**
- Python backend serves JSON responses from API endpoints
- Android app auto-generates Kotlin data classes from JSON schemas using:
  - `retrofit-converter` (JSON to Kotlin)
  - OR manual mapping with `@SerializedName` annotations
- Pydantic models ↔ JSON ↔ Kotlin data classes are kept in sync

**API Communication:**
```
Android App        REST API Calls        Python Backend Server
(Kotlin/Retrofit)         (JSON)        (FastAPI/Flask)
     |                      |                   |
     |--- POST /login ----->|                   |
     |<-- 200 JSON Response |<-------------------|
     |                      |                   |
     |                      |<------- GET /users
     |                      |------------------>|
     |<-- 200 JSON Response |<-------------------|
     |                      |                   |
     |<---------------------|<-------------------|
     |      Local Cache     |                   |
     |      (SQLite/Room)   |                   |
     |                      |                   |
```

---

## 3. Data Models

### Note: Data Models for Android

These models are used by the Android app to consume the Python API. They are auto-generated from the Python Pydantic models via the REST API responses.

### 3.1 Core Models

#### 3.1.1 Authentication

```kotlin
// User - received from /api/v1/users/me
data class User(
    val id: String,
    val email: String,
    val name: String,
    val avatar_url: String? = null,
    val trial_ends_at: String? = null,      // ISO 8601 date string
    val plan: String,                       // "FREE", "STANDARD", "PREMIUM"
    val plan_status: String,               // "active", "expired", "pending"
    val plan_ends_at: String? = null,      // ISO 8601 date string
    val limited_entries: Int,              // Remaining entries for current plan
    val is_active: Boolean,
    val created_at: String,                // ISO 8601 date string
    val updated_at: String                 // ISO 8601 date string
)

// AuthTokens - received from /api/v1/auth/login
data class AuthTokens(
    val access_token: String,
    val refresh_token: String,
    val token_type: String = "Bearer",
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

enum class InviteStatus {
    PENDING,
    ACCEPTED,
    REJECTED,
    EXPIRED
}

// Group - received from /api/v1/groups
data class Group(
    val id: String,
    val name: String,
    val currency: String,
    val status: GroupStatus,
    val invite_code: String,
    val invite_status: InviteStatus,
    val members: List<Member>,
    val expenses: List<Expense>,
    val created_at: String,
    val updated_at: String
)

data class Member(
    val id: String,
    val user_id: String,
    val name: String,
    val email: String,
    val role: String,
    val is_owner: Boolean,
    val joined_at: String,
    val is_online: Boolean
)
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
    SHOP    // Other categories as per API
}

// Expense - received from /api/v1/expenses
data class Expense(
    val trans_id: String,
    val expn_desc: String,
    val expn_ctgry: ExpenseCategory,
    val date_time: String,                  // ISO 8601 date string
    val amount: Double,
    val status: String,                     // "open", "paid", "settled", "deleted"
    val paid_by: List<ExpensePayment>?,
    val split_mode: String,                 // "EQUAL", "PERCENTAGE"
    val expn_splt: Map<String, Double>,
    val group_id: String,
    val is_deleted: Boolean,
    val created_at: String,
    val updated_at: String,
    val created_by: String,
    val modified_by: String?
)

data class ExpensePayment(
    val member_id: String,
    val paid_amount: Double,
    val paid_at: String? = null,
    val status: String
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

// Notification - received from /api/v1/notifications
data class Notification(
    val id: String,
    val kind: NotificationKind,
    val user_id: String,
    val group_id: String? = null,
    val note: String,
    val is_read: Boolean,
    val created_at: String,
    val expires_at: String? = null
)
```

### 3.5 Settlement Models

```kotlin
// MatrixRow - used for settlement calculations
data class MatrixRow(
    val expense_id: String,
    val payout_id: String? = null,
    val amount: Long,
    val share: Long
)

// Payout - received from /api/v1/payouts
data class Payout(
    val id: String,
    val group_id: String,
    val payor_id: String,
    val payee_id: String,
    val amount: Long,
    val status: String,                      // "pending", "paid", "settled", "failed", "cancelled"
    val paid_at: String? = null,
    val replaced_by: String? = null,
    val reason: String? = null,
    val created_at: String,
    val updated_at: String
)

// SettlementRound - received from /api/v1/settlements
data class SettlementRound(
    val id: String,
    val group_id: String,
    val status: String,                     // "pending", "paid", "partial", "settled", "failed"
    val total_amount: Long,
    val processed_amount: Long,
    val created_at: String,
    val updated_at: String,
    val matrix: List<MatrixRow>
)
```

---

## 4. API Client (Retrofit for Android)

### 4.1 Retrofit API Interface

```kotlin
// Generated Retrofit service for Python API
interface KharchaApiService {
    
    // Authentication
    @POST("api/v1/auth/login")
    suspend fun login(@Body loginRequest: LoginRequest): Response<AuthTokens>
    
    @POST("api/v1/auth/signup")
    suspend fun signup(@Body signupRequest: SignupRequest): Response<User>
    
    @POST("api/v1/auth/refresh")
    suspend fun refreshToken(@Body refreshRequest: RefreshTokenRequest): Response<AuthTokens>
    
    @POST("api/v1/auth/logout")
    suspend fun logout()
    
    // Users
    @GET("api/v1/users/me")
    suspend fun getCurrentUser(): Response<User>
    
    @PUT("api/v1/users/{id}")
    suspend fun updateUser(@Path("id") id: String, @Body userData: UserData): Response<User>
    
    // Groups
    @GET("api/v1/groups")
    suspend fun getGroups(): Response<List<Group>>
    
    @GET("api/v1/groups/{id}")
    suspend fun getGroup(@Path("id") id: String): Response<Group>
    
    @POST("api/v1/groups")
    suspend fun createGroup(@Body groupData: GroupData): Response<Group>
    
    @POST("api/v1/groups/{id}/invite")
    suspend fun inviteToGroup(@Path("id") id: String, @Body inviteData: InviteData): Response<InviteStatus>
    
    @POST("api/v1/groups/{id}/join")
    suspend fun joinGroup(@Path("id") id: String, @Query("invite_code") inviteCode: String): Response<Unit>
    
    @DELETE("api/v1/groups/{id}")
    suspend fun deleteGroup(@Path("id") id: String): Response<Unit>
    
    // Expenses
    @GET("api/v1/expenses")
    suspend fun getExpenses(@Query("group_id") groupId: String): Response<List<Expense>>
    
    @POST("api/v1/expenses")
    suspend fun createExpense(@Body expenseData: ExpenseData): Response<Expense>
    
    @PUT("api/v1/expenses/{id}")
    suspend fun updateExpense(@Path("id") id: String, @Body expenseData: ExpenseUpdate): Response<Expense>
    
    @DELETE("api/v1/expenses/{id}")
    suspend fun deleteExpense(@Path("id") id: String): Response<Unit>
    
    // Settlements
    @GET("api/v1/settlements/{groupId}")
    suspend fun getSettlement(@Path("groupId") groupId: String): Response<List<SettlementRound>>
    
    @POST("api/v1/settlements/{groupId}/settle")
    suspend fun settleGroup(@Path("groupId") groupId: String): Response<SettlementRound>
    
    // Notifications
    @GET("api/v1/notifications")
    suspend fun getNotifications(@Query("unread_only") unreadOnly: Boolean): Response<List<Notification>>
    
    @PUT("api/v1/notifications/{id}/read")
    suspend fun markAsRead(@Path("id") id: String): Response<Unit>
    
    // ... more endpoints
}
```

### 4.2 Retrofit API Client Setup

```kotlin
class ApiClient(
    private val retrofit: Retrofit,
    private val tokenStore: TokenStore
) {
    private val apiService: KharchaApiService = retrofit.create(KharchaApiService::class.java)
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val request = chain.request()
            val token = tokenStore.getAccessToken()
            
            if (token != null && !token.isEmpty()) {
                val newRequest = request.newBuilder()
                    .addHeader("Authorization", "Bearer $token")
                    .build()
                chain.proceed(newRequest)
            } else {
                chain.proceed(request)
            }
        }
        .build()
    
    suspend fun <T> callApi(method: String, path: String, body: Any? = null): Result<T> {
        val url = "http://127.0.0.1:8000/$path"
        val request: Request.Builder = when (method.uppercase()) {
            "GET" -> {
                val response = apiService.getFromPath(path)
                return Result.Success(statusCode = response.code(), body = response.body())
            }
            "POST" -> {
                val response = apiService.postToPath(path, body)
                return Result.Success(statusCode = response.code(), body = response.body())
            }
            "PUT" -> ...
            "PATCH" -> ...
            "DELETE" -> ...
            else -> throw IllegalStateException("Unsupported method")
        }
        
        // Handle error responses
        if (response.code() >= 400) {
            return Result.Error(
                code = response.code().toString(),
                message = getErrorMessage(response.code())
            )
        }
        
        return Result.Success(statusCode = response.code(), body = response.body())
    }
}
```

### 4.3 Local Cache Strategy (Room Database)

```kotlin
// Room database schema for local cache
@Database(entities = [
    UserEntity, GroupEntity, ExpenseEntity, PayoutEntity,
    NotificationEntity, InvitationEntity, SettlementRoundEntity,
    ExpenseCacheEntity, PayoutCacheEntity
], version = 1, exportSchema = true)
abstract class KharchaTrackerDatabase : RoomDatabase() {
    abstract val userDao: UserDao
    abstract val groupDao: GroupDao
    abstract val expenseDao: ExpenseDao
    abstract val payoutDao: PayoutDao
    abstract val notificationDao: NotificationDao
    // ... more DAOs
}

// DAO Interface
interface ExpenseDao {
    @Query("SELECT * FROM expenses WHERE group_id = :groupId ORDER BY date_time DESC LIMIT :limit")
    suspend fun getExpensesByGroup(groupId: String, limit: Int = 50): List<ExpenseEntity>
    
    @Query("SELECT * FROM expenses WHERE status = :status")
    suspend fun getExpensesByStatus(status: String): List<ExpenseEntity>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExpense(expense: ExpenseEntity): Long
    
    @Update
    suspend fun updateExpense(expense: ExpenseEntity): Int
    
    @Delete
    suspend fun deleteExpense(expense: ExpenseEntity): Int
    
    @Query("SELECT * FROM expenses WHERE id IN (:ids)")
    suspend fun getExpensesByIds(ids: List<String>): List<ExpenseEntity>
    
    @Query("SELECT COUNT(*) FROM expenses WHERE group_id = :groupId AND status = 'open'")
    suspend fun getOpenExpensesCount(groupId: String): Int
}
```

---

## 5. State Management

### 5.1 ViewModel Pattern

```kotlin
// Base ViewModel with Lifecycle Awareness
abstract class BaseViewModel : ViewModel() {
    protected val lifecycle = _lifecycle
    protected val scope = viewModelScope
}

// Example ViewModel for Groups
class GroupsViewModel(
    private val repository: GroupRepository,
    private val navigation: NavHostController<NavHostGraph>,
    private val authManager: AuthManager
) : BaseViewModel() {

    private val _groupsState = MutableStateFlow(GroupsState())
    val groupsState: StateFlow<GroupsState> = _groupsState.asStateFlow()
    
    init {
        observeGroups()
    }
    
    fun loadGroups() = scope.launch {
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
                error = e.message ?: "Unknown error"
            )
        }
    }
    
    private fun observeGroups() {
        // Observe real-time updates from WebSocket or polling
        // (if using WebSocket, this would subscribe to changes)
    }
    
    fun addGroup(group: Group) {
        scope.launch {
            repository.addGroup(group)
            refreshGroups()
        }
    }
    
    fun deleteGroup(groupId: String) {
        scope.launch {
            repository.deleteGroup(groupId)
            refreshGroups()
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

### 5.2 Local vs Remote State Strategy

```kotlin
sealed class ExpensedState {
    // Remote (from API)
    object FetchingFromServer : ExpensedState()
    data class Fetched(val expenses: List<Expense>) : ExpensedState()
    data class FetchError(val error: String) : ExpensedState()
    
    // Local (from cache)
    object FetchingFromLocal : ExpensedState()
    data class LoadedFromLocal(val expenses: List<Expense>) : ExpensedState()
    
    // Sync status
    object Syncing : ExpensedState()
    data class Synced(val localCount: Int, val remoteCount: Int) : ExpensedState()
}
```

---

## 6. UI Screens (Jetpack Compose)

### 6.1 App Shell

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
    subscriptionEndsAt: Instant?,
    windowSizeClass: WindowSizeClass,
    isTablet: Boolean,
    isDesktop: Boolean
) {
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Top Bar (always visible)
        KharchaTopAppBar(
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
                .background(MaterialTheme.colorScheme.surface)
        ) {
            LazyColumn(
                content = { items(count) { Item } }
            )
        }
        
        // Bottom Tab Bar (mobile/tablet only, hidden on desktop)
        when {
            isDesktop -> {
                // Desktop uses DrawerLayout
                KharchaDrawer(
                    currentRoute = currentRoute,
                    onNavigationClick = onNavigationClick
                )
            }
            isTablet -> {
                // Tablet uses TabRow (bottom bar)
                TabRow(
                    selectedTabIndex = when (currentRoute) {
                        "dashboard" -> 0
                        "groups" -> 1
                        "expenses" -> 2
                        "activity" -> 3
                        "settings" -> 4
                        "subscription" -> 5
                        else -> 0
                    },
                    containerColor = MaterialTheme.colorScheme.primary
                ) {
                    Tab(
                        selected = false,
                        text = Text("Dashboard"),
                        icon = { Icon(Icons.Strings.Dashboard, contentDescription = null) }
                    )
                    // ... more tabs
                }
            }
            else -> {
                // Mobile uses TabRow (bottom bar)
                TabRow(
                    selectedTabIndex = when (currentRoute) {
                        "dashboard" -> 0
                        "groups" -> 1
                        "expenses" -> 2
                        "activity" -> 3
                        "settings" -> 4
                        "subscription" -> 5
                        else -> 0
                    },
                    containerColor = MaterialTheme.colorScheme.primary
                ) {
                    Tab(
                        selected = false,
                        text = Text("Dashboard"),
                        icon = { Icon(Icons.Strings.Dashboard, contentDescription = null) }
                    )
                    // ... more tabs
                }
            }
        }
    }
}
```

### 6.2 Login/Signup Screens

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
```

---

## 7. Implementation Roadmap (Android Only)

### Phase 1: Foundation (Weeks 1-2)
- [ ] Project setup with Gradle Kotlin DSL
- [ ] Core data models (User, Group, Expense, etc.) - generated from Python API
- [ ] Room database schema
- [ ] Retrofit API client (connect to Python backend)
- [ ] Dependency injection (Koin)
- [ ] Theme setup (Material 3)

### Phase 2: Authentication (Weeks 3-4)
- [ ] Login screen
- [ ] Signup screen
- [ ] Token management (access/refresh)
- [ ] Magic link SSO
- [ ] Session persistence (store tokens in encrypted preferences)

### Phase 3: Groups (Weeks 5-7)
- [ ] Groups list screen
- [ ] Create group
- [ ] Join via invite code
- [ ] Group details screen
- [ ] Member management
- [ ] Invite members via email

### Phase 4: Expenses (Weeks 8-10)
- [ ] Expense entry screen
- [ ] Custom splits
- [ ] Expense list screen
- [ ] Edit/delete expenses

### Phase 5: Dashboard (Weeks 11-12)
- [ ] Dashboard screen
- [ ] Expense summary cards
- [ ] Spending trends chart (MPAndroidChart/Compose Charts)
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
- [ ] App preferences

### Phase 9: Polish (Week 20)
- [ ] Bug fixes
- [ ] Performance optimization
- [ ] Analytics integration (Firebase Analytics)
- [ ] Crash reporting (Firebase Crashlytics)

---

## 8. Permissions & Background

### 8.1 Required Permissions

```xml
<!-- AndroidManifest.xml -->
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
<uses-permission android:name="android.permission.SCHEDULE_EXACT_ALARM" />
<uses-permission android:name="android.permission.USE_EXACT_ALARM" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
```

### 8.2 Background Sync Service

```kotlin
// WorkManager for periodic background sync
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

// Schedule background work periodically
fun scheduleBackgroundSync(context: Context, intervalHours: Int = 1) {
    val constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()
    
    val workRequest = OneTimeWorkRequestBuilder<SyncWorker>()
        .setConstraints(constraints)
        .build()
    
    WorkManager.getInstance(context)
        .enqueueUniqueWork("sync_data", ExistingPeriodicWorkReplacements, workRequest)
    
    // For periodic sync, also add PeriodicWorkRequest
    val periodicWorkRequest = PeriodicWorkRequestBuilder<SyncWorker>(intervalHours * 60 * 60 * 1000)
        .setInitialDelay(1000)
        .setConstraints(constraints)
        .build()
    
    WorkManager.getInstance(context)
        .enqueueUniqueWork("periodic_sync", ExistingPeriodicWorkReplacements, periodicWorkRequest)
}
```

---

## 9. API Communication with Python Backend

### 9.1 API Endpoint Mapping (Versioned)

| Endpoint Group | Version | Description |
|----------------|--------|----------------|
| Authentication | `/api/v1/auth/` | Login, signup, refresh, logout, magic link |
| Users | `/api/v1/users/` | User management, profile |
| Groups | `/api/v1/groups/` | Group CRUD, member management |
| Expenses | `/api/v1/expenses/` | Expense CRUD, splits |
| Settlements | `/api/v1/settlements/` | Settlement rounds, payouts |
| Notifications | `/api/v1/notifications/` | Notification CRUD |
| Subscriptions | `/api/v1/subscriptions/` | Subscription management |
| Activity | `/api/v1/activity/` | Audit log |
| Settings | `/api/v1/settings/` | User preferences |

### 9.2 API Version Management

#### Backend Strategy (Python)
```python
# API versioning - always include version in path
# Version 1.0 (Current)
@app.get("/api/v1/auth/login")
@app.get("/api/v1/users/", response_model=UserV1)

# Version 2.0 (Future - when ready)
@app.get("/api/v2/auth/login")
@app.get("/api/v2/users/", response_model=UserV2)

# Response headers for version tracking
# Always include in responses:
# - X-API-Version: v1 or v2
# - X-API-Schema-Version: for model schema version

# Deprecation headers (when sunsetting v1):
# Deprecation: true
# Sunset: Thu, 01 Jan 2028 00:00:00 GMT (NTP timestamp)
# Link: </api/v2/users>; rel="successor-version"
# Link: </v2/swagger.json>; rel="openapi"

# Schema version tracking (for generated models)
# X-API-Schema-Version: 1
```

#### Android Client Strategy
```kotlin
// API Version Service - managed on both client and server
class ApiVersionManager {
    companion object {
        const val CLIENT_DEFAULT_VERSION = "1.0"
        val SUPPORTED_VERSIONS = setOf("1.0", "2.0", "3.0")
        
        // Get server's current supported version
        suspend fun getSupportedVersion(context: Context): ApiVersion {
            val headers = apiService.ping()
            val version = extractVersionFromHeader(headers)
            val currentVersion = PREFERRED_VERSION
            return ApiVersion(
                version = version,
                currentVersion = currentVersion,
                isCurrent = version == currentVersion,
                isDeprecated = version.isDeprecated(),
                supportEndDate = version.supportEndDate
            )
        }
    }
    
    data class ApiVersion(
        val version: String,
        val currentVersion: String,
        val isDeprecated: Boolean = false,
        val isUnsupported: Boolean = false,
        val supportEndDate: String? = null,
        val migrationAvailable: Boolean = false,
        val nextVersion: String? = null
    )
}

// Auto-version selection in Retrofit
class KharchaRetrofitClient(
    private val apiVersion: String = "1.0",
    private val serverUrl: String = "http://127.0.0.1:8000"
) {
    private val retrofit: Retrofit
    
    init {
        // Discover version from server at first use
        apiVersion = discoverApiVersion()
    }
    
    private fun discoverApiVersion(): String {
        val pingHeaders = apiService.ping()
        val xApiVersion = pingHeaders["X-API-Version"]
        val apiVersionHeader = pingHeaders["API-Version"]
        
        val detectedVersion = xApiVersion.or(apiVersionHeader)
        
        // Validate detected version
        return when {
            detectedVersion in SUPPORTED_VERSIONS -> detectedVersion
            detectedVersion == null -> CLIENT_DEFAULT_VERSION
            else -> {
                // Server is using unsupported version
                // Upgrade client to match server
                val newVersion = findNextSupportedVersion(detectedVersion)
                clientVersion = newVersion
                showVersionWarning(newVersion)
                newVersion
            }
        }
    }
    
    // Retrofit service with version-aware endpoints
    interface KharchaApiService {
        @GET("$apiVersion/api/v1/auth/login")
        suspend fun login(@Body loginRequest: LoginRequest): Response<AuthTokens>
        
        @GET("$apiVersion/api/v1/users/")
        suspend fun getGroups(): Response<List<Group>>
        
        // Auto-generate new API endpoints when version changes
        // OR use version-aware interface
        @GET("$apiVersion/api/v1/{endpoint}")
        suspend fun dynamicEndpoint(
            @Path("endpoint") endpoint: String,
            @Query("api_version") version: String = apiVersion
        ): Response<DynamicResponse>
    }
}
```

#### Version Migration Support
```kotlin
// Gradual migration strategy
class VersionMigrationManager {
    fun shouldUseVersion(
        endpoint: String, 
        currentClientVersion: String, 
        serverVersion: String
    ): String {
        val apiVersion = when {
            // Server version is same or newer, and endpoint is backward compatible
            serverVersion == currentClientVersion -> currentClientVersion
            
            // Server has new version, but we're already on latest
            currentClientVersion in SUPPORTED_VERSIONS -> serverVersion
            
            // Server has new version, client needs to migrate
            else -> serverVersion
        }
        
        return apiVersion
    }
    
    fun detectEndpointVersion(endpoint: String): String {
        return when {
            endpoint.contains("/v2/") -> "2.0"
            endpoint.contains("/v3/") -> "3.0"
            else -> "1.0"
        }
    }
    
    fun handleVersionConflict(
        endpoint: String,
        clientVersion: String,
        serverVersion: String
    ): VersionConflictStrategy {
        return when {
            clientVersion == serverVersion -> VersionConflictStrategy.NOTHING
            
            // Server version is newer, try to upgrade
            clientVersion in SUPPORTED_VERSIONS -> {
                VersionConflictStrategy.MIGRATE_TO_NEW {
                    // Show migration dialog
                    upgradeToVersion(serverVersion)
                }
            }
            
            // Both on unsupported version
            else -> VersionConflictStrategy.DOWNGRADE_OR_UPGRADE {
                if (serverVersion in SUPPORTED_VERSIONS) {
                    upgradeToVersion(serverVersion)
                } else {
                    // Server is behind, suggest update
                    VersionConflictStrategy.RECOMMEND_SERVER_UPDATE
                }
            }
        }
    }
    
    enum class VersionConflictStrategy(val action: () -> Unit) {
        NOTHING,
        MIGRATE_TO_NEW,
        STICK_TO_CLIENT,
        RECOMMEND_SERVER_UPDATE
    }
    
    suspend fun resolveConflict(strategy: VersionConflictStrategy): VersionConflictResolution {
        return when (strategy) {
            VersionConflictStrategy.MIGRATE_TO_NEW -> {
                performMigration()
                VersionConflictResolution.SUCCESSFUL_MIGRATION
            }
            VersionConflictStrategy.STICK_TO_CLIENT -> {
                attemptFallbackRequest()
                VersionConflictResolution.FALLBACK_USED
            }
            VersionConflictStrategy.RECOMMEND_SERVER_UPDATE -> {
                VersionConflictResolution.RECOMMEND_ACTION
            }
            else -> VersionConflictResolution.NO_CONFLICT
        }
    }
}

// Model version tracking
class ModelVersionManager {
    data class ModelVersionInfo(
        val modelName: String,
        val schemaVersion: Int,
        val clientVersion: Int,
        val serverVersion: Int,
        val isCompatible: Boolean,
        val migrationPath: String? = null
    )
    
    fun checkModelCompatibility(
        clientModel: DataClass,
        serverResponse: JsonElement
    ): ModelCompatibilityStatus {
        // Check field-by-field compatibility
        val clientFields = clientModel::class.java.fields.map { it.name }
        val serverFields = extractFieldNames(serverResponse)
        
        val missingFields = clientFields - serverFields
        val extraFields = serverFields - clientFields
        
        return when {
            missingFields.isEmpty() && extraFields.isEmpty() -> {
                ModelCompatibilityStatus.COMPATIBLE
            }
            missingFields.isEmpty() -> {
                ModelCompatibilityStatus.EXTRA_FIELDS(serverFields - clientFields)
            }
            extraFields.isEmpty() -> {
                ModelCompatibilityStatus.MISSING_FIELDS(clientFields - serverFields)
            }
            else -> {
                ModelCompatibilityStatus.INCOMPATIBLE
            }
        }
    }
}
```

---

## 9.3 API Health & Compatibility Checks

### 9.3.1 API Health Check

```kotlin
// Check if backend API is healthy and responding
class ApiHealthChecker {
    suspend fun checkApiHealth(context: Context): ApiHealthStatus {
        val startTime = System.currentTimeMillis()
        
        try {
            val response = apiService.ping()
            val responseTime = System.currentTimeMillis() - startTime
            val statusCode = response.code()
            
            val health = when (statusCode) {
                in 200..299 -> {
                    ApiHealthStatus.HEALTHY(
                        responseTime = responseTime,
                        version = extractVersionFromResponse(response),
                        features = detectSupportedFeatures()
                    )
                }
                429 -> ApiHealthStatus.RATE_LIMITED()
                500..599 -> ApiHealthStatus.SERVER_ERROR()
                else -> ApiHealthStatus.UNRESPONSIVE(responseTime = responseTime)
            }
            
            updateConnectionState(health)
            return health
        } catch (e: Exception) {
            return ApiHealthStatus.UNREACHABLE(exception = e)
        }
    }
    
    private fun detectSupportedFeatures(): Set<ApiFeature> {
        val features = mutableSetOf<ApiFeature>()
        
        // Test each major feature endpoint
        try {
            if (apiService.hasCapability("groups")) features += ApiFeature.GROUPS
            if (apiService.hasCapability("expenses")) features += ApiFeature.EXPENSES
            if (apiService.hasCapability("settlements")) features += ApiFeature.SETTLEMENTS
            // ...
        } catch (e: Exception) {
            // Feature not available
        }
        
        return features
    }
}

data class ApiHealthStatus(
    val isHealthy: Boolean,
    val responseTime: Long? = null,
    val version: String? = null,
    val supportedFeatures: Set<ApiFeature>? = null,
    val errors: List<ApiError>? = null
)

enum class ApiFeature {
    AUTHENTICATION,
    GROUPS,
    EXPENSES,
    SETTLEMENTS,
    NOTIFICATIONS,
    SUBSCRIPTIONS,
    ACTIVITY_LOG
}

enum class ApiError(val code: String) {
    TIMEOUT("TIMEOUT"),
    RATE_LIMITED("RATE_LIMITED"),
    SERVER_ERROR("SERVER_ERROR"),
    NOT_FOUND("NOT_FOUND"),
    UNAUTHORIZED("UNAUTHORIZED"),
    INCOMPATIBLE_VERSION("VERSION_INCOMPATIBLE")
}
```

### 9.3.2 Feature Detection

```kotlin
// Detect which API features are available on the server
class FeatureDetectionService {
    suspend fun detectFeatures(context: Context): DetectedFeatures {
        // Check each feature endpoint
        val features = mutableMapOf<String, ApiFeatureStatus>()
        
        features["auth"] = checkFeature("/api/v1/auth/")
        features["groups"] = checkFeature("/api/v1/groups/")
        features["expenses"] = checkFeature("/api/v1/expenses/")
        features["settlements"] = checkFeature("/api/v1/settlements/")
        
        // Check for beta/new features
        features["beta_features"] = checkBetaFeatures()
        
        return DetectedFeatures(features)
    }
    
    private suspend fun checkFeature(endpoint: String): ApiFeatureStatus {
        try {
            val response = apiService.getFeatureStatus(endpoint)
            return if (response.status == "active") {
                ApiFeatureStatus.ACTIVE(response.version)
            } else {
                ApiFeatureStatus.INACTIVE(response.message)
            }
        } catch (e: Exception) {
            return ApiFeatureStatus.UNAVAILABLE(e.message)
        }
    }
}
```

---

## 9.4 API Schema Evolution

### 9.4.1 Automatic Model Sync

```kotlin
// When Python API schema changes (new version deployed)
// Android auto-detects and updates models

class ModelSyncService {
    suspend fun checkModelSyncStatus(context: Context): ModelSyncStatus {
        val serverSchema = fetchServerSchema()
        val localSchema = getLocalModelSchema()
        
        val syncStatus = compareSchemaVersions(serverSchema, localSchema)
        
        when (syncStatus) {
            SchemaStatus.UP_TO_DATE -> return ModelSyncStatus.UP_TO_DATE
            SchemaStatus.OUTDATED -> return ModelSyncStatus.OUTDATED(
                recommendedVersion = syncStatus.recommendedVersion,
                migrationRequired = syncStatus.requiresMigration
            )
            SchemaStatus.DIVERGED -> return ModelSyncStatus.DIVERGED(
                serverSchemaVersion = syncStatus.serverVersion,
                localSchemaVersion = syncStatus.localVersion,
                migrationPath = syncStatus.migrationPath
            )
        }
    }
    
    suspend fun syncModels(context: Context) {
        val status = checkModelSyncStatus(context)
        
        when (status) {
            ModelSyncStatus.UP_TO_DATE -> {
                // Already synced
            }
            ModelSyncStatus.OUTDATED,
            ModelSyncStatus.DIVERGED -> {
                // Download new model files
                downloadModelUpdate(status.migrationPath)
                applyModelUpdate(status.migrationPath)
                showSyncCompleteNotification()
            }
        }
    }
}
```

### 9.4.2 Version Negotiation

```kotlin
// Client tells server its version, server tells client its version
class VersionNegotiator {
    suspend fun negotiateVersions(): VersionAgreement {
        // Client proposes its version
        val clientVersion = clientPreferredVersion
        
        // Server suggests compatible version
        val serverResponse = negotiateVersion(clientVersion)
        val serverPreferred = extractServerVersion(serverResponse)
        
        // Decide which version to use
        val agreedVersion = resolveAgreement(clientVersion, serverPreferred)
        
        return VersionAgreement(
            clientVersion = clientVersion,
            serverVersion = serverPreferred,
            agreedVersion = agreedVersion,
            compatible = clientVersion in serverSupportedVersions
        )
    }
    
    private suspend fun negotiateVersion(clientVersion: String): VersionResponse {
        val negotiateRequest = VersionNegotiationRequest(clientVersion)
        val response = apiService.negotiateVersion(negotiateRequest)
        return extractServerVersion(response)
    }
    
    private fun resolveAgreement(clientVersion: String, serverVersion: String): String {
        return when {
            clientVersion == serverVersion -> clientVersion
            clientVersion in SUPPORTED_VERSIONS && serverVersion in SUPPORTED_VERSIONS -> {
                // Prefer newer version
                whenVersionNewer(clientVersion, serverVersion)
            }
            clientVersion in SUPPORTED_VERSIONS -> clientVersion
            else -> serverVersion
        }
    }
    
    private fun whenVersionNewer(v1: String, v2: String): String {
        val v1Parts = parseVersion(v1)
        val v2Parts = parseVersion(v2)
        
        return when {
            v2Parts.major > v1Parts.major -> v2
            v2Parts.minor > v1Parts.minor -> v2
            else -> v1
        }
    }
}
```

---

## 9.5 Breaking Change Detection

```kotlin
// Detect if API has breaking changes that affect Android
class BreakingChangeDetector {
    data class ChangeReport(
        val changeType: ChangeType,
        val endpoint: String,
        val oldModel: String,
        val newModel: String,
        val impact: ImpactLevel,
        val migrationHint: String
    )
    
    enum class ChangeType {
        FIELD_ADDED,
        FIELD_REMOVED,
        FIELD_TYPE_CHANGED,
        REQUIRED_CHANGED,
        OPTIONAL_TO_REQUIRED,
        NEW_FIELD,
        REMOVED_FIELD,
        DEPRECATED,
        RENAMED_FIELD,
        METHOD_CHANGED,
        RESPONSE_CHANGED,
        BREAKING // Something that breaks compatibility
    }
    
    enum class ImpactLevel {
        NONE,         // No action needed
        MINOR,        // Update app, no data loss
        MAJOR,        // Update required, possible data migration
        BREAKING     // Update required, data loss without migration
    }
    
    fun detectBreakingChanges(): List<ChangeReport> {
        val changes = mutableListOf<ChangeReport>()
        
        // Compare current API model with what app expects
        val serverModels = fetchAllApiModels()
        val clientModels = getLocalModelMappings()
        
        for ((modelName, clientModel) in clientModels) {
            val serverModel = serverModels[modelName] ?: continue
            
            val changes = detectModelDifferences(clientModel, serverModel)
            
            for (change in changes) {
                if (change.impact == ImpactLevel.BREAKING || 
                    (change.impact == ImpactLevel.MAJOR && change.requiresMigration)) {
                    changes.add(
                        ChangeReport(
                            changeType = change.changeType,
                            endpoint = change.affectedEndpoint,
                            oldModel = clientModel,
                            newModel = serverModel,
                            impact = change.impact,
                            migrationHint = generateMigrationHint(change)
                        )
                    )
                }
            }
        }
        
        return changes
    }
    
    fun generateMigrationHint(change: ChangeReport): String {
        return when (change.changeType) {
            ChangeType.FIELD_REMOVED,
            ChangeType.REMOVED_FIELD -> 
                "Download updated models from: ${change.migrationPath}"
            
            ChangeType.FIELD_TYPE_CHANGED,
            ChangeType.FIELD_RENAMED -> 
                "Migrate to new API version, data will be converted"
            
            ChangeType.OPTIONAL_TO_REQUIRED -> 
                "Mark field as deprecated or provide default value"
            
            else -> "Compatibility check passed"
        }
    }
}
```

---

## 9.6 Graceful Degradation

```kotlin
// If server is down or API is incompatible, use cached data
class GracefulDegradationManager {
    fun enableDegradationMode(context: Context, reason: DegradationReason) {
        degradationReason = reason
        offlineMode = true
        
        val degradationLevel = when (reason) {
            DegradationReason.UNREACHABLE_SERVER -> DegradationLevel.FULL_OFFLINE
            DegradationReason.UNSUPPORTED_VERSION -> DegradationVersionLevel.FALLBACK_VERSION
            DegradationReason.RATE_LIMITED -> DegradationLevel.TEMPORARY_OFFLINE
            DegradationReason.SERVER_ERROR -> DegradationLevel.PAID_OFFLINE
        }
        
        setDegradationLevel(degradationLevel)
        showDegradationNotification(reason)
    }
    
    fun isFeatureAvailable(feature: ApiFeature): Boolean {
        return when (degradationLevel) {
            DegradationLevel.FULL_OFFLINE -> {
                // Only local features available
                isLocalFeatureAvailable(feature)
            }
            DegradationLevel.TEMPORARY_OFFLINE -> {
                // Local features + read-only server features
                isLocalFeatureAvailable(feature) || isReadOnlyServerFeature(feature)
            }
            DegradationLevel.PAID_OFFLINE -> {
                // Local features + write-capable server features (with warnings)
                isLocalFeatureAvailable(feature) || 
                    (isWriteServerFeatureAvailable(feature) && showWarning)
            }
            DegradationLevel.OFFLINE -> {
                // All local features
                true
            }
            else -> true
        }
    }
}

enum class DegradationLevel(val features: Set<ApiFeature>) {
    FULL_OFFLINE(setOf(
        ApiFeature.LOCAL_CACHE,
        ApiFeature.OFFLINE_ONLY
    )),
    TEMPORARY_OFFLINE(setOf(
        ApiFeature.LOCAL_CACHE,
        ApiFeature.OFFLINE_READ,
        ApiFeature.OFFLINE_WRITE
    )),
    OFFLINE(setOf(
        ApiFeature.LOCAL_CACHE,
        ApiFeature.OFFLINE_READ,
        ApiFeature.OFFLINE_WRITE,
        ApiFeature.OFFLINE_SYNC
    )),
    PAID_OFFLINE(setOf(
        ApiFeature.LOCAL_CACHE,
        ApiFeature.OFFLINE_READ,
        ApiFeature.OFFLINE_WRITE,
        ApiFeature.OFFLINE_SYNC,
        ApiFeature.DEGRADED_SERVER
    ));
    
    fun isFeatureAvailable(feature: ApiFeature): Boolean = features.contains(feature)
}

enum class DegradationReason {
    UNREACHABLE_SERVER,
    UNSUPPORTED_VERSION,
    RATE_LIMITED,
    SERVER_ERROR,
    INVALID_TOKEN,
    VERSION_MISMATCH
}
```

---

## 11. Local Database Strategy (Room)

### High Priority Risks for Android Only

1. **Separate monolith complexity** - Android and Python are completely separate; breaking changes can affect both independently
2. **API version compatibility** - Android app may break if Python API changes
3. **Large dataset performance** - Need efficient SQLite queries and caching
4. **Retrofit vs generated models** - Manual annotation with `@SerializedName` can be error-prone
5. **Material 3 learning curve** - Less mature than Material 2, need to follow guidelines closely

### Mitigation Strategies

1. Use API versioning in URLs (`/api/v1/`)
2. Implement API version check during app startup
3. Use pagination and lazy loading for lists
4. Consider using `retrofit-converter` with `kotlinx-serialization-json` for model generation
5. Follow Material Design 3 guidelines strictly
6. Implement robust error handling and user feedback

---

## 12. Deliverables

1. **Android App** (`android/app/`) - Complete mobile application
2. **Gradle Build Configuration** - Multi-module project setup
3. **API Client** (Retrofit) - REST client for Python backend
4. **Local Database** (Room/SQLite) - Offline data storage
5. **UI Screens** (Jetpack Compose) - Material 3 themed screens
6. **ViewModels** - State management
7. **Documentation** - Usage and developer documentation
8. **Test Suite** - Unit tests, Compose tests, integration tests
9. **Release Scripts** - Build, test, package (APK/AAB) scripts

---

## 13. File Locations

```
/home/pi/Projects/kharcha-tracker-v1.1/android/
├── app/                          # Android app module
│   └── build.gradle.kts
├── composeApp/                   # Jetpack Compose module
│   └── build.gradle.kts
├── src/
│   └── main/
│       ├── AndroidManifest.xml
│       ├── res/                  # Android resources
│       └── kotlin/com/kharcha/tracker/
│           ├── di/               # Koin dependency injection
│           ├── models/           # Data models
│           ├── repositories/     # Repository implementations
│           ├── viewmodels/       # ViewModels
│           └── screens/          # Composable screens
└── composeApp/src/main/kotlin/
    └── com/kharcha/tracker/      # Android-specific UI
```

---

## 14. Next Steps

Before implementation begins:

1. **Review the Python API blueprint** (`kharcha-tracker.yaml`) - Ensure all endpoints are accessible and documented
2. **Verify Python backend is running** - Ensure API server is up and responding
3. **Confirm design system** - Approve Material 3 theme colors and typography
4. **Plan for platform-specific features** - Camera, NFC, biometric auth for Android
5. **Choose model generation strategy** - Retrofit converter vs manual annotations
6. **Set up API credentials** - Ensure test user accounts exist

---

*This proposal covers the architecture, components, and roadmap for building a native Android frontend application for the Kharcha Tracker system, with mobile-first responsive design principles and REST API communication with the Python backend server.*