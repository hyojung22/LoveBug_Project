package com.example.lovebug_project.data.repository

import com.example.lovebug_project.data.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.user.UserInfo
import io.github.jan.supabase.auth.user.UserSession
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import io.github.jan.supabase.auth.status.SessionStatus

class SupabaseAuthRepository {
    private val supabase = SupabaseClient.client
    
    /**
     * Sign up a new user with email and password
     */
    suspend fun signUp(
        email: String,
        password: String,
        username: String,
        nickname: String
    ): Result<UserInfo?> {
        return try {
            val result = supabase.auth.signUpWith(Email) {
                this.email = email
                this.password = password
                data = JsonObject(mapOf(
                    "username" to JsonPrimitive(username),
                    "nickname" to JsonPrimitive(nickname),
                    "display_name" to JsonPrimitive(nickname) // Add display_name field
                ))
            }
            Result.success(result)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Sign in with email and password
     */
    suspend fun signIn(email: String, password: String): Result<UserSession> {
        return try {
            supabase.auth.signInWith(Email) {
                this.email = email
                this.password = password
            }
            val session = supabase.auth.currentSessionOrNull()
                ?: throw IllegalStateException("Sign in succeeded but no session found")
            Result.success(session)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Sign out the current user
     */
    suspend fun signOut(): Result<Unit> {
        return try {
            supabase.auth.signOut()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Get current user session
     */
    fun getCurrentSession(): UserSession? {
        return supabase.auth.currentSessionOrNull()
    }
    
    /**
     * Get current user
     */
    fun getCurrentUser(): UserInfo? {
        return supabase.auth.currentUserOrNull()
    }
    
    /**
     * Observe authentication state changes
     */
    fun observeAuthState(): Flow<SessionStatus> {
        return supabase.auth.sessionStatus
    }
    
    /**
     * Update user metadata (nickname, profile image, etc.)
     */
    suspend fun updateUserMetadata(
        nickname: String? = null,
        profileImage: String? = null,
        sharedSavingStats: Boolean? = null
    ): Result<UserInfo> {
        return try {
            val updates = mutableMapOf<String, JsonPrimitive>()
            nickname?.let { updates["nickname"] = JsonPrimitive(it) }
            profileImage?.let { updates["profile_image"] = JsonPrimitive(it) }
            sharedSavingStats?.let { updates["shared_saving_stats"] = JsonPrimitive(it) }
            
            val result = supabase.auth.updateUser {
                data = if (updates.isNotEmpty()) JsonObject(updates) else null
            }
            Result.success(result)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Delete user account
     */
    suspend fun deleteAccount(): Result<Unit> {
        return try {
            // Supabase doesn't have a direct delete method in client SDK
            // You would need to call a server-side function or use the admin API
            // For now, we'll just sign out
            supabase.auth.signOut()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}