package com.example.lovebug_project.data.repository

import android.content.Context

/**
 * Supabase Repository Manager
 * 모든 Supabase 리포지토리를 관리하는 중앙 관리자
 */
class SupabaseRepositoryManager private constructor(context: Context) {
    
    companion object {
        @Volatile
        private var INSTANCE: SupabaseRepositoryManager? = null
        
        fun getInstance(context: Context): SupabaseRepositoryManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SupabaseRepositoryManager(context.applicationContext).also { INSTANCE = it }
            }
        }
        
        // Legacy access for backward compatibility - lazy to prevent initialization issues
        val authRepository by lazy { SupabaseAuthRepository() }
        val expenseRepository by lazy { SupabaseExpenseRepository() }
    }
    
    // Enhanced repositories with caching
    val cachedPostRepository = CachedPostRepository(context)
    val imageRepository = SupabaseImageRepository()
    val userRepository = SupabaseUserRepository()
    
    // Direct access repositories
    val postRepository = SupabasePostRepository()
    
    /**
     * Clear all caches - useful for logout or data refresh
     */
    suspend fun clearAllCaches() {
        cachedPostRepository.clearCache()
    }
    
    /**
     * Get system health status
     */
    fun getHealthStatus(): RepositoryHealth {
        val cacheStats = cachedPostRepository.getCacheStats()
        return RepositoryHealth(
            cacheStats = cacheStats,
            isHealthy = true
        )
    }
}

/**
 * Repository system health information
 */
data class RepositoryHealth(
    val cacheStats: com.example.lovebug_project.utils.CacheManager.CacheStats,
    val isHealthy: Boolean
)