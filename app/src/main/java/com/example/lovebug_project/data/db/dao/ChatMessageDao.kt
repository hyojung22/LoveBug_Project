package com.example.lovebug_project.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.lovebug_project.data.db.entity.ChatMessage

@Dao
interface ChatMessageDao {
    /**
     * 메시지 추가
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(message: ChatMessage)

    /**
     * 채팅방의 모든 메시지 불러오기
     */
    @Query("SELECT * FROM chat_messages WHERE chatId = :chatId ORDER BY timestamp ASC")
    fun getMessagesByChat(chatId: Int): List<ChatMessage>
}