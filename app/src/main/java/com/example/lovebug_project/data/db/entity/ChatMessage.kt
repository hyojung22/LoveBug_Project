package com.example.lovebug_project.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

/**
 * 채팅 메시지 테이블
 * - 채팅방에서 주고받은 메시지 기록
 */
@Entity(
    tableName = "chat_messages",
    foreignKeys = [ForeignKey(
        entity = Chat::class,
        parentColumns = ["chatId"],
        childColumns = ["chatId"],
        onDelete = ForeignKey.CASCADE
    )]
)
data class ChatMessage(
    @PrimaryKey(autoGenerate = true) val messageId: Int = 0,
    val chatId: Int,
    val senderId: Int,
    val message: String,
    val timestamp: String // "2025-08-06 22:30"
)
