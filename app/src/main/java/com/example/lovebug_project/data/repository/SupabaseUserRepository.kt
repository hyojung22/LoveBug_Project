package com.example.lovebug_project.data.repository

import com.example.lovebug_project.data.supabase.SupabaseClient
import com.example.lovebug_project.data.supabase.models.UserProfile
import com.example.lovebug_project.data.supabase.models.UserProfileSearchResult
import com.example.lovebug_project.data.supabase.models.UserProfileUpdate
import com.example.lovebug_project.utils.ErrorReporter
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns

/**
 * Repository for user profile operations using Supabase
 * Handles profile creation, updates, and searching
 */
class SupabaseUserRepository {
    
    private val supabase = SupabaseClient.client
    
    /**
     * Get user profile by user ID
     * @param userId The user ID to get profile for
     * @return UserProfile or null if not found
     */
    suspend fun getUserProfile(userId: String): UserProfile? {
        return try {
            supabase.from("profiles")
                .select {
                    filter { 
                        eq("id", userId)
                    }
                }
                .decodeSingleOrNull<UserProfile>()
                
        } catch (e: Exception) {
            ErrorReporter.logSupabaseError("GetUserProfile", e,
                ErrorReporter.createContext("userId" to userId))
            null
        }
    }
    
    /**
     * Search users by nickname (case-insensitive, partial match)
     * @param nickname The nickname to search for
     * @param limit Maximum number of results to return (default 10)
     * @return List of matching user profiles
     */
    suspend fun searchUsersByNickname(nickname: String, limit: Int = 10): List<UserProfileSearchResult> {
        return try {
            if (nickname.isBlank()) {
                return emptyList()
            }
            
            supabase.from("profiles")
                .select(Columns.list("id", "nickname", "avatar_url")) {
                    filter {
                        ilike("nickname", "%${nickname.trim()}%")
                    }
                    limit(limit.toLong())
                }
                .decodeList<UserProfileSearchResult>()
                
        } catch (e: Exception) {
            ErrorReporter.logSupabaseError("SearchUsersByNickname", e,
                ErrorReporter.createContext("nickname" to nickname, "limit" to limit))
            emptyList()
        }
    }
    
    /**
     * Find user by exact nickname match (case-insensitive)
     * @param nickname The exact nickname to find
     * @return UserProfile or null if not found
     */
    suspend fun getUserByNickname(nickname: String): UserProfile? {
        return try {
            if (nickname.isBlank()) {
                return null
            }
            
            supabase.from("profiles")
                .select {
                    filter {
                        ilike("nickname", nickname.trim())
                    }
                    limit(1L)
                }
                .decodeSingleOrNull<UserProfile>()
                
        } catch (e: Exception) {
            ErrorReporter.logSupabaseError("GetUserByNickname", e,
                ErrorReporter.createContext("nickname" to nickname))
            null
        }
    }
    
    /**
     * Update user profile
     * @param userId The user ID to update profile for
     * @param profileUpdate The profile updates to apply
     * @return Updated UserProfile or null if failed
     */
    suspend fun updateUserProfile(userId: String, profileUpdate: UserProfileUpdate): UserProfile? {
        return try {
            supabase.from("profiles")
                .update(profileUpdate) {
                    filter {
                        eq("id", userId)
                    }
                    select()
                }
                .decodeSingleOrNull<UserProfile>()
                
        } catch (e: Exception) {
            ErrorReporter.logSupabaseError("UpdateUserProfile", e,
                ErrorReporter.createContext("userId" to userId))
            null
        }
    }
    
    /**
     * Create or update user profile
     * @param userProfile The profile to create or update
     * @return UserProfile or null if failed
     */
    suspend fun upsertUserProfile(userProfile: UserProfile): UserProfile? {
        return try {
            supabase.from("profiles")
                .upsert(userProfile) {
                    select()
                }
                .decodeSingleOrNull<UserProfile>()
                
        } catch (e: Exception) {
            ErrorReporter.logSupabaseError("UpsertUserProfile", e,
                ErrorReporter.createContext("userId" to userProfile.id))
            null
        }
    }
    
    /**
     * Check if nickname is available
     * @param nickname The nickname to check
     * @param currentUserId Current user ID (to exclude from check)
     * @return true if nickname is available, false if taken
     */
    suspend fun isNicknameAvailable(nickname: String, currentUserId: String? = null): Boolean {
        return try {
            if (nickname.isBlank()) {
                return false
            }
            
            val query = supabase.from("profiles")
                .select(Columns.list("id")) {
                    filter {
                        ilike("nickname", nickname.trim())
                        // Exclude current user from check
                        currentUserId?.let { 
                            neq("id", it)
                        }
                    }
                    limit(1L)
                }
            
            val result = query.decodeSingleOrNull<Map<String, String>>()
            result == null // Available if no matching profile found
            
        } catch (e: Exception) {
            ErrorReporter.logSupabaseError("IsNicknameAvailable", e,
                ErrorReporter.createContext("nickname" to nickname, "currentUserId" to currentUserId))
            false // Assume not available on error
        }
    }
    
    /**
     * Get all user profiles (for admin purposes)
     * @param limit Maximum number of results to return (default 100)
     * @return List of user profiles
     */
    suspend fun getAllProfiles(limit: Int = 100): List<UserProfile> {
        return try {
            supabase.from("profiles")
                .select {
                    limit(limit.toLong())
                }
                .decodeList<UserProfile>()
                
        } catch (e: Exception) {
            ErrorReporter.logSupabaseError("GetAllProfiles", e,
                ErrorReporter.createContext("limit" to limit))
            emptyList()
        }
    }
}