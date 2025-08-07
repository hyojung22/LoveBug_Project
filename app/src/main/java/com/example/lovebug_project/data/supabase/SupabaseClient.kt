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
        android.util.Log.d("SupabaseClient", "🔧 ==> Creating Supabase client...")
        android.util.Log.d("SupabaseClient", "Supabase URL: ${SUPABASE_URL}")
        android.util.Log.d("SupabaseClient", "Anon Key: ${SUPABASE_ANON_KEY.take(10)}...${SUPABASE_ANON_KEY.takeLast(10)}")
        
        install(Auth) {
            android.util.Log.d("SupabaseClient", "📝 Installing Auth plugin...")
            // Enable PKCE flow for enhanced security
            flowType = FlowType.PKCE
            android.util.Log.d("SupabaseClient", "Auth flow type: PKCE")
            
            // Use memory session manager to prevent Android context initialization issues
            // This prevents "Failed to create default settings for SettingsSessionManager" error
            sessionManager = MemorySessionManager()
            android.util.Log.d("SupabaseClient", "Session manager: MemorySessionManager")
            
            // For production, consider implementing a proper Android-aware session manager
            // that delays initialization until Application context is available
        }
        
        install(Postgrest) {
            android.util.Log.d("SupabaseClient", "🗄️ Installing Postgrest plugin...")
        }
        
        install(Storage) {
            android.util.Log.d("SupabaseClient", "📁 Installing Storage plugin...")
            // Storage configuration for image uploads
        }
        
        install(Realtime) {
            android.util.Log.d("SupabaseClient", "⚡ Installing Realtime plugin...")
            // Realtime configuration for live updates
        }
        
        android.util.Log.d("SupabaseClient", "✅ All Supabase plugins installed")
    }
    
    init {
        android.util.Log.d("SupabaseClient", "🚀 SupabaseClient initialized")
        android.util.Log.d("SupabaseClient", "Client: $client")
        android.util.Log.d("SupabaseClient", "Auth plugin: ${client.pluginManager.getPluginOrNull(io.github.jan.supabase.auth.Auth)}")
        android.util.Log.d("SupabaseClient", "Realtime plugin: ${client.pluginManager.getPluginOrNull(io.github.jan.supabase.realtime.Realtime)}")
        android.util.Log.d("SupabaseClient", "Postgrest plugin: ${client.pluginManager.getPluginOrNull(io.github.jan.supabase.postgrest.Postgrest)}")
    }
}