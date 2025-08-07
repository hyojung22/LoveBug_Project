package com.example.lovebug_project.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

/**
 * 게시글 좋아요 테이블
 * - 어떤 사용자가 어떤 게시글에 좋아요를 눌렀는지 기록
 */
@Entity(
    tableName = "likes",
    foreignKeys = [
        ForeignKey(entity = Post::class, parentColumns = ["postId"], childColumns = ["postId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = User::class, parentColumns = ["userId"], childColumns = ["userId"], onDelete = ForeignKey.CASCADE)
    ]
)
data class Like(
    @PrimaryKey(autoGenerate = true) val likeId: Int = 0,
    val postId: Int,
    val userId: Int
)
