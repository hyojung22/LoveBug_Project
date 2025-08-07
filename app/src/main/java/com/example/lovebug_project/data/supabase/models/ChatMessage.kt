package com.example.lovebug_project.data.supabase.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Supabase ChatMessage 테이블 모델
 * 채팅 메시지
 */
@Serializable
data class ChatMessage(
    @SerialName("message_id") val messageId: Int = 0,
    @SerialName("chat_id") val chatId: Int,
    @SerialName("sender_id") val senderId: String, // Supabase Auth user UUID
    val message: String,
    val timestamp: String, // ISO timestamp
    @SerialName("created_at") val createdAt: String? = null
)