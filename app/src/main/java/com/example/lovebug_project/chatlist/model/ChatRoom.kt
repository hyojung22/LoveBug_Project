package com.example.lovebug_project.chatlist.model

data class ChatRoom(
    val roomId: String, // 대화방 고유 ID
    val partnerName: String,
    val lastMessage: String?,
    val timestamp: Long,
    val partnerProfileImageUrl: String? // 상대방 프로필 이미지 URL (선택 사항)
    // val unreadCount: Int // 안 읽은 메시지 수 (선택 사항)
)