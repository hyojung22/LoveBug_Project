package com.example.lovebug_project.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.lovebug_project.data.db.entity.Like

@Dao
interface LikeDao {
    /**
     * 좋아요 추가
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(like: Like)

    /**
     * 좋아요 삭제
     */
    @Delete
    fun delete(like: Like)

    /**
     * 게시글 좋아요 수 가져오기
     */
    @Query("SELECT COUNT(*) FROM likes WHERE postId = :postId")
    fun getLikeCountByPost(postId: Int): Int
}