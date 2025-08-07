package com.example.lovebug_project.data.supabase.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Supabase Comment 테이블 모델
 * 게시글 댓글
 */
@Serializable
data class Comment(
    @SerialName("comment_id") val commentId: Int = 0,
    @SerialName("post_id") val postId: Int,
    @SerialName("user_id") val userId: String, // Supabase Auth user UUID
    val content: String,
    @SerialName("created_at") val createdAt: String? = null
)