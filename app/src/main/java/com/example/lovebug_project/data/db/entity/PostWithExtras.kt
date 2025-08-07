package com.example.lovebug_project.data.db.entity

import java.io.Serializable

/**
 * Simple data class placeholder for PostWithExtras (Room version backed up as .kt.bak)
 * TODO: Replace with Supabase data models
 */
data class PostWithExtras(
    val post: Post,
    val nickname: String,
    val profileImage: String? = null,
    val likeCount: Int = 0,
    val commentCount: Int = 0,
    val isLiked: Boolean = false,
    val isBookmarked: Boolean = false
) : Serializable