package com.example.lovebug_project.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.lovebug_project.data.db.entity.User

@Dao
interface UserDao {
    /**
     * 회원 가입
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(user: User)

    /**
     * 로그인 (ID와 비밀번호로 조회)
     */
    @Query("SELECT * FROM users WHERE userLoginId = :loginId AND password = :password")
    suspend fun login(loginId: String, password: String): User?

    /**
     * 유저 ID로 정보 조회
     */
    @Query("SELECT * FROM users WHERE userId = :userId")
    suspend fun getUserById(userId: Int): User?

    /**
     * 닉네임 또는 프로필 이미지 수정
     */
    @Update
    suspend fun update(user: User)

    /**
     * 탈퇴 처리
     */
    @Delete
    suspend fun delete(user: User)
}