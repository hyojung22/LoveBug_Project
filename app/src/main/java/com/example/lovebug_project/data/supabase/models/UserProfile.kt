package com.example.lovebug_project.data.supabase.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * User profile data model for Supabase profiles table
 * Contains additional user information like nickname, bio, etc.
 */
@Serializable
data class UserProfile(
    @SerialName("id")
    val id: String = "",
    
    @SerialName("nickname") 
    val nickname: String = "",
    
    @SerialName("bio")
    val bio: String? = null,
    
    @SerialName("avatar_url")
    val avatarUrl: String? = null,
    
    @SerialName("email")
    val email: String? = null,
    
    @SerialName("created_at")
    val createdAt: String = "",
    
    @SerialName("updated_at")
    val updatedAt: String = ""
)

/**
 * Data class for updating user profile information
 * Contains only the fields that can be updated by users
 */
@Serializable
data class UserProfileUpdate(
    @SerialName("nickname")
    val nickname: String? = null,
    
    @SerialName("bio")
    val bio: String? = null,
    
    @SerialName("avatar_url")
    val avatarUrl: String? = null
)

/**
 * Search result for user profiles
 * Used when searching for users by nickname
 */
@Serializable
data class UserProfileSearchResult(
    @SerialName("id")
    val id: String,
    
    @SerialName("nickname")
    val nickname: String,
    
    @SerialName("avatar_url")
    val avatarUrl: String? = null
)