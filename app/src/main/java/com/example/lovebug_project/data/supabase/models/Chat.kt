package com.example.lovebug_project.data.supabase.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Supabase Chat 테이블 모델
 * 1:1 채팅방 정보
 */
@Serializable
data class Chat(
    @SerialName("chat_id") val chatId: Int = 0,
    @SerialName("user1_id") val user1Id: String, // Supabase Auth user UUID
    @SerialName("user2_id") val user2Id: String, // Supabase Auth user UUID
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null
)