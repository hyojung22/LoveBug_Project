package com.example.lovebug_project.data.db.entity

/**
 * Simple data class placeholder for User (Room version backed up as .kt.bak)
 * TODO: Replace with Supabase data models
 */
data class User(
    val userId: Int = 0,
    val email: String,
    val username: String,
    val nickname: String,
    val profileImage: String? = null,
    val createdAt: String = System.currentTimeMillis().toString()
)