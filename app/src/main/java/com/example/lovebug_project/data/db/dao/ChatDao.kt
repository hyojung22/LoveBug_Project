package com.example.lovebug_project.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.lovebug_project.data.db.entity.Chat

@Dao
interface ChatDao {
    /**
     * 채팅방 생성
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(chat: Chat)

    /**
     * 특정 유저가 포함된 채팅방 목록 가져오기
     */
    @Query("SELECT * FROM chats WHERE user1Id = :userId OR user2Id = :userId")
    suspend fun getChatsByUser(userId: Int): List<Chat>
}