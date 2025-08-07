package com.example.lovebug_project.data.supabase.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Data model for Post with Profile information
 * Used when fetching posts with associated user profile data
 */
@Serializable
data class PostWithProfile(
    // Post information
    @SerialName("post_id") val postId: Int = 0,
    @SerialName("user_id") val userId: String,
    val title: String,
    val content: String,
    @SerialName("image_url") val imageUrl: String? = null,
    @SerialName("image_path") val imagePath: String? = null,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String? = null,
    
    // Profile information
    val nickname: String? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null
)