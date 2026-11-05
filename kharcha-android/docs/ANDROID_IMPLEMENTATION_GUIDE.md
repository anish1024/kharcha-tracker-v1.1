# Android Implementation Guide - Kharcha Tracker

## Current Status

✅ **Completed:**
- Group entity and related models
- Expense entity with items, attachments, and approval history
- Settlement entity with payment records
- Category entity with hierarchical structure
- Milestone entity with transaction tracking
- User entity with preferences
- All API service interfaces
- REST client for requests and responses
- Database entities and structure
- Database manager

## File Structure

```
android/app/src/main/kotlin/com/kharcha/tracker/
├── api/
│   ├── AuthApiService.kt                    ✅
│   ├── ExpenseApiService.kt                ✅
│   ├── GroupApiService.kt                  ✅
│   ├── CategoryApiService.kt               ✅
│   └── MilestoneApiService.kt              ✅
│
├── di/
│   └── NetworkModule.kt                    ✅
│   └── RepositoryModule.kt                ⏳ TODO
│
├── database/
│   ├── Database.kt                         ⏳ TODO
│   ├── DatabaseManager.kt                  ✅
│   ├── dao/
│   │   ├── UserDao.kt                      ⏳ TODO
│   │   ├── GroupDao.kt                     ⏳ TODO
│   │   ├── ExpenseDao.kt                   ⏳ TODO
│   │   ├── SettlementDao.kt                ⏳ TODO
│   │   ├── CategoryDao.kt                  ⏳ TODO
│   │   └── MilestoneDao.kt                 ⏳ TODO
│   └── entities/
│       ├── Group.kt                        ✅
│       ├── Expense.kt                      ✅
│       ├── Settlement.kt                   ✅
│       ├── Category.kt                     ✅
│       ├── Milestone.kt                    ✅
│       └── User.kt                         ✅
│
├── utils/
│   ├── NetworkManager.kt                   ⏳ TODO
│   ├── NetworkStatusListener.kt            ⏳ TODO
│   └── rest/
│       └── RestClient.kt                   ✅
│
└── models/
    └── ... (all model classes)              ✅
```

## Next Steps - Implementation Priority

### 1. Database Layer (High Priority)

#### Create Database Entity Classes

```kotlin
// android/app/src/main/kotlin/com/kharcha/tracker/database/entities/User.kt
@Entity(tableName = "users")
data class User(
    @PrimaryKey(autoGenerate = true) val id: Long,
    val userId: String,
    val username: String,
    val email: String,
    val role: String,
    val organization: String?,
    val avatar: String?,
    val isActive: Boolean = true,
    val createdAt: String,
    val updatedAt: String,
    val currency: String = "INR",
    val theme: String = "system",
    val language: String = "en"
)
```

```kotlin
// android/app/src/main/kotlin/com/kharcha/tracker/database/entities/Group.kt
@Entity(tableName = "groups")
data class Group(
    @PrimaryKey(autoGenerate = true) val id: Long,
    val groupId: String,
    val name: String,
    val userId: String,
    val budgetAmount: Double?,
    val currency: String = "INR"
)
```

```kotlin
// android/app/src/main/kotlin/com/kharcha/tracker/database/entities/Expense.kt
@Entity(tableName = "expenses")
data class Expense(
    @PrimaryKey(autoGenerate = true) val id: Long,
    val expenseId: String,
    val groupId: Long,
    val name: String,
    val amount: Double,
    val status: String,
    val date: String?,
    val currency: String = "INR",
    val isOnline: Boolean = true,
    val isSynced: Boolean = true,
    val lastSyncedAt: String?
)
```

```kotlin
// android/app/src/main/kotlin/com/kharcha/tracker/database/entities/Settlement.kt
@Entity(tableName = "settlements")
data class Settlement(
    @PrimaryKey(autoGenerate = true) val id: Long,
    val settlementId: String,
    val groupId: Long,
    val amount: Double,
    val status: String
)
```

```kotlin
// android/app/src/main/kotlin/com/kharcha/tracker/database/entities/Category.kt
@Entity(tableName = "categories")
data class Category(
    @PrimaryKey(autoGenerate = true) val id: Long,
    val categoryId: String,
    val name: String,
    val userId: String
)
```

```kotlin
// android/app/src/main/kotlin/com/kharcha/tracker/database/entities/Milestone.kt
@Entity(tableName = "milestones")
data class Milestone(
    @PrimaryKey(autoGenerate = true) val id: Long,
    val milestoneId: String,
    val groupId: Long,
    val title: String,
    val budget: Double
)
```

#### Create Repository Module

```kotlin
// android/app/src/main/kotlin/com/kharcha/tracker/di/RepositoryModule.kt
@Module
@InstallIn(Singleton class::class)
class RepositoryModule {
    @Provides
    @Singleton
    fun provideAuthRepository(
        authService: AuthApiService,
        userRepo: UserRepo,
        authStorage: AuthStorage
    ): AuthRepository = AuthRepository(authService, userRepo, authStorage)
    
    @Provides
    @Singleton
    fun provideExpenseRepository(
        expenseApiService: ExpenseApiService,
        expenseRepo: ExpenseRepo
    ): ExpenseRepository = ExpenseRepository(expenseApiService, expenseRepo)
    
    @Provides
    @Singleton
    fun provideGroupRepository(
        groupApiService: GroupApiService,
        groupRepo: GroupRepo
    ): GroupRepository = GroupRepository(groupApiService, groupRepo)
    
    @Provides
    @Singleton
    fun provideCategoryRepository(
        categoryApiService: CategoryApiService,
        categoryRepo: CategoryRepo
    ): CategoryRepository = CategoryRepository(categoryApiService, categoryRepo)
    
    @Provides
    @Singleton
    fun provideMilestoneRepository(
        milestoneApiService: MilestoneApiService,
        milestoneRepo: MilestoneRepo
    ): MilestoneRepository = MilestoneRepository(milestoneApiService, milestoneRepo)
}
```

### 2. Repository Layer (High Priority)

```kotlin
// Repository pattern with cache-then-network strategy

interface AuthRepository {
    fun login(email: String, password: String): Flow<AuthTokens>
    fun logout(): Flow<Unit>
    fun getCurrentUser(): Flow<User?>
    fun refreshToken(): Flow<AuthTokens>
}

interface ExpenseRepository {
    suspend fun createExpense(expense: Expense): Result<Expense>
    suspend fun updateExpense(expense: Expense): Result<Expense>
    suspend fun deleteExpense(expenseId: String): Result<Unit>
    fun getExpenses(userId: String): Flow<List<Expense>>
    fun getExpenseById(expenseId: String): Flow<Expense?>
    suspend fun addReceipt(expenseId: String, receipt: MultipartBody.Part): Result<Unit>
}
```

```kotlin
// Base repository implementation

abstract class BaseRepository<T>(private val cacheManager: CacheManager<T>) {
    abstract suspend fun fetchFromNetwork(key: String): Result<T>
    abstract fun getFromCache(key: String): T?
    
    fun loadData(key: String) = launch {
        getFromCache(key)?.let { emit(it) } ?: fetchFromNetwork(key) { data ->
            cacheManager.save(key, data)
            emit(data)
        }
    }
}
```

### 3. Network Layer (Medium Priority)

#### Implement NetworkManager

```kotlin
// android/app/src/main/kotlin/com/kharcha/tracker/utils/NetworkManager.kt
class NetworkManager private constructor() {
    private var connectionType: ConnectionType = ConnectionType.DEFAULT
    private var networkUrl: String = "YOUR_API_URL"
    private var connectionTimeout = 30
    private var readTimeout = 30
    
    companion object {
        @Volatile
        private var INSTANCE: NetworkManager? = null
        
        val getINSTANCE: NetworkManager
            get() = INSTANCE ?: synchronized(this) {
                INSTANCE = NetworkManager()
                INSTANCE!!
            }
    }
    
    fun getConnectionType(): ConnectionType = connectionType
    fun getNetworkUrl(): String = networkUrl
    
    fun isOnline(): Boolean = this.connectionType == ConnectionType.ONLINE
    fun isOffline(): Boolean = this.connectionType == ConnectionType.OFFLINE
}

enum class ConnectionType {
    ONLINE, OFFLINE, METRIC_AWARE, DEFAULT
}
```

#### Implement NetworkStatusListener

```kotlin
// android/app/src/main/kotlin/com/kharcha/tracker/utils/NetworkStatusListener.kt
class NetworkStatusListener(private val onStatusChanged: (Boolean) -> Unit) : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork = connectivityManager.activeNetwork ?: return
        val networkInfo = connectivityManager.getNetworkProperties(activeNetwork)
        
        val isConnected = networkInfo.isConnected
        val isMetered = networkInfo.isMetered
        
        val newType = when {
            isConnected -> {
                if (isMetered) ConnectionType.METRIC_AWARE else ConnectionType.ONLINE
            }
            else -> ConnectionType.OFFLINE
        }
        
        if (newType != this.lastKnownType) {
            this.lastKnownType = newType
            onStatusChanged(newType == ConnectionType.ONLINE)
        }
    }
}
```

### 4. Sync Layer (High Priority)

#### Sync Manager

```kotlin
// android/app/src/main/kotlin/com/kharcha/tracker/utils/SyncManager.kt
class SyncManager {
    fun syncExpenses(groupId: String): Job {
        return viewModelScope.launch {
            val localExpenses = getExpenseDao().getExpensesByGroup(groupId)
            val serverExpenses = expenseApiService.getExpenses(getToken()).await()
            
            val changes = compareLocalAndServer(localExpenses, serverExpenses)
            
            // Sync modifications
            syncChanges(changes.modifications) { modified ->
                expenseApiService.updateExpense(modified.toApiFormat())
            }
            
            // Sync deletions
            syncChanges(changes.deletions) { toDelete ->
                expenseApiService.deleteExpense(toDelete.id)
            }
        }
    }
    
    private fun compareLocalAndServer(
        local: List<Expense>, 
        server: List<Expense>
    ): SyncChanges {
        val changes = SyncChanges()
        
        val localMap = local.associateBy { it.expenseId }
        val serverMap = server.associateBy { it.expenseId }
        
        local.forEach { localExpense ->
            val serverExpense = serverMap[localExpense.expenseId]
            
            if (serverExpense == null) {
                // New on server
                changes.new.add(localExpense)
            } else if (localExpense.lastSyncedAt == null) {
                // Not synced yet
                changes.pending.add(localExpense)
                changes.update.add(serverExpense)
            } else if (serverExpense.updatedAt > localExpense.updatedAt) {
                // Server updated
                changes.update.add(serverExpense)
            }
        }
        
        server.forEach { serverExpense ->
            if (!localMap.containsKey(serverExpense.expenseId)) {
                // New locally
                changes.local.add(serverExpense)
            }
        }
        
        return changes
    }
}

data class SyncChanges(
    val new: List<Expense> = emptyList(),
    val pending: List<Expense> = emptyList(),
    val update: List<Expense> = emptyList(),
    val local: List<Expense> = emptyList(),
    val deletions: List<Expense> = emptyList()
)
```

### 5. Authentication Flow (High Priority)

#### Auth Storage

```kotlin
// android/app/src/main/kotlin/com/kharcha/tracker/utils/auth/AuthStorage.kt
class AuthStorage @Inject constructor(private val context: Context) {
    private val prefs = context.getSharedPreferences(SHARED_PREFS_NAME, Context.MODE_PRIVATE)
    
    fun setAccessToken(token: String) {
        prefs.edit().putString(ACCESS_TOKEN, token).apply()
    }
    
    fun setRefreshToken(token: String) {
        prefs.edit().putString(REFRESH_TOKEN, token).apply()
    }
    
    fun getAccessToken(): String? = prefs.getString(ACCESS_TOKEN, null)
    fun getRefreshToken(): String? = prefs.getString(REFRESH_TOKEN, null)
    
    fun isLoggedIn(): Boolean = getAccessToken() != null
    
    fun logout() {
        prefs.edit().remove(ACCESS_TOKEN).apply()
    }
    
    fun refreshToken(): Result<AuthTokens> {
        val refreshToken = getRefreshToken() ?: return Result.failure(AuthException("No refresh token"))
        
        val result = apiService.refreshToken(mapOf("refresh_token" to refreshToken))
        
        when (result) {
            is Result.Success -> {
                setAccessToken(result.data.accessToken)
                setRefreshToken(result.data.refreshToken)
                return Result.success(result.data)
            }
            is Result.Failure -> return Result.failure(result.exception())
        }
        
        return Result.failure(AuthException("Refresh token expired"))
    }
}
```

### 6. Sync Flow (High Priority)

#### Auto-Sync on Network Recovery

```kotlin
// android/app/src/main/kotlin/com/kharcha/tracker/utils/AutoSyncManager.kt
class AutoSyncManager {
    init {
        // Register network status changes
        val intentFilter = IntentFilter("android.net.conn.CONNECTIVITY_CHANGE")
        context.registerReceiver(
            intentFilter,
            object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    val networkManager = NetworkManager.getINSTANCE
                    if (networkManager.isOnline() && hasPendingSyncItems()) {
                        triggerSync()
                    }
                }
            }
        )
    }
    
    fun registerForSync() {
        lifecycleRegistry = LifecycleRegistry(this@MainActivity)
        val observer = Observer { status ->
            if (status && hasPendingSyncItems()) {
                syncPendingItems()
            }
        }
        lifecycleRegistry.addObserver(observer)
    }
}

data class SyncItem(val type: SyncType, val data: Any, val action: SyncAction) {
    enum class SyncType {
        EXPENSE, GROUP, SETTLEMENT, CATEGORY, MILESTONE
    }
    
    enum class SyncAction {
        CREATE, UPDATE, DELETE, SYNC
    }
}
```

### 7. Offline-First Implementation

#### Database-first Operations

```kotlin
// ExpenseDao.kt
@Dao
interface ExpenseDao {
    @Query("SELECT * FROM expenses WHERE groupId = :groupId")
    fun getExpensesByGroup(groupId: Long): List<Expense>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(expense: Expense)
    
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertOrUpdate(expense: Expense) {
        val existing = query("SELECT * FROM expenses WHERE id = ?", expense.id)
        if (existing.isEmpty()) {
            insert(expense)
        } else {
            val existingExpense = existing.first
            val updated = existingExpense.copy(updateData)
            update(updated)
        }
    }
    
    @Update
    suspend fun update(expense: Expense)
    
    @Query("DELETE FROM expenses WHERE id = :id")
    suspend fun deleteExpense(id: Long)
    
    @Query("SELECT * FROM expenses WHERE status = 'offline' AND isSynced = 0")
    fun getPendingOfflineExpenses(): List<Expense>
    
    @Query("SELECT * FROM expenses WHERE status IN ('offline', 'pending_sync') AND isSynced = 0")
    fun getSyncQueuedExpenses(): List<Expense>
}
```

#### Conflict Resolution

```kotlin
// Conflict detection and resolution
fun detectConflict(local: Expense, server: Expense): ConflictType {
    return when {
        local.lastSyncedAt == null -> PENDING_SYNC
        server.updatedAt > local.updatedAt -> SERVER_CHANGED
        local.updatedAt > server.updatedAt -> CLIENT_CHANGED
        else -> CONCURRENT
    }
}

enum class ConflictType {
    PENDING_SYNC, SERVER_CHANGED, CLIENT_CHANGED, CONCURRENT
}

fun resolveConflict(local: Expense, server: Expense): Expense {
    return when (detectConflict(local, server)) {
        PENDING_SYNC -> server
        SERVER_CHANGED -> server
        CLIENT_CHANGED -> local
        CONCURRENT -> {
            // Show UI for user to resolve
            // Default: server version
            server
        }
    }
}
```

## UI Implementation

### Screens to Create

```
┌─────────────────────────────────┐
│        Splash Screen            │
│   Loading / "Checking status..."│
└─────────────────────────────────┘

┌─────────────────────────────────┐
│      Login / Sign Up Screen     │
│   [Email Input] [Password Input]│
│      [Login Button]             │
└─────────────────────────────────┘

┌─────────────────────────────────┐
│    Dashboard Home Screen        │
│  ┌─────────┐ ┌─────────┐        │
│  │ Balance │ Net     │            │
│  │ ₹10,000 │ ₹5,000  │            │
│  └─────────┘ └─────────┘        │
│  ┌─────────────────────┐       │
│  │  Active Groups      │       │
│  └─────────────────────┘       │
│  ┌─────┐ ┌─────┐ ┌─────┐       │
│  │Group│ │Group│ │Group│       │
│  └─────┘ └─────┘ └─────┘       │
│  ┌─────────────────────┐       │
│  │  Recent Expenses    │       │
│  └─────────────────────┘       │
└─────────────────────────────────┘

┌─────────────────────────────────┐
│       Expense Details Screen    │
│  Amount: ₹1,250                 │
│  Category: Food (23%)          │
│  Date: 24 Oct 2026            │
│  [Upload Receipt] [Notes]      │
│  ┌─────────────────────┐      │
│  │   Upload Receipt     │      │
│  └─────────────────────┘      │
│  [Submit for Approval]         │
└─────────────────────────────────┘

┌─────────────────────────────────┐
│         Group Screen            │
│  ┌─────────┐ ┌─────────┐       │
│  │Balance  │ 25 Expenses│       │
│  │ ₹25,000 │          │       │
│  └─────────┘ └─────────┘       │
│  [Create Expense]              │
│                                │
│  ┌─────────────────────┐       │
│  │ Income Expenses     │       │
│  └─────────────────────┘       │
│  ┌─────────────────────┐       │
│  │ Pending Settlements │       │
│  └─────────────────────┘       │
│                                │
│  ┌─────┐ ┌─────┐ ┌─────┐      │
│  │Expense│  │Exp │ ...    │      │
│  └─────┘ └─────┘ └─────┘      │
└─────────────────────────────────┘

┌─────────────────────────────────┐
│      Settlement Approval        │
│  Total Received: ₹15,000        │
│  Expenses Covered: ₹15,000      │
│  Status: Pending               │
│  ┌─────────────────────┐       │
│  │ Approve All         │       │
│  └─────────────────────┘       │
│  [Approve] [Reject]            │
└─────────────────────────────────┘
```

## Multi-User Collaboration

### Implementation

```kotlin
// Handle multiple users viewing/editing same expense
data class ExpenseCollaborationInfo(
    val lastSyncTimestamp: Long,
    val lastModifiedBy: String,
    val syncedWithUsers: Set<String>
) {
    fun hasConflict(anotherExpense: Expense): Boolean {
        return this.lastModifiedBy != anotherExpense.lastModifiedBy &&
               (this.lastSyncTimestamp < anotherExpense.lastSyncTimestamp ||
                anotherExpense.lastSyncTimestamp < this.lastSyncTimestamp)
    }
    
    fun isLatest() = this.lastSyncTimestamp > allExpenses.map { it.lastSyncTimestamp }.maxOrNull() ?: 0
}
```

### Sync Strategy for Multi-User

```kotlin
fun syncMultiUserExpenses(groupId: String, userId: String): Job {
    return viewModelScope.launch {
        val startTime = System.currentTimeMillis()
        val userExpenses = getExpenseDao().getExpensesByUser(userId)
        
        // Get all group expenses from server
        val serverExpenses = expenseApiService.getExpenses(userId)
        
        // Filter expenses for this group
        val userGroupExpenses = userExpenses.filter { it.groupId == groupId }
        
        // Compare with server
        val changes = compareExpenses(userGroupExpenses, serverExpenses)
        
        // Send local changes to server
        sendExpenseChanges(changes.localChanges)
        
        // Update local database with server changes
        updateLocalExpenses(changes.serverChanges)
        
        updateBalance()
    }
}
```

## Dependency Configuration

### app/build.gradle (Dependencies)

```groovy
dependencies {
    // Navigation
    implementation("androidx.navigation:navigation-fragment-ktx:2.7.6")
    implementation("androidx.navigation:navigation-ui-ktx:2.7.6")
    
    // Accompanist (Material 3 extensions)
    implementation("com.google.accompanist:accompanist-systemuicontroller:0.34.0")
    implementation("com.google.accompanist:accompanist-permissions:0.34.0")
    
    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0")
    
    // Lifecycle
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.7.0")
    
    // Room
    implementation("androidx.room:room-runtime:2.6.1")
    annotationProcessor("androidx.room:room-compiler:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    
    // Retrofit
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.google.protobuf:protobuf-kotlin-lite:3.25.2")
    
    // Coil for Image Loading
    implementation("io.coil-kt:coil:2.5.0")
    implementation("io.coil-kt:coil-compose:2.5.0")
    
    // Material 3
    implementation("androidx.compose.material3:material3:1.2.1")
    implementation("androidx.compose.material3:material3-window-size-class:1.2.1")
    
    // DataStore
    implementation("androidx.datastore:datastore-preferences:1.0.0")
    
    // Image Loading
    implementation("io.coil-kt:coil-base:2.5.0")
    implementation("io.coil-kt:coil-gif:2.5.0")
    implementation("io.coil-kt:coil-diskcache:2.5.0")
    implementation("io.coil-kt:coil-network-okhttp:2.5.0")
    
    // OkHttp
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    
    // Gson
    implementation("com.google.code.gson:gson:2.10.1")
    
    // WorkManager
    implementation("androidx.work:work-runtime-ktx:2.9.0")
    
    // Ktor
    implementation("io.ktor:ktor-client-android:2.3.11")
    implementation("io.ktor:ktor-client-content-negotiation:2.3.11")
    implementation("io.ktor:ktor-serialization-gson:2.3.11")
    
    // Navigation Compose
    implementation("androidx.navigation:navigation-compose:2.7.6")
    implementation("androidx.hilt.navigation.compose:1.1.0")
    
    // Permissions
    implementation("com.google.accompanist:accompanist-permissions:0.34.0")
    
    // Hilt
    implementation("com.google.dagger:hilt-android:2.49")
    implementation("androidx.hilt:hilt-navigation-compose:1.1.0")
    implementation("androidx.hilt:hilt-work:1.1.0")
    
    // DataStore
    implementation("androidx.datastore:datastore-preferences:1.0.0")
    
    // Accompanist Permissions
    implementation("com.google.accompanist:accompanist-permissions:0.34.0")
}
```

### Proguard/R8 Rules

```proguard
# Keep generated code
-keep class com.kharcha.tracker.models.** { *; }
-keep class com.kharcha.tracker.api.** { *; }
-dontwarn com.google.protobuf.**
-dontwarn retrofit2.**
-dontwarn okhttp3.**

# Keep Retrofit and Gson
-keepattributes Signature
-keepattributes *Annotation*
-keep class retrofit2.** { *; }
-dontwarn kotlin.Unit
-dontwarn retrofit2.KotlinExtensions

# Keep Coil
-keep class coil.imageprovider.** { *; }
-keep class coil.drawable.** { *; }

# Keep Hilt
-keep @androidx.annotation.Retention(androidx.annotation.RetentionPolicy.SOURCE) class androidx.lifecycle.ProcessLifecycleOwner\$LifecycleEventDispatcher

# Keep Room
-keep class androidx.room.** { *; }
-dontwarn androidx.room.**

# Keep Kotlin coroutines
-keep class kotlinx.coroutines.** { *; }
```

## Implementation Checklist

### Phase 1: Setup
- [ ] Configure API URLs in NetworkManager
- [ ] Add all dependencies to build.gradle
- [ ] Apply Room dependency
- [ ] Set up Hilt in AndroidManifest.xml
- [ ] Create workManager configuration

### Phase 2: Database
- [ ] Create all entity classes
- [ ] Create all DAO classes
- [ ] Create Database class
- [ ] Create DatabaseManager
- [ ] Add migrations if schema changes
- [ ] Test database operations

### Phase 3: Authentication
- [ ] Implement AuthStorage with DataStore
- [ ] Implement NetworkModule with interceptors
- [ ] Implement AuthApiService
- [ ] Handle login/signup/logout
- [ ] Handle token refresh
- [ ] Handle offline authentication
- [ ] Test auth flow

### Phase 4: Groups
- [ ] Implement GroupRepository
- [ ] Implement GroupApiService
- [ ] Create Group UI screens
- [ ] Test group CRUD operations
- [ ] Test group balance calculations

### Phase 5: Expenses
- [ ] Implement ExpenseRepository
- [ ] Implement ExpenseApiService
- [ ] Create Expense UI screens
- [ ] Test expense CRUD operations
- [ ] Implement receipt upload
- [ ] Implement payment modes
- [ ] Test offline expense creation
- [ ] Test sync logic

### Phase 6: Settlements
- [ ] Implement SettlementRepository
- [ ] Implement settlement endpoints
- [ ] Create Settlement UI screens
- [ ] Test settlement flow
- [ ] Implement approval/rejection
- [ ] Test offline settlement

### Phase 7: Multi-User
- [ ] Implement user sync
- [ ] Handle concurrent modifications
- [ ] Test conflict resolution
- [ ] Test group-level collaboration

### Phase 8: Categories & Milestones
- [ ] Implement CategoryRepository
- [ ] Implement MilestoneRepository
- [ ] Test category endpoints
- [ ] Test milestone tracking

### Phase 9: Reports
- [ ] Implement ReportRepository
- [ ] Create report screens
- [ ] Export functionality
- [ ] Test report generation

### Phase 10: Polish
- [ ] Add loading states
- [ ] Add error handling with Toast
- [ ] Add sync status indicators
- [ ] Add offline mode indicator
- [ ] Performance optimization
- [ ] UX improvements

## Testing

### Unit Tests
```kotlin
@Test
fun testExpenseCalculation() {
    val expense = Expense(
        amount = 1000.0,
        taxRate = 18.0,
        bankCharges = 50.0
    )
    
    val expected = 1000.0 + (1000.0 * 0.18) - 50.0
    assertTrue(BigDecimal(expense.getFinalAmount()) == BigDecimal(expected))
}
```

### Mock Tests
```kotlin
@Test
fun testExpenseRepository_createExpense() = runTest {
    val mockApiService = mockk<ExpenseApiService>()
    val mockExpenseRepo = ExpenseRepository(mockApiService)
    
    whenever(mockApiService.createExpense(any(), any())).thenReturn(
        Response.success(mockExpense)
    )
    
    val result = mockExpenseRepo.createExpense(expense)
    assertTrue(result.isSuccess())
    assertEquals(mockExpense.id, result.data().id)
}
```

### Integration Tests
```kotlin
@Test
fun testExpenseFullFlow() = runTest {
    val expense = createExpense(...)
    submitExpense(expense)
    val updatedExpense = getExpenseById(expense.id)
    
    assertEquals("SUBMITTED", updatedExpense.status)
}
```

## Debug/Logging

```kotlin
class ApiLogger {
    fun log(apiName: String, endpoint: String, status: Int, duration: Long, exception: Throwable?) {
        val timestamp = Date().toString()
        val log = """
            $timestamp
            $apiName
            $endpoint
            Status: $status
            Duration: ${duration}ms
            ${if (exception != null) "Error: ${exception.message}" else "Success"}
        """.trimIndent()
        
        println(log)
    }
}

// Add to NetworkModule
val apiLogger = ApiLogger()
```

## Production Checklist

- [ ] Review all API endpoints
- [ ] Test all network conditions
- [ ] Test offline scenarios
- [ ] Test conflict resolution
- [ ] Add proper error messages
- [ ] Add proper validation
- [ ] Test on different devices
- [ ] Performance testing
- [ ] Security review
- [ ] Privacy review

## Troubleshooting

### Token Refresh Fails
```kotlin
// Add exponential backoff
fun refreshTokenWithBackoff(): Job {
    return job {
        var attempt = 0
        while (attempt < 3) {
            try {
                return@job refreshToken()
            } catch (e: Exception) {
                attempt++
                delay(2000L * attempt) // 2s, 4s, 6s
            }
        }
        return@job throw AuthException("Failed to refresh token")
    }
}
```

### Sync Loop Issues
```kotlin
// Track sync state to prevent infinite loop
fun isSyncInProgress(): Boolean = isSyncing
fun markSyncComplete() {
    isSyncing = false
    pendingSyncItems.clear()
}
```

### Database Lock
```kotlin
// Use transaction for bulk operations
@Transaction
suspend fun bulkInsert(expenses: List<Expense>) {
    db.beginTransaction()
    try {
        expenses.forEach { insert(it) }
        db.setTransactionSuccessful()
    } finally {
        db.endTransaction()
    }
}
```

---

This document provides a complete roadmap for implementing the Android app. Start with Phase 1 and work systematically through each phase, testing thoroughly at each step.