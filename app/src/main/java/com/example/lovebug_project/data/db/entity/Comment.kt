package com.example.lovebug_project.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

/**
 * 게시글 댓글 테이블
 * - 댓글 내용, 작성 시간, 수정 시간 기록
 */
@Entity(
    tableName = "comments",
    foreignKeys = [
        ForeignKey(entity = Post::class, parentColumns = ["postId"], childColumns = ["postId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = User::class, parentColumns = ["userId"], childColumns = ["userId"], onDelete = ForeignKey.CASCADE)
    ]
)
data class Comment (
    @PrimaryKey(autoGenerate = true) val commentId: Int = 0,
    val postId: Int,
    val userId: Int,
    var content: String,
    val createdAt: String,
    val updateAt: String? = null
)