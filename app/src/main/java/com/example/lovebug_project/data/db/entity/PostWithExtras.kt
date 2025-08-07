package com.example.lovebug_project.data.db.entity

import com.example.lovebug_project.data.db.entity.Post
import java.io.Serializable

data class PostWithExtras(
    val post: Post,
    val nickname: String,
    val profileImage: String?,
    val likeCount: Int,
    val commentCount: Int,
    val isBookmarked: Boolean
) : Serializable