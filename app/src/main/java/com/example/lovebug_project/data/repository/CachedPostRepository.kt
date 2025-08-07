package com.example.lovebug_project.data.repository

import android.content.Context
import com.example.lovebug_project.data.supabase.models.Post
import com.example.lovebug_project.data.supabase.models.PostWithProfile
import com.example.lovebug_project.utils.CacheKeys
import com.example.lovebug_project.utils.CacheManager
import com.example.lovebug_project.utils.ErrorReporter

/**
 * Cached wrapper for SupabasePostRepository
 * Provides intelligent caching with cache invalidation strategies
 */
class CachedPostRepository(context: Context) {
    
    private val supabaseRepo = SupabasePostRepository()
    private val cache = CacheManager.getInstance(context)
    
    companion object {
        private const val POSTS_CACHE_TTL = 15 // 15 minutes for post lists
        private const val SINGLE_POST_CACHE_TTL = 30 // 30 minutes for individual posts
        private const val COUNT_CACHE_TTL = 60 // 1 hour for counts
    }
    
    /**
     * Get all posts with intelligent caching
     */
    suspend fun getAllPosts(
        limit: Int = 20,
        offset: Int = 0,
        forceRefresh: Boolean = false
    ): Result<List<Post>> {
        val cacheKey = CacheKeys.postsList(limit, offset)
        
        return if (forceRefresh) {
            // Force refresh - fetch fresh data and update cache
            val result = supabaseRepo.getAllPosts(limit, offset)
            if (result.isSuccess) {
                result.getOrNull()?.let { posts ->
                    cache.put(cacheKey, posts, POSTS_CACHE_TTL)
                }
            }
            result
        } else {
            try {
                // Try cache first, fallback to network
                val cachedPosts = cache.getOrPut(cacheKey, POSTS_CACHE_TTL) {
                    val networkResult = supabaseRepo.getAllPosts(limit, offset)
                    if (networkResult.isFailure) {
                        throw networkResult.exceptionOrNull() 
                            ?: Exception("Failed to fetch posts from network")
                    }
                    networkResult.getOrThrow()
                }
                Result.success(cachedPosts)
            } catch (e: Exception) {
                ErrorReporter.logSupabaseError(
                    "getAllPosts-Cached",
                    e,
                    ErrorReporter.createContext(
                        "limit" to limit,
                        "offset" to offset,
                        "forceRefresh" to forceRefresh
                    )
                )
                Result.failure(e)
            }
        }
    }
    
    /**
     * Get all posts with profiles using intelligent caching
     */
    suspend fun getAllPostsWithProfiles(
        limit: Int = 20,
        offset: Int = 0,
        forceRefresh: Boolean = false
    ): Result<List<PostWithProfile>> {
        val cacheKey = CacheKeys.postsList(limit, offset, "with_profiles")
        
        return if (forceRefresh) {
            // Force refresh - fetch fresh data and update cache
            val result = supabaseRepo.getAllPostsWithProfiles(limit, offset)
            if (result.isSuccess) {
                result.getOrNull()?.let { posts ->
                    cache.put(cacheKey, posts, POSTS_CACHE_TTL)
                }
            }
            result
        } else {
            try {
                // Try cache first, fallback to network
                val cachedPosts = cache.getOrPut(cacheKey, POSTS_CACHE_TTL) {
                    val networkResult = supabaseRepo.getAllPostsWithProfiles(limit, offset)
                    if (networkResult.isFailure) {
                        throw networkResult.exceptionOrNull() 
                            ?: Exception("Failed to fetch posts with profiles from network")
                    }
                    networkResult.getOrThrow()
                }
                Result.success(cachedPosts)
            } catch (e: Exception) {
                ErrorReporter.logSupabaseError(
                    "getAllPostsWithProfiles-Cached",
                    e,
                    ErrorReporter.createContext(
                        "limit" to limit,
                        "offset" to offset,
                        "forceRefresh" to forceRefresh
                    )
                )
                Result.failure(e)
            }
        }
    }
    
    /**
     * Get single post with caching
     */
    suspend fun getPostById(
        postId: Int,
        forceRefresh: Boolean = false
    ): Result<Post?> {
        val cacheKey = CacheKeys.post(postId)
        
        return if (forceRefresh) {
            val result = supabaseRepo.getPostById(postId)
            if (result.isSuccess) {
                result.getOrNull()?.let { post ->
                    cache.put(cacheKey, post, SINGLE_POST_CACHE_TTL)
                }
            }
            result
        } else {
            try {
                val cachedPost = cache.getOrPut(cacheKey, SINGLE_POST_CACHE_TTL) {
                    val networkResult = supabaseRepo.getPostById(postId)
                    if (networkResult.isFailure) {
                        throw networkResult.exceptionOrNull() 
                            ?: Exception("Failed to fetch post from network")
                    }
                    networkResult.getOrThrow()
                }
                Result.success(cachedPost)
            } catch (e: Exception) {
                ErrorReporter.logSupabaseError("getPostById-Cached", e)
                Result.failure(e)
            }
        }
    }
    
    /**
     * Get posts by user with caching
     */
    suspend fun getPostsByUserId(
        userId: String,
        forceRefresh: Boolean = false
    ): Result<List<Post>> {
        val cacheKey = CacheKeys.userPosts(userId)
        
        return if (forceRefresh) {
            val result = supabaseRepo.getPostsByUserId(userId)
            if (result.isSuccess) {
                result.getOrNull()?.let { posts ->
                    cache.put(cacheKey, posts, POSTS_CACHE_TTL)
                }
            }
            result
        } else {
            try {
                val cachedPosts = cache.getOrPut(cacheKey, POSTS_CACHE_TTL) {
                    val networkResult = supabaseRepo.getPostsByUserId(userId)
                    if (networkResult.isFailure) {
                        throw networkResult.exceptionOrNull() 
                            ?: Exception("Failed to fetch user posts from network")
                    }
                    networkResult.getOrThrow()
                }
                Result.success(cachedPosts)
            } catch (e: Exception) {
                ErrorReporter.logSupabaseError("getPostsByUserId-Cached", e)
                Result.failure(e)
            }
        }
    }
    
    /**
     * Create post and invalidate relevant caches
     */
    suspend fun createPost(post: Post): Result<Post> {
        val result = supabaseRepo.createPost(post)
        
        if (result.isSuccess) {
            // Invalidate affected caches
            invalidatePostListCaches()
            invalidateUserPostCache(post.userId)
            invalidateCountCache()
            
            // Cache the new post
            result.getOrNull()?.let { createdPost ->
                cache.put(CacheKeys.post(createdPost.postId), createdPost, SINGLE_POST_CACHE_TTL)
            }
            
            ErrorReporter.logSuccess("createPost-Cached", "Cache invalidation completed")
        }
        
        return result
    }
    
    /**
     * Update post and invalidate caches
     */
    suspend fun updatePost(postId: Int, post: Post): Result<Post> {
        val result = supabaseRepo.updatePost(postId, post)
        
        if (result.isSuccess) {
            // Invalidate affected caches
            invalidatePostListCaches()
            invalidateUserPostCache(post.userId)
            cache.remove(CacheKeys.post(postId))
            
            // Cache updated post
            result.getOrNull()?.let { updatedPost ->
                cache.put(CacheKeys.post(postId), updatedPost, SINGLE_POST_CACHE_TTL)
            }
        }
        
        return result
    }
    
    /**
     * Delete post and invalidate caches
     */
    suspend fun deletePost(postId: Int, userId: String): Result<Unit> {
        val result = supabaseRepo.deletePost(postId)
        
        if (result.isSuccess) {
            // Invalidate all affected caches
            invalidatePostListCaches()
            invalidateUserPostCache(userId)
            invalidateCountCache()
            cache.remove(CacheKeys.post(postId))
        }
        
        return result
    }
    
    /**
     * Get posts count with caching
     */
    suspend fun getPostsCount(forceRefresh: Boolean = false): Result<Long> {
        val cacheKey = CacheKeys.postsCount()
        
        return if (forceRefresh) {
            val result = supabaseRepo.getPostsCount()
            if (result.isSuccess) {
                result.getOrNull()?.let { count ->
                    cache.put(cacheKey, count, COUNT_CACHE_TTL)
                }
            }
            result
        } else {
            try {
                val cachedCount = cache.getOrPut(cacheKey, COUNT_CACHE_TTL) {
                    val networkResult = supabaseRepo.getPostsCount()
                    if (networkResult.isFailure) {
                        throw networkResult.exceptionOrNull() 
                            ?: Exception("Failed to fetch posts count from network")
                    }
                    networkResult.getOrThrow()
                }
                Result.success(cachedCount)
            } catch (e: Exception) {
                ErrorReporter.logSupabaseError("getPostsCount-Cached", e)
                Result.failure(e)
            }
        }
    }
    
    /**
     * Clear all post-related caches
     */
    suspend fun clearCache() {
        cache.clearAll()
        ErrorReporter.logSuccess("ClearCache", "All post caches cleared")
    }
    
    /**
     * Get cache statistics
     */
    fun getCacheStats() = cache.getStats()
    
    // Private cache invalidation helpers
    private suspend fun invalidatePostListCaches() {
        // In a more sophisticated implementation, you might track which
        // list cache keys exist and selectively invalidate them
        // For now, we'll rely on TTL expiration
    }
    
    private suspend fun invalidateUserPostCache(userId: String) {
        cache.remove(CacheKeys.userPosts(userId))
    }
    
    private suspend fun invalidateCountCache() {
        cache.remove(CacheKeys.postsCount())
    }
}