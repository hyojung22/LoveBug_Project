package com.example.lovebug_project.data.db.entity

/**
 * Simple data class placeholder for Like (Room version backed up as .kt.bak)
 * TODO: Replace with Supabase data models
 */
data class Like(
    val likeId: Int = 0,
    val postId: Int,
    val userId: Int,
    val createdAt: String = System.currentTimeMillis().toString()
)