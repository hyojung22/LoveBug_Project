package com.example.lovebug_project.data.repository

import com.example.lovebug_project.data.db.AppDatabase
import com.example.lovebug_project.data.db.entity.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Repository pattern for centralized database access
 * Provides async database operations and business logic layer
 */
class DatabaseRepository(private val database: AppDatabase) {

    // User operations
    suspend fun insertUser(user: User): Long = withContext(Dispatchers.IO) {
        database.userDao().insert(user)
    }

    suspend fun loginUser(loginId: String, password: String): User? = withContext(Dispatchers.IO) {
        database.userDao().login(loginId, password)
    }

    suspend fun getUserById(userId: Int): User? = withContext(Dispatchers.IO) {
        database.userDao().getUserById(userId)
    }

    suspend fun updateUser(user: User) = withContext(Dispatchers.IO) {
        database.userDao().update(user)
    }

    // Post operations
    suspend fun insertPost(post: Post): Long = withContext(Dispatchers.IO) {
        database.postDao().insert(post)
    }

    suspend fun getAllPosts(): List<Post> = withContext(Dispatchers.IO) {
        database.postDao().getAllPosts()
    }

    suspend fun getPostById(postId: Int): Post = withContext(Dispatchers.IO) {
        database.postDao().getPostById(postId)
    }

    suspend fun getPostsByUser(userId: Int): List<Post> = withContext(Dispatchers.IO) {
        database.postDao().getPostsByUser(userId)
    }

    suspend fun deletePostById(postId: Int) = withContext(Dispatchers.IO) {
        database.postDao().deleteById(postId)
    }

    // Comment operations
    suspend fun insertComment(comment: Comment) = withContext(Dispatchers.IO) {
        database.commentDao().insert(comment)
    }

    suspend fun getCommentsByPost(postId: Int): List<Comment> = withContext(Dispatchers.IO) {
        database.commentDao().getCommentsByPost(postId)
    }

    suspend fun getCommentCountByPost(postId: Int): Int = withContext(Dispatchers.IO) {
        database.commentDao().getCommentCountByPost(postId)
    }

    suspend fun updateComment(comment: Comment) = withContext(Dispatchers.IO) {
        database.commentDao().update(comment)
    }

    suspend fun deleteComment(comment: Comment) = withContext(Dispatchers.IO) {
        database.commentDao().delete(comment)
    }

    // Chat operations
    suspend fun insertChat(chat: Chat) = withContext(Dispatchers.IO) {
        database.chatDao().insert(chat)
    }

    suspend fun getChatsByUser(userId: Int): List<Chat> = withContext(Dispatchers.IO) {
        database.chatDao().getChatsByUser(userId)
    }

    // Chat message operations
    suspend fun insertChatMessage(message: ChatMessage) = withContext(Dispatchers.IO) {
        database.chatMessageDao().insert(message)
    }

    suspend fun getMessagesByChat(chatId: Int): List<ChatMessage> = withContext(Dispatchers.IO) {
        database.chatMessageDao().getMessagesByChat(chatId)
    }

    // Expense operations
    suspend fun insertExpense(expense: Expense) = withContext(Dispatchers.IO) {
        database.expenseDao().insert(expense)
    }

    suspend fun updateExpense(expense: Expense) = withContext(Dispatchers.IO) {
        database.expenseDao().update(expense)
    }

    suspend fun deleteExpense(expense: Expense) = withContext(Dispatchers.IO) {
        database.expenseDao().delete(expense)
    }

    suspend fun getExpensesByMonth(userId: Int, month: String): List<Expense> = withContext(Dispatchers.IO) {
        database.expenseDao().getExpenseByMonth(userId, month)
    }

    suspend fun getExpensesByDate(userId: Int, date: String): List<Expense> = withContext(Dispatchers.IO) {
        database.expenseDao().getExpensesByDate(userId, date)
    }

    suspend fun getExpenseById(id: Int): Expense? = withContext(Dispatchers.IO) {
        database.expenseDao().getExpenseById(id)
    }

    // Like operations
    suspend fun insertLike(like: Like) = withContext(Dispatchers.IO) {
        database.likeDao().insert(like)
    }

    suspend fun deleteLike(like: Like) = withContext(Dispatchers.IO) {
        database.likeDao().delete(like)
    }

    suspend fun deleteLikeByUserAndPost(userId: Int, postId: Int) = withContext(Dispatchers.IO) {
        database.likeDao().deleteLike(userId, postId)
    }

    suspend fun getLikeCountByPost(postId: Int): Int = withContext(Dispatchers.IO) {
        database.likeDao().getLikeCountByPost(postId)
    }

    suspend fun isPostLikedByUser(userId: Int, postId: Int): Boolean = withContext(Dispatchers.IO) {
        database.likeDao().isPostLikedByUser(userId, postId)
    }

    // Bookmark operations
    suspend fun insertBookmark(bookmark: Bookmark) = withContext(Dispatchers.IO) {
        database.bookmarkDao().insert(bookmark)
    }

    suspend fun deleteBookmark(bookmark: Bookmark) = withContext(Dispatchers.IO) {
        database.bookmarkDao().delete(bookmark)
    }

    suspend fun getBookmarksByUser(userId: Int): List<Bookmark> = withContext(Dispatchers.IO) {
        database.bookmarkDao().getBookmarksByUser(userId)
    }

    suspend fun isPostBookmarkedByUser(userId: Int, postId: Int): Boolean = withContext(Dispatchers.IO) {
        database.bookmarkDao().isPostBookmarkedByUser(userId, postId)
    }

    // Saving goal operations
    suspend fun insertSavingGoal(goal: SavingGoal) = withContext(Dispatchers.IO) {
        database.savingGoalDao().insert(goal)
    }

    suspend fun getSavingGoal(userId: Int, month: String): SavingGoal? = withContext(Dispatchers.IO) {
        database.savingGoalDao().getGoal(userId, month)
    }

    // Saving stats operations
    suspend fun insertOrUpdateSavingStats(stats: SavingStats) = withContext(Dispatchers.IO) {
        database.savingStatsDao().insertOrUpdate(stats)
    }

    suspend fun getSavingStatsByUserAndMonth(userId: Int, month: String): List<SavingStats> = withContext(Dispatchers.IO) {
        database.savingStatsDao().getStatsByUserAndMonth(userId, month)
    }

    companion object {
        @Volatile
        private var INSTANCE: DatabaseRepository? = null

        fun getInstance(database: AppDatabase): DatabaseRepository {
            return INSTANCE ?: synchronized(this) {
                val instance = DatabaseRepository(database)
                INSTANCE = instance
                instance
            }
        }
    }
}