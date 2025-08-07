package com.example.lovebug_project.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

/**
 * 게시글 북마크(즐겨찾기) 테이블
 * - 내가 저장한 게시글 관리
 */
@Entity(
    tableName = "bookmarks",
    foreignKeys = [
        ForeignKey(entity = Post::class, parentColumns = ["postId"], childColumns = ["postId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = User::class, parentColumns = ["userId"], childColumns = ["userId"], onDelete = ForeignKey.CASCADE)
    ]
)
data class Bookmark(
    @PrimaryKey(autoGenerate = true) val bookmarkId: Int = 0,
    val postId: Int,
    val userId: Int
)
