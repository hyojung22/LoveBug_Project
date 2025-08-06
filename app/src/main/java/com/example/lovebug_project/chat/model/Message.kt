package com.example.lovebug_project.chat.model

data class Message(
    val messageId: String = "",
    val senderId: String = "",
    val text: String = "",
    val timestamp: Long = 0L,
    val chatRoomId: String = "",
    val likedBy: MutableList<String> = mutableListOf(), // 내부 로직용으로 유지
    var likeCount: Int = 0,                             // UI에 표시 안함, 내부 로직용으로 유지 가능
    var isLikedByCurrentUser: Boolean = false
) {
    // constructor() : this("", "", "", 0L, "", mutableListOf(), 0, false) // For Firebase
}
