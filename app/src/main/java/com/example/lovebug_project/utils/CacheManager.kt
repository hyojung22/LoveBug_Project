package com.example.lovebug_project.utils

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.Serializable
import java.util.concurrent.ConcurrentHashMap

/**
 * Multi-level caching system for Supabase data
 * Provides memory cache + persistent storage cache
 */
class CacheManager private constructor(private val context: Context) {
    
    companion object {
        @Volatile
        private var INSTANCE: CacheManager? = null
        
        fun getInstance(context: Context): CacheManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: CacheManager(context.applicationContext).also { INSTANCE = it }
            }
        }
        
        private const val CACHE_PREFS = "supabase_cache"
        const val DEFAULT_TTL_MINUTES = 30
    }
    
    val memoryCache = ConcurrentHashMap<String, CacheEntry<*>>()
    val persistentCache: SharedPreferences = context.getSharedPreferences(CACHE_PREFS, Context.MODE_PRIVATE)
    val mutex = Mutex()
    val json = Json { ignoreUnknownKeys = true }
    
    @Serializable
    data class CacheEntry<T>(
        val data: T,
        val timestamp: Long,
        val ttlMinutes: Int = DEFAULT_TTL_MINUTES
    ) {
        val isExpired: Boolean
            get() = System.currentTimeMillis() - timestamp > ttlMinutes * 60 * 1000
    }
    
    /**
     * Get cached data with type safety
     */
    suspend inline fun <reified T> get(
        key: String,
        fromPersistent: Boolean = true
    ): T? = mutex.withLock {
        // Check memory cache first
        val memoryEntry = memoryCache[key] as? CacheEntry<T>
        if (memoryEntry != null && !memoryEntry.isExpired) {
            ErrorReporter.logSuccess("CacheHit-Memory", "Key: $key")
            return@withLock memoryEntry.data
        }
        
        // Check persistent cache
        if (fromPersistent) {
            val persistentData = persistentCache.getString(key, null)
            if (persistentData != null) {
                try {
                    val entry = json.decodeFromString<CacheEntry<T>>(persistentData)
                    if (!entry.isExpired) {
                        // Restore to memory cache
                        memoryCache[key] = entry
                        ErrorReporter.logSuccess("CacheHit-Persistent", "Key: $key")
                        return@withLock entry.data
                    } else {
                        // Remove expired entry
                        remove(key)
                    }
                } catch (e: Exception) {
                    ErrorReporter.logSupabaseError("CacheDeserialize", e, 
                        ErrorReporter.createContext("key" to key))
                    // Remove corrupted entry
                    remove(key)
                }
            }
        }
        
        null
    }
    
    /**
     * Cache data with TTL
     */
    suspend inline fun <reified T> put(
        key: String,
        data: T,
        ttlMinutes: Int = DEFAULT_TTL_MINUTES,
        toPersistent: Boolean = true
    ) = mutex.withLock {
        val entry = CacheEntry(data, System.currentTimeMillis(), ttlMinutes)
        
        // Store in memory
        memoryCache[key] = entry
        
        // Store in persistent cache
        if (toPersistent) {
            try {
                val serialized = json.encodeToString(entry)
                persistentCache.edit().putString(key, serialized).apply()
                ErrorReporter.logSuccess("CachePut", "Key: $key, TTL: ${ttlMinutes}min")
            } catch (e: Exception) {
                ErrorReporter.logSupabaseError("CacheSerialize", e,
                    ErrorReporter.createContext("key" to key))
            }
        }
    }
    
    /**
     * Remove cached data
     */
    suspend fun remove(key: String) = mutex.withLock {
        memoryCache.remove(key)
        persistentCache.edit().remove(key).apply()
    }
    
    /**
     * Clear all cached data
     */
    suspend fun clearAll() = mutex.withLock {
        memoryCache.clear()
        persistentCache.edit().clear().apply()
        ErrorReporter.logSuccess("CacheClear", "All caches cleared")
    }
    
    /**
     * Clear expired entries
     */
    suspend fun clearExpired() = mutex.withLock {
        val expiredKeys = mutableListOf<String>()
        
        // Check memory cache
        memoryCache.entries.removeAll { (key, entry) ->
            if (entry.isExpired) {
                expiredKeys.add(key)
                true
            } else false
        }
        
        // Check persistent cache
        val editor = persistentCache.edit()
        persistentCache.all.forEach { (key, value) ->
            if (value is String) {
                try {
                    val entry = json.decodeFromString<CacheEntry<Any>>(value)
                    if (entry.isExpired) {
                        editor.remove(key)
                        expiredKeys.add(key)
                    }
                } catch (e: Exception) {
                    // Remove corrupted entries
                    editor.remove(key)
                    expiredKeys.add(key)
                }
            }
        }
        editor.apply()
        
        if (expiredKeys.isNotEmpty()) {
            ErrorReporter.logSuccess("CacheCleanup", "Removed ${expiredKeys.size} expired entries")
        }
    }
    
    /**
     * Get cache statistics
     */
    fun getStats(): CacheStats {
        val memorySize = memoryCache.size
        val persistentSize = persistentCache.all.size
        val expiredCount = memoryCache.values.count { it.isExpired }
        
        return CacheStats(
            memoryEntries = memorySize,
            persistentEntries = persistentSize,
            expiredEntries = expiredCount
        )
    }
    
    @Serializable
    data class CacheStats(
        val memoryEntries: Int,
        val persistentEntries: Int,
        val expiredEntries: Int
    )
    
    /**
     * Get cached data or fetch fresh data if not available
     */
    suspend inline fun <reified T> getOrPut(
        key: String,
        ttlMinutes: Int = 30,
        crossinline fetcher: suspend () -> T
    ): T {
        // Try to get from cache first
        get<T>(key)?.let { return it }
        
        // Fetch fresh data and cache it
        val freshData = fetcher()
        put(key, freshData, ttlMinutes)
        return freshData
    }
}

/**
 * Cache key generators for consistent naming
 */
object CacheKeys {
    fun postsList(limit: Int, offset: Int, suffix: String = "") = 
        if (suffix.isNotEmpty()) "posts_list_${limit}_${offset}_$suffix" else "posts_list_${limit}_$offset"
    fun post(postId: Int) = "post_$postId"
    fun userPosts(userId: String) = "user_posts_$userId"
    fun postsCount() = "posts_count"
    
    // User-specific keys
    fun userProfile(userId: String) = "user_profile_$userId"
    fun userExpenses(userId: String) = "user_expenses_$userId"
    
    // Image cache keys
    fun imageMetadata(imageUrl: String) = "image_meta_${imageUrl.hashCode()}"
}

