package com.example.lovebug_project.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.lovebug_project.data.db.entity.Post

@Dao
interface PostDao {
    /** 게시글 추가 후 생성된 PK를 Long으로 반환 */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(post: Post): Long

    /** 단건 조회 */
    @Query("SELECT * FROM posts WHERE postId = :postId")
    fun getPostById(postId: Int): Post

    /**
     * 게시글 전체 목록 가져오기
     */
    @Query("SELECT * FROM posts ORDER BY createdAt DESC")
    fun getAllPosts(): List<Post>

    /**
     * 특정 유저가 작성한 게시글 목록
     */
    @Query("SELECT * FROM posts WHERE userId = :userId")
    fun getPostsByUser(userId: Int): List<Post>

    // 최신 글 하나
    @Query("SELECT * FROM posts ORDER BY postId DESC LIMIT 1")
    fun getLatestPost(): Post?

    /** ID로 게시글 삭제 */
    @Query("DELETE FROM posts WHERE postId = :postId")
    fun deleteById(postId: Int)
}