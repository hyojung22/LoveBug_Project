package com.example.lovebug_project.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

/**
 * 월별 지출 목표 금액 저장 테이블
 * - 사용자별 특정 월의 목표금액
 */
@Entity(
    tableName = "saving_goals",
    foreignKeys = [ForeignKey(
        entity = User::class,
        parentColumns = ["userId"],
        childColumns = ["userId"],
        onDelete = ForeignKey.CASCADE
    )]
)
data class SavingGoal(
    @PrimaryKey(autoGenerate = true) val goalId: Int = 0,
    val userId: Int,
    val month: String, // "2025-08"
    val goalAmount: Int // 목표 금액
)