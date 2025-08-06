package com.example.lovebug_project.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

/**
 * 사용자 게시글 테이블
 * - 이미지, 제목, 내용, 작성일자 저장
 */
@Entity(
    tableName = "posts",
    foreignKeys = [ForeignKey(
        entity = User::class,
        parentColumns = ["userId"],
        childColumns = ["userId"],
        onDelete = ForeignKey.CASCADE
    )]
)
data class Post (
    @PrimaryKey(autoGenerate = true) val postId: Int = 0,
    val userId: Int,
    val title: String,
    val content: String,
    val image: String?, // 게시글 이미지 경로
    val createdAt: String // "2025-08-06 15:30"
)