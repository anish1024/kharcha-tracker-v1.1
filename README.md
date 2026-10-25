# Kharcha Tracker v2.0 - Android API Integration

## Overview

This project implements a comprehensive Android expense tracking application with full API integration. The app supports:

- **Expense Management**: Track and manage both expenses and income with features like receipts, tax calculation, and payment modes
- **Group Management**: Create and manage multiple groups with budget tracking, expense distribution, and settlements
- **Smart Sync**: Automatic syncing of local data with the API, with conflict resolution and change detection
- **Offline Support**: Full offline functionality with local database and sync when connection is restored
- **Multiple Users**: Support for organizations with multiple users, each with their own expenses and groups

## Project Structure

```
kharcha-tracker-android/
├── android/
│   ├── app/
│   │   ├── src/
│   │   │   └── main/
│   │   │       ├── kotlin/com/kharcha/tracker/
│   │   │       │   ├── di/                 # Dependency injection modules
│   │   │       │   │   └── NetworkModule.kt
│   │   │       │   │       └── RepositoryModule.kt (to be created)
│   │   │       │   ├── api/               # API interfaces
│   │   │       │   │   ├── AuthApiService.kt
│   │   │       │   │   ├── ExpenseApiService.kt
│   │   │       │   │   ├── GroupApiService.kt
│   │   │       │   │   ├── CategoryApiService.kt
│   │   │       │   │   └── MilestoneApiService.kt
│   │   │       │   ├── data/              # Data models and repositories
│   │   │       │   │   └── models/        # Data classes
│   │   │       │   │       ├── Group.kt
│   │   │       │   │       ├── Expense.kt
│   │   │       │   │       ├── User.kt
│   │   │       │   │       ├── Settlement.kt
│   │   │       │   │       ├── Category.kt
│   │   │       │   │       ├── Milestone.kt
│   │   │       │   ├── utils/            # Utility classes
│   │   │       │   │   ├── NetworkManager.kt
│   │   │       │   │   ├── NetworkStatusListener.kt
│   │   │       │   │   └── auth/AuthStorage.kt
│   │   │       │   │       └── rest/      # REST client implementation
│   │   │       │   │           └── RestClient.kt
│   │   │       │   └── database/         # Room database and DAOs
│   │   │       │       │       ├── Database.kt
│   │   │       │       ├── DatabaseManager.kt
│   │   │       │       ├── dao/          # DAO classes
│   │   │       │       └── entities/     # Entity classes
│   │   │       │           ├── User.kt
│   │   │       │           ├── Group.kt
│   │   │       │           ├── Expense.kt
│   │   │       │           ├── Settlement.kt
│   │   │       │           ├── Category.kt
│   │   │       │           └── Milestone.kt
│   │   ├── build.gradle                  # App module dependencies
│   │   └── proguard-rules.pro
│   └── build.gradle                      # Project dependencies
├── ios/                                  # iOS implementation
└── README.md
```

## Key Features

### 1. User Management

```kotlin
// Create user account
fun registerUser(userId: String, username: String, email: String, password: String) {
    // Implementation
}

// Login
fun loginUser(email: String, password: String) {
    // Implementation
}

// Logout
fun logout() {
    // Implementation
}
```

### 2. Group Management

```kotlin
// Create group
fun createGroup(groupId: String, name: String, userId: String, type: String) {
    // Implementation
}

// Get group expenses
fun getGroupExpenses(groupId: String, userId: String, type: String) {
    // Implementation
}

// Create settlement
fun createSettlement(settlementId: String, groupId: String, userId: String, amount: Double) {
    // Implementation
}

// Approve settlement
fun approveSettlement(settlementId: String, groupId: String, userId: String) {
    // Implementation
}
```

### 3. Expense Management

```kotlin
// Create expense
fun createExpense(expenseId: String, groupId: String, userId: String, amount: Double, userId: String) {
    // Implementation
}

// Get expenses
fun getExpenses(groupId: String, userId: String, type: String) {
    // Implementation
}

// Submit for approval
fun submitExpense(expenseId: String, userId: String) {
    // Implementation
}

// Add receipt
fun addReceipt(expenseId: String, userId: String, content: ByteArray, notes: String) {
    // Implementation
}
```

### 4. Offline Support

```kotlin
// When user creates expense offline, mark as "offline"
fun createExpenseOffline() {
    val expense = Expense(...)
    expense.isOnline = false
    expense.isSynced = false
    expense.lastSyncedAt = null
    getExpenseDao().insert(expense)
}

// When app connects online, auto-sync changes
fun syncExpenses(groupId: String, userId: String) {
    // Get all locally created/expired expenses
    // Compare with server timestamps
    // Send PATCH for modified expenses
    // Update local DB with server data
}
```

### 5. Multi-User Collaboration

```kotlin
// Create user
fun createUser(userId: String, username: String, email: String, password: String) {
    // Implementation
}

// Assign group to users
fun assignUserToGroup(groupId: String, userId: String) {
    // Implementation
}

// Sync expenses across users
fun syncGroupExpenses(groupId: String) {
    // Get all users in group
    // For each user, fetch their locally modified expenses
    // Compare with server and sync
}
```

## Implementation Strategy

### Phase 1: API Integration

1. **Set up Retrofit** for API calls
2. **Create API clients** with authentication
3. **Implement endpoints** for user, group, expense, settlement, category, milestone
4. **Handle responses** and error cases

### Phase 2: Local Storage

1. **Set up Room database**
2. **Create DAOs** for CRUD operations
3. **Implement sync logic** for offline support
4. **Handle conflicts** (e.g., two users modifying same expense)

### Phase 3: User Interface

1. **Create screens** for each feature
2. **Implement navigation**
3. **Add UI for multi-user collaboration**
4. **Handle sync status indicators**

### Phase 4: Advanced Features

1. **Receipt scanning** (using device camera)
2. **Auto-calculation** of final amounts
3. **Expense approval workflow**
4. **Budget tracking**
5. **Reports and analytics**

## Data Flow

```
[User] --Login--> [Server]
                |
                v
        [Auth Token]
                |
                v
[Local DB] <-- [Sync] --> [Server]
  |              ^
  |              |
  |           [API Calls]
  v
[UI Updates]
```

## API Endpoints

### Authentication
- POST /api/v1/auth/login
- POST /api/v1/auth/signup
- POST /api/v1/auth/logout
- POST /api/v1/auth/refresh

### Groups
- GET /api/v1/groups
- POST /api/v1/groups
- GET /api/v1/groups/{groupId}
- PUT /api/v1/groups/{groupId}
- DELETE /api/v1/groups/{groupId}

### Expenses
- GET /api/v1/expenses
- POST /api/v1/expenses
- GET /api/v1/expenses/{expenseId}
- PUT /api/v1/expenses/{expenseId}
- DELETE /api/v1/expenses/{expenseId}

### Settlements
- POST /api/v1/groups/{groupId}/settlement
- GET /api/v1/groups/{groupId}/settlements
- POST /api/v1/groups/{groupId}/settlement/{settlementId}/approve
- GET /api/v1/groups/{groupId}/settlements/approvals

### Categories
- GET /api/v1/categories
- POST /api/v1/categories
- PUT /api/v1/categories/{categoryId}

### Milestones
- GET /api/v1/milestones
- POST /api/v1/milestones

### Reports
- GET /api/v1/expenses/category/{categoryId}/date/{startDate}-{endDate}

## Database Schema

### Users Table
- id: Long
- userId: String
- username: String
- email: String
- role: String

### Groups Table
- id: Long
- groupId: String
- name: String
- userId: String

### Expenses Table
- id: Long
- expenseId: String
- groupId: Long
- name: String
- amount: Double
- status: String
- date: String

### Settlements Table
- id: Long
- settlementId: String
- groupId: Long
- amount: Double
- status: String

### Categories Table
- id: Long
- categoryId: String
- name: String
- userId: String

### Milestones Table
- id: Long
- milestoneId: String
- groupId: Long
- title: String
- budget: Double

## Sync Logic

```kotlin
fun syncGroup(groupId: String): Job {
    return viewModelScope.launch {
        // 1. Get all local expenses for the group
        val localExpenses = getExpenseDao().getExpensesByGroup(groupId)
        
        // 2. Fetch from server
        val serverExpenses = fetchGroupExpenses(groupId)
        
        // 3. Compare and diff
        val changes = compareLocalAndServerExpenses(localExpenses, serverExpenses)
        
        // 4. Send modifications to server
        if (changes.modifications.isNotEmpty()) {
            updateExpenses(changes.modifications)
        }
        
        // 5. Update local DB
        saveExpenses(changes.updates)
        
        // 6. Handle deletions
        deleteExpenses(changes.deletions)
        
        updateUi(serverExpenses)
    }
}
```

## Error Handling

```kotlin
sealed class ApiResult<T> {
    data class Success<T>(val data: T) : ApiResult<T>()
    data class Error(val code: Int, val message: String) : ApiResult<T>()
}

fun handleApiResponse(result: ApiResult<T>): T? {
    return when (result) {
        is ApiResult.Success -> result.data
        is ApiResult.Error -> throw ApiException(result.message)
        else -> null
    }
}
```

## Testing Strategy

1. **Unit Tests** for utilities and data processing
2. **Mock Tests** for API calls using mocked responses
3. **UI Tests** for screens and user interactions
4. **Sync Tests** to verify offline and online data consistency

## Future Enhancements

1. **Dark mode** for better visual experience
2. **Custom reports** with date filters and categories
3. **Push notifications** for approval requests
4. **Receipt OCR** for automatic expense entry
5. **Multiple currencies** support
6. **Expense categorization** AI suggestions

## Getting Started

1. Clone the repository
2. Install dependencies
3. Configure API URL in [NetworkManager](android/app/src/main/kotlin/com/kharcha/tracker/utils/NetworkManager.kt)
4. Run on emulator or device
5. Login or register
6. Start creating groups and expenses

## Development Notes

- Use Kotlin Coroutines for async operations
- Implement proper error handling with Toast notifications
- Add loading states for all operations
- Validate inputs before sending to API
- Handle concurrent modifications carefully
- Implement exponential backoff for network retries

## License

[Add your license here]

## Credits

- Android API: [Kharcha Tracker API Documentation](./android_frontend_plan.md)
- Design: [Material Design 3](https://m3.material.io/)