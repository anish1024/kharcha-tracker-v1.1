# SQLite (Room) Database Management Strategy
# Kharcha Tracker Android App

---

## ⚠️ CRITICAL UNDERSTANDING

### The Database is a **CACHE**, NOT a Mirror

```
┌─────────────────────────────────────────────────────────────────┐
│                        REMOTE API                              │
│  ┌─────────────────────────────────────────────────────────┐  │
│  │ User Data         │ Group Data  │ Expense Data  │ ...  │  │
│  │ (Full, always current)                                    │  │
│  └─────────────────────────────────────────────────────────┘  │
│         ▲                        ▲                            │
│         │                        │                            │
│         ▼                        ▼                            │
│  ┌─────────────────────────────────────────────────────────┐  │
│  │                  LOCAL DATABASE (Room)                  │  │
│  │   ┌─────────────────────────────────────┐              │  │
│  │   │ USER_SESSION (Cached Token)        │              │  │
│  │   │ CACHE_USER_PROFILE                │              │  │
│  │   │ CACHE_GROUP_LIST (Summary)        │              │  │
│  │   │ CACHE_GROUP_DETAILS (Cached)      │              │  │
│  │   │ CACHE_EXPENSES (Selected)         │              │  │
│  │   │ CACHE_SETTLEMENT_ROUNDS (Cached)  │              │  │
│  │   │ CACHE_PAYOUTS (Selected)          │              │  │
│  │   │ CACHE_NOTIFICATIONS (Recent)      │              │  │
│  │   │ CACHE_ACTIVITY_LOGS (Rolling)     │              │  │
│  │   │ UNTRACKED_MODIFICATIONS_QUEUE     │              │  │
│  │   └─────────────────────────────────────┘              │  │
│  │                                                        │  │
│  │   ❌ Invite Codes      → Memory only (short-lived)   │  │
│  │   ❌ Full Audit Logs   → Backend only (unlimited)    │  │
│  │   ❌ Images           → File cache (binary)         │  │
│  │   ❌ Settings         → EncryptedPrefs             │  │
│  └─────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────┘
```

**Key Point:** Only **critical, frequently-accessed** data is cached. Heavy/infrequently-used data stays on the server.

---

## 1. DATABASE SCHEMA (SELECTIVE CACHING)

### Table Categories

| Category | Tables | Purpose | TTL |
|----------|--------|---------|------|
| **Auth** | `user_session` | Access & Refresh tokens | Token expiry time |
| **User** | `cache_user_profile` | Profile data | 7 days |
| **Groups** | `cache_group_list` | Group summaries | 30 days |
| **Groups** | `cache_group_details` | Full group data | 7 days |
| **Expenses** | `cache_expenses` | Expense summaries | 7 days |
| **Settlement** | `cache_settlement_rounds` | Settlement summaries | 30 days |
| **Payouts** | `cache_payouts` | Payout data | 30 days |
| **Notifications** | `cache_notifications` | Recent notifications | 1 hour |
| **Activity** | `cache_activity_logs` | Recent events (rolling) | 30 days / 1000 rows |
| **Offline Queue** | `untracked_modifications` | Pending sync changes | Until synced |
| **Temp** | `scratch_pad` | Temporary processing | Session-based |

### Core Entities

```kotlin
// ──────────────────────────────────────────────────────────────
// Auth Tables
// ──────────────────────────────────────────────────────────────

@Entity(tableName = "user_session")
data class CachedUserSession(
    @PrimaryKey val user_id: Int,
    val access_token: String,
    val refresh_token: String,
    val created_at: Instant,
    val expires_at: Instant,        // When token expires
    val revoked: Boolean = false    // If revoked on backend
)

// ──────────────────────────────────────────────────────────────
// User Tables
// ──────────────────────────────────────────────────────────────

@Entity(tableName = "cache_user_profile")
data class CachedUserProfile(
    @PrimaryKey(val = ["user_id"]) val user_id: Int,
    val name: String,
    val email: String,
    val avatar_url: String?,
    val created_at: Instant,
    val last_modified: Instant,     // For staleness detection
    val cached_at: Instant,         // When cached locally
    val deleted: Boolean = false    // Soft delete
)

// ──────────────────────────────────────────────────────────────
// Group Tables
// ──────────────────────────────────────────────────────────────

@Entity(tableName = "cache_group_list")
data class CachedGroupList(
    @PrimaryKey(val = ["group_id"]) val group_id: Long,
    val user_id: Int,
    val group_id: Long,
    val name: String,
    val status: String,            // OPEN, LOCKED, SETTLED
    val currency: String,
    val created_at: Instant,
    val last_modified: Instant,
    val cached_at: Instant,
    val deleted: Boolean = false
)

@Entity(tableName = "cache_group_details")
data class CachedGroupDetail(
    @PrimaryKey val id: Long,
    @Indexed(val = ["group_id"]) val group_id: Long,
    val name: String,
    val currency: String,
    val status: String,
    val owner_id: Int,
    val member_ids: List<Int>,
    val invite_code: String?,      // Cached for convenience
    val created_at: Instant,
    val last_modified: Instant,
    val cached_at: Instant
)

// ──────────────────────────────────────────────────────────────
// Expense Tables
// ──────────────────────────────────────────────────────────────

@Entity(tableName = "cache_expenses")
data class CachedExpense(
    @PrimaryKey(val = ["trans_id"]) val trans_id: Long,
    @Indexed(val = ["group_id"]) val group_id: Long,
    val amount: Double,
    val description: String,
    val category: String,
    val payer_id: Int,
    val created_at: Instant,
    val last_modified: Instant,
    val cached_at: Instant,
    val is_paid: Boolean = false,
    val deleted: Boolean = false
)

// ──────────────────────────────────────────────────────────────
// Settlement Tables
// ──────────────────────────────────────────────────────────────

@Entity(tableName = "cache_settlement_rounds")
data class CachedSettlementRound(
    @PrimaryKey val id: Long,
    @Indexed(val = ["group_id"]) val group_id: Long,
    val round_number: Int,
    val start_date: Instant,
    val end_date: Instant,
    val status: String,
    val created_at: Instant,
    val last_modified: Instant
)

@Entity(tableName = "cache_settlement_calcs")
data class CachedSettlementCalc(
    @PrimaryKey val id: Long,
    @Indexed(val = ["round_id"]) val round_id: Long,
    @Indexed(val = ["user_id"]) val user_id: Int,
    val due_amount: Double,
    val payout_id: Long?,
    val created_at: Instant,
    val last_modified: Instant
)

// ──────────────────────────────────────────────────────────────
// Payout Tables
// ──────────────────────────────────────────────────────────────

@Entity(tableName = "cache_payouts")
data class CachedPayout(
    @PrimaryKey val id: Long,
    @Indexed(val = ["group_id"]) val group_id: Long,
    val payor_id: Int,
    val payee_id: Int,
    val amount: Double,
    val status: String,            // PENDING, PAID, SETTLED, FAILED, etc.
    val created_at: Instant,
    val last_modified: Instant,
    val confirmed_by: Int?,
    val linked_expense_ids: List<Long>?
)

// ──────────────────────────────────────────────────────────────
// Notification Table
// ──────────────────────────────────────────────────────────────

@Entity(tableName = "cache_notifications")
data class CachedNotification(
    @PrimaryKey val id: Long,
    @Indexed(val = ["user_id"]) val user_id: Int,
    val type: String,             // See types below
    val title: String,
    val message: String,
    val group_id: Long?,
    val read: Boolean = false,
    val created_at: Instant,
    val expires_at: Instant?
)

// Notification Types
// GROUP_MEMBERS_ADDED, GROUP_MEMBERS_LEFT, GROUP_CREATED,
// GROUP_DELETED, GROUP_LOCKED, GROUP_SETTLED, GROUP_UNLOCKED,
// GROUP_INVITE_ACCEPTED, GROUP_INVITE_DECLINED

// ──────────────────────────────────────────────────────────────
// Activity Table (Rolling Window)
// ──────────────────────────────────────────────────────────────

@Entity(tableName = "cache_activity_logs")
data class CachedActivityLog(
    @PrimaryKey val id: Long,
    @Indexed(val = ["group_id"]) val group_id: Long,
    val actor_id: Int,
    val action: String,           // CREATED, EXPENSE_ADDED, etc.
    val entity_type: String,      // GROUP, EXPENSE, etc.
    val entity_id: Long,
    val description: String,
    val created_at: Instant,
    val cached_at: Instant
)

// ──────────────────────────────────────────────────────────────
// Offline Queue (Critical for Offline-First)
// ──────────────────────────────────────────────────────────────

@Entity(tableName = "untracked_modifications")
data class UntrackedModification(
    @PrimaryKey val id: String,   // UUID
    @Indexed(val = ["entity_type", "entity_id"]) val entity_type: String,
    val entity_id: Long,
    val action: String,           // CREATE, UPDATE, DELETE
    val payload: String,          // JSON with changes
    val created_at: Instant,
    val sync_status: String,      // PENDING, SYNCED, FAILED, RETRY
    val retry_count: Int = 0
)
```

---

## 2. STALENESS DETECTION

### Strategy: Timestamp-Based Comparison

Every cached entity has a `last_modified` timestamp. When data is fetched:

```kotlin
fun isEntityStale(local: LocalEntity, remote: RemoteEntity): Boolean {
    val localModified = local.last_modified
    val remoteCreated = remote.created_at
    
    return localModified < remoteCreated
}
```

### Entity Relationship Cascade

If a parent entity changes, dependent children must be marked stale:

```kotlin
fun markRelatedEntitiesStale(parentId: Long, parentType: String) {
    when (parentType) {
        "GROUP" -> {
            markStale("cache_group_details", parentId)
            markStale("cache_expenses", parentId)  // Filter by group_id
            markStale("cache_settlement_rounds", parentId)
            markStale("cache_payouts", parentId)
        }
        "USER" -> {
            markStale("cache_user_profile", parentId)
            markStale("cache_group_list", parentId)  // Filter by user_id
        }
        "EXPENSE" -> {
            markStale("cache_expenses", parentId)
        }
    }
}
```

---

## 3. CACHE REFRESH MECHANISMS

### Trigger 1: Network Availability (Background Sync)

```kotlin
class SyncWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork() {
        // Priority 1: Check Auth Status
        if (tokenManager.isTokenExpired()) {
            refreshOrLogout()
            return
        }
        
        // Priority 2: Sync Offline Changes
        if (syncManager.hasPendingChanges()) {
            syncManager.processPendingChanges()
            return
        }
        
        // Priority 3: Refresh Critical Stale Data
        val staleItems = db.findStaleEntities(user_id)
        fetchFromAPI(staleItems)
        db.applyUpdates()
    }
    
    override fun onSuccess(): Boolean {
        // Mark sync as complete
        return true
    }
    
    override fun onTimeout(): Boolean {
        // Retry mechanism
        return if (retryCount < MAX_RETRIES) {
            markForRetry()
            true
        } else false
    }
}
```

### Trigger 2: App Resume

```kotlin
fun onResume() {
    if (!workManager.isSyncInProgress()) {
        startBackgroundSync()
    }
}

fun startBackgroundSync() {
    val syncJob = WorkManager.getInstance(context)
        .beginUniqueWork("kharcha-sync", WorkListType.LOAD, syncData)
        .addBackOffCriteria(BackOffCriteria(10000, 3))
        .build()
    
    WorkManager.getInstance(context).enqueue(syncJob)
}
```

### Trigger 3: Manual Pull-to-Refresh

```kotlin
@Composable
fun PullToRefreshScope.ExpensesScreen(
    expenses: List<CachedExpense>,
    onRefresh: () -> Unit
) {
    val isRefreshing = remember { mutableStateOf(false) }
    
    LaunchedEffect(isRefreshing.value) {
        if (isRefreshing.value) {
            onRefresh()
            isRefreshing.value = false
        }
    }
    
    val state = RefreshableState(isRefreshing = isRefreshing)
    
    Refreshable(
        refreshing = isRefreshing,
        onRefreshRequest = {
            onRefresh()
            isRefreshing.value = true
        }
    ) {
        // Rest of the UI
    }
}
```

### Trigger 4: Parent Entity Change

```kotlin
fun onGroupUpdated(newGroup: Group) {
    val oldGroup = readGroupFromCache(newGroup.group_id)
    
    // Check staleness
    if (isStale(oldGroup, newGroup)) {
        // Mark all related entities as stale
        markRelatedExpensesStale(newGroup.group_id)
        markRelatedSettlementsStale(newGroup.group_id)
        markRelatedPayoutsStale(newGroup.group_id)
        markRelatedActivityStale(newGroup.group_id)
    }
}
```

---

## 4. CACHE RETENTION (TTL) Strategy

### TTL Configuration by Data Criticality

| Table | TTL | Rationale |
|-------|-----|-----------|
| `user_session` | Until `expires_at` | Auto-expire with token |
| `cache_user_profile` | 7 days | User rarely changes |
| `cache_group_list` | 30 days | Groups are stable |
| `cache_group_details` | 7 days | Moderate changes expected |
| `cache_expenses` | 7 days | Frequent changes |
| `cache_settlement_rounds` | 30 days | Settlements are periodic |
| `cache_payouts` | 30 days | Payouts are periodic |
| `cache_notifications` | 1 hour | Notifications decay fast |
| `cache_activity_logs` | Rolling (30 days / 1000 rows) | Recent history only |
| `untracked_modifications` | Until synced | Must sync or queue expires |

### Implementation

```kotlin
@Entity(tableName = "cache_group_details")
data class CachedGroupDetail(
    // ... fields ...
    val ttlSeconds: Long = 7 * 24 * 3600L,
    
    @Index
    val cached_at: Instant = Instant.now()
)

fun cleanupExpiredData() {
    val now = Instant.now()
    val oneHourAgo = now.minusSeconds(3600)
    
    // Delete expired notifications
    db.deleteExpired("cache_notifications", oneHourAgo)
    
    // Delete expired users
    db.deleteExpired("cache_user_profile", now.minusSeconds(7 * 24 * 3600))
    
    // Keep only recent activity logs (rolling window)
    db.truncateActivityLogs(now.minusSeconds(30 * 24 * 3600))
}

// WorkManager runs this hourly
fun scheduleCleanupTask() {
    WorkManager.getInstance(context).enqueuePeriodic(
        PeriodicWorkRequestBuilder<CleanupWorker>(1, TimeUnit.HOURS)
    )
}
```

---

## 5. CACHE SIZE LIMITS

### Hard Cap Configuration

```kotlin
object CacheConfig {
    const val MAX_DATABASE_SIZE_BYTES = 50 * 1024 * 1024L  // 50 MB
    
    val TABLE_LIMITS = mapOf(
        "cache_activity_logs" to 1000,       // Most accessed
        "cache_notifications" to 100,        // High churn
        "untracked_modifications" to 500,    // Sync queue
        "cache_group_details" to 50,         // Max groups per user
        "scratch_pad" to 100                 // Temporary
    )
    
    // Criticality priority (1 = delete first, 10 = keep forever)
    val TABLE_PRIORITY = mapOf(
        "user_session" to 10,
        "cache_user_profile" to 9,
        "cache_group_list" to 8,
        "cache_group_details" to 7,
        "cache_expenses" to 6,
        "cache_settlement_rounds" to 5,
        "cache_payouts" to 4,
        "cache_notifications" to 3,
        "cache_activity_logs" to 2,
        "untracked_modifications" to 1,
        "scratch_pad" to 0
    )
}
```

### Size Monitoring & Enforcement

```kotlin
fun getDatabaseSize(db: RoomDatabase): Long {
    // SQLite page count * ~1KB per page (estimate)
    val result = db.query("PRAGMA page_count")
    return result.count[0] * 1024
}

fun checkAndEnforceLimits(db: RoomDatabase) {
    val currentSize = getDatabaseSize(db)
    
    if (currentSize > CacheConfig.MAX_DATABASE_SIZE_BYTES) {
        cleanupAggressively(db)
    }
}

fun cleanupAggressively(db: RoomDatabase) {
    val tableOrder = CacheConfig.TABLE_PRIORITY.entries.sortedBy { it.value }
    
    for ((tableName, priority) in tableOrder) {
        if (getDatabaseSize(db) > CacheConfig.MAX_DATABASE_SIZE_BYTES * 0.9) {
            // Truncate this table
            db.execSQL("DELETE FROM $tableName")
        }
    }
}
```

---

## 6. OFFLINE-FIRST FLOW (Detailed Sequence)

### Scenario A: Create Expense While Offline

```
Step 1: User Opens Add Expense Screen
   ↓
Step 2: App Checks Network Status
   - Network check: OFFLINE
   ↓
Step 3: Show "Offline Mode" Indicator in Top Bar
   ↓
Step 4: User Fills Form
   - Description
   - Amount
   - Category
   - Payer
   - Split configuration
   ↓
Step 5: User Taps "Save"
   - Validate input locally
   - Assign temporary ID
   - Create UntrackedModification record:
     
     INSERT INTO untracked_modifications (
         entity_type = 'EXPENSE',
         entity_id = temp_id,
         action = 'CREATE',
         payload = JSON('{
             "trans_id": "tmp_12345",
             "group_id": 1,
             "amount": 500.0,
             ...
         }')
     )
   ↓
Step 6: UI Shows "Expense Saved (Offline)"
   - Add success message with "Offline" tag
   - Store in temporary composable state
   ↓
Step 7: Network Becomes Available
   ↓
Step 8: WorkManager Triggers SyncWorker
   ↓
Step 9: Fetch Current State (Optimistic Lock)
   - GET /groups/{group_id}
   - Check if group still exists
   - Get current version timestamp
   ↓
Step 10: Conflict Detection
   - Compare untracked modification timestamp
   - Compare server timestamp
   ↓
Step 10a: No Conflict → Proceed
   - POST /expenses with modified payload
   - Receive server response
   - Update untracked_modification status = 'SYNCED'
   - Delete from untracked_modifications
   - Insert actual response into cache tables
   - Generate activity log entry
   ↓
Step 10b: Conflict Detected
   - Show conflict resolution dialog:
   
     ┌─────────────────────────────────┐
     │  CONFLICT: Multiple Changes      │
     │                                 │
     │  Local: Expense created at 10:00 │
     │  Server: Expense deleted at 11:00│
     │                                 │
     │  What would you like to do?     │
     │                                 │
     │  [ Keep My Expense ]            │
     │  [ Accept Server ]              │
     │  [ Merge (if possible) ]        │
     │                                 │
     └─────────────────────────────────┘
   ↓
Step 11: User Resolves Conflict
   - If "Keep":
     * Retransmit to server
     * Server rejects → retry with exponential backoff
     * If server still rejects → delete locally and show error
   ↓
Step 12: UI Updates
   - Expense now shows with real server ID
   - Success toast
   - Offline indicator removed
```

### Scenario B: Fetch Groups While Offline

```
Step 1: User Opens Groups Screen
   ↓
Step 2: Check Cache Hit/Miss
   - Query cache for user's groups
   ↓
Step 3: Cache Hit (Data Exists)
   - Check staleness (last_modified)
   ↓
Step 4: Not Stale
   - Display from cache immediately
   - No network call
   - Show no "Offline" indicator
   - Background: mark as "synced"
   ↓
Step 5: Stale (Older than parent modification)
   - Display cached data with "Outdated" warning
   - Background: trigger refresh
   ↓
Step 6: Cache Miss
   - Show "Loading..."
   - Call API: GET /groups
   - If network error:
     * Show error message
     * Display last cached data (if exists)
     * Queue fetch in untracked_modifications
   ↓
Step 7: Refresh Complete
   - Update cache
   - Replace UI data
   - Sync local changes
   - Mark as synced
```

---

## 7. CONFLICT RESOLUTION STRATEGY

### Conflict Types

| Type | Scenario | Resolution |
|------|----------|------------|
| **Timestamp Conflict** | Same entity modified simultaneously | User chooses or last-writer-wins |
| **Operation Conflict** | Local CREATE vs Server DELETE | Hard conflict, manual resolution |
| **Cascade Conflict** | Parent deleted, children modified | Delete all related children |
| **Queue Overflow** | Too many pending changes | Drop oldest, notify user |

### Conflict Detection Algorithm

```kotlin
sealed class ConflictStatus {
    object NO_CONFLICT : ConflictStatus()
    object TIMESTAMP_MISMATCH : ConflictStatus()
    object OPERATION_MISMATCH : ConflictStatus()
    object QUEUE_OVERFLOW : ConflictStatus()
}

fun detectConflict(
    localChange: UntrackedModification,
    serverState: RemoteEntity
): ConflictStatus {
    return when {
        // Case 1: Both updated, timestamps differ
        localChange.action == "UPDATE" && 
        serverState.last_modified > localChange.created_at -> {
            TIMESTAMP_MISMATCH
        }
        
        // Case 2: Local create, server deleted
        localChange.action == "CREATE" && 
        serverState.action == "DELETE" -> {
            OPERATION_MISMATCH
        }
        
        // Case 3: Parent deleted, child modified
        serverState.parent_action == "DELETE" &&
        localChange.entity_type in serverState.children -> {
            CASCADE_CONFLICT
        }
        
        else -> NO_CONFLICT
    }
}
```

### Conflict Resolution UI

```kotlin
@Composable
fun ConflictDialog(
    entity: String,
    localVersion: RemoteEntity,
    serverVersion: RemoteEntity,
    onResolve: (Resolution) -> Unit
) {
    when (detectConflict(localVersion, serverVersion)) {
        ConflictStatus.NO_CONFLICT -> {
            Toast("No conflict", ToastType.SUCCESS)
        }
        ConflictStatus.TIMESTAMP_MISMATCH -> {
            // Show both versions with diff
            // User chooses
        }
        ConflictOperation -> {
            // Hard conflict, show warning
            // Auto-delete oldest
        }
    }
}
```

---

## 8. MIGRATION STRATEGY

### Versioned Database

```kotlin
@Database(
    entities = [
        CachedUserSession::class,
        CachedUserProfile::class,
        CachedGroupList::class,
        CachedGroupDetail::class,
        CachedExpense::class,
        // ... more as needed
    ],
    version = 2,  // Increment for each change
    exportSchema = true  // Enable migration hints
)
abstract class KharchaTrackerDatabase(
    context: Context
) : RoomDatabase<AppDao>(context)

object Migrations {
    // From version 1 to 2: Add new field
    @Migration(from = 1, to = 2)
    object MigrationV1toV2 : Migration(
        migrateDatabase = { database ->
            val groups = database.queryAll<CachedGroupList>()
            for (group in groups) {
                database.insert("cache_group_list", 
                    group.copy(newField = "default_value"))
            }
        },
        applyDatabaseSchema = { schemaBuilder ->
            schemaBuilder.addColumns(
                tableName = "cache_group_list",
                columnName = "new_field",
                affinity = TypeAffinity.TEXT,
                autoIncrement = false,
                notNull = false
            )
        }
    )
    
    // From version 2 to 3: Split table
    @Migration(from = 2, to = 3)
    object MigrationV2toV3 : Migration {
        // Copy data from table A to tables B and C
        // Drop table A
    }
    
    // From version 3 to 4: Delete table
    @Migration(from = 3, to = 4)
    object MigrationV3toV4 : Migration {
        migrateDatabase = { database ->
            // Migrate data elsewhere
            val data = database.queryAll<CachedNotification>()
            database.insert("cache_notification_v2", data.map { it.toV4() })
        }
        applyDatabaseSchema = { schemaBuilder ->
            schemaBuilder.dropTable("cache_notifications", ignoreIfNotExists = true)
            schemaBuilder.addTable(/* new schema */)
        }
    }
}
```

---

## SUMMARY: RULES AT A GLANCE

| Question | Answer |
|----------|--------|
| **Does SQLite mirror remote API?** | NO - Only critical data cached |
| **What's cached?** | User session, groups, expenses, settlements, payouts, activity, notifications |
| **What's NOT cached?** | Invite codes (memory), full audit logs (server), images (file), settings (Prefs) |
| **How is staleness detected?** | Timestamp comparison (`last_modified` vs `created_at`) + dependency cascade |
| **How is cache refreshed?** | On app resume, network available, pull-to-refresh, parent change |
| **Is there rollover?** | Yes - TTL-based expiry + rolling window for activity logs |
| **Is there a size cap?** | Yes - Hard 50MB limit with priority-based cleanup |
| **How are conflicts resolved?** | Timestamp-based, user-assisted for hard conflicts |
| **What happens offline?** | Local save to queue → Sync when online → Apply or resolve conflicts |

---

## TECHNICAL IMPLEMENTATION SNIPPETS

### Database Constructor with Migrations

```kotlin
@AndroidApplication
class KharchaTrackerApp : Application() {
    companion object {
        @Volatile
        private var instance: KharchaTrackerApp? = null
        
        fun getDatabase(context: Context): KharchaTrackerDatabase {
            return instance?.database ?: KharchaTrackerApp.getInstance()
                ?.apply { database = Room.databaseBuilder(this, KharchaTrackerDatabase::class.java, "kharcha.db")
                    .addMigrations(Migrations::class.java.members.filterIsInstance<Migration>())
                    .addCallback(object : RoomDatabase.Callback() {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            super.onCreate(db)
                            registerBackgroundSync()
                        }
                    })
                    .build()
                }
        }
    }
}
```

### Entity with TTL Handling

```kotlin
@Entity(tableName = "cache_notifications")
data class CachedNotification(
    @PrimaryKey val id: Long,
    val user_id: Int,
    val type: String,
    val title: String,
    val message: String,
    val read: Boolean = false,
    val created_at: Instant,
    @Index
    val expires_at: Instant? = null
)

fun getCachedNotifications(user_id: Int, since: Instant?): List<CachedNotification> {
    val where = mutableMapOf<String, Any>("user_id" to user_id)
    if (since != null) {
        where["created_at"] = since
    }
    if (now() < Instant.now().minusSeconds(3600)) {
        where["created_at"] = where["created_at"] + " AND created_at > ${now() - 3600}"
    }
    return db.query("cache_notifications", where)
}
```

---

**Document Version:** 1.0  
**Last Updated:** October 23, 2026  
**Purpose:** Detailed SQLite/Room database management strategy for Kharcha Tracker Android App
