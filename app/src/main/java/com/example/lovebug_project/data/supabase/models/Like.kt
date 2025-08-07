package com.example.lovebug_project.data.supabase.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Supabase Like 테이블 모델
 * 게시글 좋아요
 */
@Serializable
data class Like(
    @SerialName("like_id") val likeId: Int = 0,
    @SerialName("post_id") val postId: Int,
    @SerialName("user_id") val userId: String, // Supabase Auth user UUID
    @SerialName("created_at") val createdAt: String? = null
)