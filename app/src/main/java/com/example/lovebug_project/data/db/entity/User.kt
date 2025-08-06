package com.example.lovebug_project.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 사용자 정보 테이블
 * - 회원가입/로그인/마이페이지용
 * - 프로필 사진, 닉네임, 절약 성과 공개 여부 포함
 */
@Entity(tableName = "users")
data class User(
    @PrimaryKey(autoGenerate = true) val userId: Int = 0,
    val username: String,
    val nickname: String,
    val userLoginId: String,
    val password: String,
    val profileImage : String?, // 이미지 URI 또는 경로
    val shareSavingStats : Boolean = false, // 절약 성과 공유 여부
)