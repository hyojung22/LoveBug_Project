package com.example.lovebug_project.utils

import android.content.Context
import com.example.lovebug_project.data.db.MyApplication

/**
 * Helper class to manage authentication transition from Room DB to Supabase
 * Provides methods to get current user ID compatible with existing code
 */
object AuthHelper {
    
    /**
     * Get the current user ID in a format compatible with existing code
     * This method bridges the gap between Room DB integer IDs and Supabase UUID strings
     * 
     * @param context Application context
     * @return User ID as integer, or -1 if not logged in
     */
    fun getCurrentUserId(context: Context): Int {
        val sharedPref = context.getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
        
        // First check if we have a Supabase user ID
        val supabaseUserId = sharedPref.getString("supabase_user_id", null)
        if (supabaseUserId != null) {
            // For now, we'll use a hash of the UUID to generate a consistent integer ID
            // In a real migration, you might want to maintain a mapping table
            return supabaseUserId.hashCode()
        }
        
        // Fall back to old Room DB user ID for backward compatibility
        return sharedPref.getInt("userId", -1)
    }
    
    /**
     * Get the current user's Supabase ID
     * 
     * @param context Application context
     * @return Supabase user ID as string, or null if not logged in
     */
    fun getSupabaseUserId(context: Context): String? {
        val sharedPref = context.getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
        return sharedPref.getString("supabase_user_id", null)
    }
    
    /**
     * Check if user is logged in (either Room DB or Supabase)
     * 
     * @param context Application context
     * @return true if user is logged in, false otherwise
     */
    fun isLoggedIn(context: Context): Boolean {
        val sharedPref = context.getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
        
        // Check Supabase first
        val supabaseUserId = sharedPref.getString("supabase_user_id", null)
        if (supabaseUserId != null) {
            // Also verify with Supabase client
            return MyApplication.authRepository.getCurrentSession() != null
        }
        
        // Fall back to Room DB check
        val roomUserId = sharedPref.getInt("userId", -1)
        return roomUserId != -1
    }
    
    /**
     * Clear all authentication data (logout)
     * 
     * @param context Application context
     */
    suspend fun logout(context: Context) {
        val sharedPref = context.getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
        
        // Sign out from Supabase
        MyApplication.authRepository.signOut()
        
        // Clear all stored user IDs
        sharedPref.edit()
            .remove("supabase_user_id")
            .remove("userId")
            .apply()
    }
    
    /**
     * Get current user information
     * 
     * @return User info from Supabase, or null if not logged in
     */
    fun getCurrentUserInfo(): io.github.jan.supabase.auth.user.UserInfo? {
        return MyApplication.authRepository.getCurrentUser()
    }
}