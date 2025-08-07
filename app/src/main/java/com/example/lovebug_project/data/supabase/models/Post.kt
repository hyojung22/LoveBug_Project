package com.example.lovebug_project.data.supabase.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Supabase Post 테이블 모델
 * 사용자 게시글 정보
 */
@Serializable
data class Post(
    @SerialName("post_id") val postId: Int = 0,
    @SerialName("user_id") val userId: String, // Supabase Auth user UUID
    val title: String,
    val content: String,
    @SerialName("image_url") val image: String? = null, // 게시글 이미지 URL
    @SerialName("created_at") val createdAt: String, // ISO timestamp
    @SerialName("updated_at") val updatedAt: String? = null
)