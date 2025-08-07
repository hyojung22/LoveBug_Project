package com.example.lovebug_project.data.db.entity

/**
 * Simple data class placeholder for Comment (Room version backed up as .kt.bak)
 * TODO: Replace with Supabase data models
 */
data class Comment(
    val commentId: Int = 0,
    val postId: Int,
    val userId: Int,
    val content: String,
    val createdAt: String,
    val updatedAt: String? = null
)