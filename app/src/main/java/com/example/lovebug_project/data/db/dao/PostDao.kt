package com.example.lovebug_project.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.lovebug_project.data.db.entity.Post

@Dao
interface PostDao {
    /**
     * 게시글 추가
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(post: Post)

    /**
     * 게시글 전체 목록 가져오기
     */
    @Query("SELECT * FROM posts ORDER BY createdAt DESC")
    suspend fun getAllPosts(): List<Post>

    /**
     * 특정 유저가 작성한 게시글 목록
     */
    @Query("SELECT * FROM posts WHERE userId = :userId")
    suspend fun getPostsByUser(userId: Int): List<Post>
}