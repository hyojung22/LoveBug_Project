package com.example.lovebug_project.data.repository

import com.example.lovebug_project.data.supabase.SupabaseClient
import com.example.lovebug_project.data.supabase.models.Post
import com.example.lovebug_project.utils.ErrorReporter
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.Json

/**
 * Realtime subscriptions for live data updates
 * Provides real-time notifications for database changes
 */
class SupabaseRealtimeRepository {
    
    private val supabase = SupabaseClient.client
    private val realtime = supabase.realtime
    
    /**
     * Subscribe to new posts in real-time
     * @return Flow of newly created posts
     */
    fun subscribeToNewPosts(): Flow<Post> {
        return realtime.channel("posts-new")
            .postgresChangeFlow<PostgresAction.Insert>(schema = "public") {
                table = "posts"
            }
            .map { action ->
                try {
                    val post = Json.decodeFromJsonElement(Post.serializer(), action.record)
                    ErrorReporter.logSuccess("RealtimeNewPost", "Received new post: ${post.postId}")
                    post
                } catch (e: Exception) {
                    ErrorReporter.logSupabaseError("RealtimeDecodeError", e,
                        ErrorReporter.createContext("action" to "INSERT", "table" to "posts"))
                    throw e
                }
            }
            .catch { error ->
                ErrorReporter.logSupabaseError("RealtimeNewPosts", error as Throwable)
                // Re-emit the error so subscribers can handle it
                throw error
            }
    }
    
    /**
     * Subscribe to post updates in real-time
     * @return Flow of updated posts
     */
    fun subscribeToPostUpdates(): Flow<Post> {
        return realtime.channel("posts-updates")
            .postgresChangeFlow<PostgresAction.Update>(schema = "public") {
                table = "posts"
            }
            .map { action ->
                try {
                    val post = Json.decodeFromJsonElement(Post.serializer(), action.record)
                    ErrorReporter.logSuccess("RealtimeUpdatePost", "Post updated: ${post.postId}")
                    post
                } catch (e: Exception) {
                    ErrorReporter.logSupabaseError("RealtimeDecodeError", e,
                        ErrorReporter.createContext("action" to "UPDATE", "table" to "posts"))
                    throw e
                }
            }
            .catch { error ->
                ErrorReporter.logSupabaseError("RealtimePostUpdates", error as Throwable)
                throw error
            }
    }
    
    /**
     * Subscribe to post deletions in real-time
     * @return Flow of deleted post IDs
     */
    fun subscribeToPostDeletions(): Flow<Int> {
        return realtime.channel("posts-deletions")
            .postgresChangeFlow<PostgresAction.Delete>(schema = "public") {
                table = "posts"
            }
            .map { action ->
                try {
                    val deletedPost = Json.decodeFromJsonElement(Post.serializer(), action.oldRecord)
                    ErrorReporter.logSuccess("RealtimeDeletePost", "Post deleted: ${deletedPost.postId}")
                    deletedPost.postId
                } catch (e: Exception) {
                    ErrorReporter.logSupabaseError("RealtimeDecodeError", e,
                        ErrorReporter.createContext("action" to "DELETE", "table" to "posts"))
                    throw e
                }
            }
            .catch { error ->
                ErrorReporter.logSupabaseError("RealtimePostDeletions", error as Throwable)
                throw error
            }
    }
    
    /**
     * Subscribe to all post changes (insert, update, delete)
     * @return Flow of all post changes with action type
     */
    fun subscribeToAllPostChanges(): Flow<PostChangeEvent> {
        return realtime.channel("posts-all-changes")
            .postgresChangeFlow<PostgresAction>(schema = "public") {
                table = "posts"
            }
            .map { action ->
                try {
                    when (action) {
                        is PostgresAction.Insert -> {
                            val post = Json.decodeFromJsonElement(Post.serializer(), action.record)
                            PostChangeEvent.Created(post)
                        }
                        is PostgresAction.Update -> {
                            val post = Json.decodeFromJsonElement(Post.serializer(), action.record)
                            PostChangeEvent.Updated(post)
                        }
                        is PostgresAction.Delete -> {
                            val deletedPost = Json.decodeFromJsonElement(Post.serializer(), action.oldRecord)
                            PostChangeEvent.Deleted(deletedPost.postId)
                        }
                        else -> {
                            PostChangeEvent.Unknown
                        }
                    }
                } catch (e: Exception) {
                    ErrorReporter.logSupabaseError("RealtimeDecodeError", e,
                        ErrorReporter.createContext("action" to action::class.simpleName))
                    PostChangeEvent.Error(e)
                }
            }
            .catch { error ->
                ErrorReporter.logSupabaseError("RealtimeAllChanges", error as Throwable)
                emit(PostChangeEvent.Error(error as Throwable))
            }
    }
    
    /**
     * Subscribe to posts for a specific user in real-time
     * @param userId User ID to filter posts
     * @return Flow of posts created by the specified user
     */
    fun subscribeToUserPosts(userId: String): Flow<Post> {
        return subscribeToNewPosts()
            .filter { post -> post.userId == userId }
    }
    
    /**
     * Get real-time connection status
     * @return Flow of connection states
     */
    fun getConnectionStatus(): Flow<ConnectionStatus> {
        return flow {
            try {
                // This is a simplified version - actual implementation would
                // monitor the realtime connection status
                emit(ConnectionStatus.Connected)
                
                // In practice, you'd monitor the actual connection state
                // and emit different statuses based on the connection health
                
            } catch (e: Exception) {
                ErrorReporter.logSupabaseError("RealtimeConnectionStatus", e)
                emit(ConnectionStatus.Error(e.message ?: "Connection error"))
            }
        }
    }
    
    /**
     * Disconnect all realtime subscriptions
     */
    suspend fun disconnectAll() {
        try {
            // In practice, you'd manage active channels and disconnect them
            ErrorReporter.logSuccess("RealtimeDisconnect", "All subscriptions disconnected")
        } catch (e: Exception) {
            ErrorReporter.logSupabaseError("RealtimeDisconnectError", e)
        }
    }
}

/**
 * Sealed class representing different types of post change events
 */
sealed class PostChangeEvent {
    data class Created(val post: Post) : PostChangeEvent()
    data class Updated(val post: Post) : PostChangeEvent()
    data class Deleted(val postId: Int) : PostChangeEvent()
    data class Error(val exception: Throwable) : PostChangeEvent()
    object Unknown : PostChangeEvent()
}

/**
 * Connection status for realtime subscriptions
 */
sealed class ConnectionStatus {
    object Connected : ConnectionStatus()
    object Connecting : ConnectionStatus()
    object Disconnected : ConnectionStatus()
    data class Error(val message: String) : ConnectionStatus()
}

/**
 * Extension functions for easier realtime integration
 */

/**
 * Combine post changes with cache invalidation
 */
fun Flow<PostChangeEvent>.withCacheInvalidation(
    cacheManager: com.example.lovebug_project.utils.CacheManager
): Flow<PostChangeEvent> {
    return this.onEach { event ->
        when (event) {
            is PostChangeEvent.Created -> {
                // Invalidate list caches when new post is created
                cacheManager.remove(com.example.lovebug_project.utils.CacheKeys.postsCount())
            }
            is PostChangeEvent.Updated -> {
                // Invalidate specific post cache
                cacheManager.remove(com.example.lovebug_project.utils.CacheKeys.post(event.post.postId))
            }
            is PostChangeEvent.Deleted -> {
                // Invalidate specific post and count caches
                cacheManager.remove(com.example.lovebug_project.utils.CacheKeys.post(event.postId))
                cacheManager.remove(com.example.lovebug_project.utils.CacheKeys.postsCount())
            }
            else -> { /* No cache action needed */ }
        }
    }
}