package com.example.lovebug_project.chat.model

import java.util.UUID

/**
 * 로컬 메시지 데이터 클래스
 * Optimistic UI와 실시간 동기화를 위한 상태 관리 포함
 */
data class Message(
    val messageId: String = "",
    val senderId: String = "",
    val senderName: String = "",
    val senderProfileImageUrl: String = "",
    val text: String = "",
    val timestamp: Long = 0L,
    val chatRoomId: String = "",
    val likedBy: MutableList<String> = mutableListOf(),
    var likeCount: Int = 0,
    var isLikedByCurrentUser: Boolean = false,
    
    // Optimistic UI를 위한 상태 필드들
    val isPending: Boolean = false,           // 전송 중인 메시지
    val isFailed: Boolean = false,            // 전송 실패한 메시지
    val isTemporary: Boolean = false,         // 임시 메시지 (서버 응답 대기 중)
    val localId: String? = null,              // 로컬 임시 ID
    val serverTimestamp: Long? = null,        // 서버에서 받은 실제 타임스탬프
    val retryCount: Int = 0                   // 재시도 횟수
) {
    /**
     * 메시지가 완전히 전송되었는지 확인
     */
    fun isCompleted(): Boolean = !isPending && !isFailed && !isTemporary
    
    /**
     * 메시지가 재시도 가능한지 확인
     */
    fun canRetry(): Boolean = isFailed && retryCount < 3
    
    /**
     * 임시 메시지를 실제 메시지로 변환
     */
    fun toCompletedMessage(realMessageId: String, serverTimestamp: Long): Message {
        return this.copy(
            messageId = realMessageId,
            isPending = false,
            isFailed = false,
            isTemporary = false,
            serverTimestamp = serverTimestamp
        )
    }
    
    /**
     * 실패한 메시지로 마크
     */
    fun toFailedMessage(): Message {
        return this.copy(
            isPending = false,
            isFailed = true,
            retryCount = retryCount + 1
        )
    }
}
