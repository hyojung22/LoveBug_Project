package com.example.lovebug_project.data.supabase

import com.example.lovebug_project.BuildConfig
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.FlowType
import io.github.jan.supabase.auth.MemorySessionManager
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.storage.Storage
import io.github.jan.supabase.realtime.Realtime

object SupabaseClient {
    // Secure credential access via BuildConfig
    private val SUPABASE_URL = BuildConfig.SUPABASE_URL
    private val SUPABASE_ANON_KEY = BuildConfig.SUPABASE_ANON_KEY
    
    val client = createSupabaseClient(
        supabaseUrl = SUPABASE_URL,
        supabaseKey = SUPABASE_ANON_KEY
    ) {
        install(Auth) {
            // Enable PKCE flow for enhanced security
            flowType = FlowType.PKCE
            
            // Use memory session manager to prevent Android context initialization issues
            // This prevents "Failed to create default settings for SettingsSessionManager" error
            sessionManager = MemorySessionManager()
            
            // For production, consider implementing a proper Android-aware session manager
            // that delays initialization until Application context is available
        }
        install(Postgrest)
        install(Storage) {
            // Storage configuration for image uploads
        }
        install(Realtime) {
            // Realtime configuration for live updates
        }
    }
}