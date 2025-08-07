package com.example.lovebug_project.data.db.entity

/**
 * Simple data class placeholder for Post (Room version backed up as .kt.bak)
 * TODO: Replace with Supabase data models
 */
data class Post(
    val postId: Int = 0,
    val userId: Int,
    val title: String,
    val content: String,
    val image: String? = null,
    val createdAt: String
)