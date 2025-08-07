# Database Repository Pattern Usage Guide

## Overview

The `DatabaseRepository` class provides a centralized, async-safe way to access the database. This replaces direct DAO access and eliminates main thread database operations.

## Migration from Direct Database Access

### Before (Old Pattern - ❌ Avoid)
```kotlin
// Direct database access - can cause ANR
val user = MyApplication.database.userDao().login(email, password)
val posts = MyApplication.database.postDao().getAllPosts()
```

### After (New Pattern - ✅ Recommended)
```kotlin
// Async repository access - safe for main thread
class LoginActivity : AppCompatActivity() {
    
    private lateinit var repository: DatabaseRepository
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = MyApplication.repository
        
        // Use coroutines for database operations
        lifecycleScope.launch {
            val user = repository.loginUser(email, password)
            if (user != null) {
                // Login successful
                startActivity(Intent(this@LoginActivity, MainActivity::class.java))
            }
        }
    }
}
```

## Common Usage Patterns

### User Operations
```kotlin
// Login
lifecycleScope.launch {
    val user = repository.loginUser(loginId, password)
    if (user != null) {
        // Handle successful login
    }
}

// Register
lifecycleScope.launch {
    val userId = repository.insertUser(newUser)
    // User created with ID: userId
}
```

### Post Operations
```kotlin
// Get all posts
lifecycleScope.launch {
    val posts = repository.getAllPosts()
    // Update UI with posts
}

// Create new post
lifecycleScope.launch {
    val postId = repository.insertPost(newPost)
    // Post created with ID: postId
}
```

### Chat Operations
```kotlin
// Get user's chats
lifecycleScope.launch {
    val chats = repository.getChatsByUser(userId)
    // Display chat list
}

// Send message
lifecycleScope.launch {
    repository.insertChatMessage(chatMessage)
    // Message sent
}
```

### Expense Tracking
```kotlin
// Add expense
lifecycleScope.launch {
    repository.insertExpense(expense)
    // Expense recorded
}

// Get monthly expenses
lifecycleScope.launch {
    val expenses = repository.getExpensesByMonth(userId, "2025-08")
    // Display expense chart
}
```

## Important Notes

### ✅ Do
- Always use `repository` instead of direct database access
- Wrap database calls in `lifecycleScope.launch` or other coroutine scopes
- Handle exceptions with try-catch blocks
- Use the Repository singleton through `MyApplication.repository`

### ❌ Don't
- Use `MyApplication.database.xxxDao()` directly
- Perform database operations on the main thread
- Block the UI with database operations

### Error Handling Example
```kotlin
lifecycleScope.launch {
    try {
        val user = repository.loginUser(email, password)
        if (user != null) {
            // Success
        } else {
            // Invalid credentials
        }
    } catch (e: Exception) {
        // Handle database error
        Log.e("LoginActivity", "Database error", e)
    }
}
```

## Benefits

1. **No ANR Risk**: All operations run in background threads
2. **Consistent API**: Uniform interface for all database operations
3. **Better Testing**: Easy to mock repository for unit tests
4. **Type Safety**: Kotlin coroutines with suspend functions
5. **Error Handling**: Centralized exception handling
6. **Future-Proof**: Easy to add caching, validation, or other features