package com.example.lovebug_project.data.supabase.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Supabase Bookmark 테이블 모델
 * 게시글 북마크
 */
@Serializable
data class Bookmark(
    @SerialName("bookmark_id") val bookmarkId: Int = 0,
    @SerialName("post_id") val postId: Int,
    @SerialName("user_id") val userId: String, // Supabase Auth user UUID
    @SerialName("created_at") val createdAt: String? = null
)