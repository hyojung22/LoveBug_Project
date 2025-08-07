package com.example.lovebug_project.data.supabase.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Chat room information with participant details
 * Used for displaying chat list with partner information
 */
@Serializable
data class ChatRoomInfo(
    @SerialName("chat_id")
    val chatId: Int,
    
    @SerialName("partner_id")
    val partnerId: String,
    
    @SerialName("partner_nickname")
    val partnerNickname: String,
    
    @SerialName("partner_avatar_url")
    val partnerAvatarUrl: String? = null,
    
    @SerialName("updated_at")
    val updatedAt: String,
    
    @SerialName("created_at")
    val createdAt: String,
    
    // Optional last message info
    val lastMessage: String? = null,
    val lastMessageTimestamp: String? = null
)

/**
 * Partner profile information for chat rooms
 */
@Serializable
data class PartnerProfile(
    @SerialName("id")
    val id: String,
    
    @SerialName("nickname")
    val nickname: String,
    
    @SerialName("avatar_url")
    val avatarUrl: String? = null
)

/**
 * User search result for starting new chats
 */
@Serializable
data class ChatUserSearchResult(
    @SerialName("id")
    val id: String,
    
    @SerialName("nickname")
    val nickname: String,
    
    @SerialName("avatar_url")
    val avatarUrl: String? = null
)