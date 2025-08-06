package com.example.lovebug_project.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.lovebug_project.data.db.entity.SavingStats

@Dao
interface SavingStatsDao {
    /**
     * 절약 성과 저장 또는 갱신
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(stat: SavingStats)

    /**
     * 특정 유저의 특정 월 성과 전체 가져오기
     */
    @Query("SELECT * FROM saving_stats WHERE userId = :userId AND month = :month")
    suspend fun getStatsByUserAndMonth(userId: Int, month: String): List<SavingStats>
}