package com.example.lovebug_project.data.db

import android.app.Application
// lifecycleScope import removed - Application doesn't have lifecycle
import com.example.lovebug_project.data.repository.SupabaseRepositoryManager
import com.example.lovebug_project.data.repository.SupabaseRealtimeRepository
import com.example.lovebug_project.data.repository.SupabaseSavingRepository
// Room import removed - migrated to Supabase
// Unused coroutines imports removed

/**
 * 앱 전체에서 사용할 Supabase 리포지토리들을 전역으로 관리하는 Application 클래스
 * Enhanced with caching, error reporting, and realtime capabilities
 */
class MyApplication : Application() {
    companion object {
        // Global application instance for accessing context
        lateinit var instance: MyApplication
            private set
            
        // Enhanced repository manager with context-aware initialization
        lateinit var repositoryManager: SupabaseRepositoryManager
            private set
        
        // Lazy-initialized realtime repository to prevent static initialization issues
        val realtimeRepository by lazy { SupabaseRealtimeRepository() }
        
        // Legacy compatibility accessors - now lazy to prevent initialization issues
        val authRepository by lazy { SupabaseRepositoryManager.authRepository }
        val expenseRepository by lazy { SupabaseRepositoryManager.expenseRepository }
        val savingRepository by lazy { SupabaseSavingRepository() }
        
        // Enhanced repositories
        val cachedPostRepository get() = repositoryManager.cachedPostRepository
        val postRepository get() = repositoryManager.postRepository
        val imageRepository get() = repositoryManager.imageRepository
        val userRepository get() = repositoryManager.userRepository
        
        // Room database removed - migration to Supabase completed
    }

    override fun onCreate() {
        super.onCreate()
        
        // Set global instance
        instance = this
        
        // Room database initialization removed - migration to Supabase completed
        
        // Initialize enhanced repository manager with application context
        repositoryManager = SupabaseRepositoryManager.getInstance(this)
        
        // Initialize periodic cache cleanup
        // schedulePeriodicCacheCleanup()
        
        // In production, initialize error reporting services here
        // initializeErrorReporting()
        
        // Initialize performance monitoring
        // initializePerformanceMonitoring()
    }
    
    // Removed schedulePeriodicCacheCleanup - Application doesn't have lifecycleScope
    // Cache cleanup will be handled differently if needed in the future
    
    private fun initializeErrorReporting() {
        // Initialize Firebase Crashlytics or other error reporting service
        // FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(true)
    }
    
    private fun initializePerformanceMonitoring() {
        // Initialize Firebase Performance Monitoring or other APM
        // FirebasePerformance.getInstance().isPerformanceCollectionEnabled = true
    }
}

