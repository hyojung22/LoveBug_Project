package com.example.lovebug_project.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.lovebug_project.data.db.entity.SavingGoal

@Dao
interface SavingGoalDao {
    /**
     * 목표 금액 설정
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(goal: SavingGoal)

    /**
     * 특정 유저의 특정 월 목표 가져오기
     */
    @Query("SELECT * FROM saving_goals WHERE userId = :userId AND month = :month")
    suspend fun getGoal(userId: Int, month: String): SavingGoal?
}