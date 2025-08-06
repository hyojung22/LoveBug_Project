package com.example.lovebug_project.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.lovebug_project.data.db.entity.Comment

@Dao
interface CommentDao {
    /**
     * 댓글 추가
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(comment: Comment)

    /**
     * 댓글 삭제
     */
    @Delete
    fun delete(comment: Comment)

    /**
     * 댓글 수정
     */
    @Update
    fun update(comment: Comment)

    /**
     * 게시글의 댓글 리스트 가져오기
     */
    @Query("SELECT * FROM comments WHERE postId = :postId ORDER BY createdAt ASC")
    fun getCommentsByPost(postId: Int): List<Comment>
}