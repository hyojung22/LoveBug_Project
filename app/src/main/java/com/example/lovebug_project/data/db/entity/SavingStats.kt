package com.example.lovebug_project.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

/**
 * 절약 성과 캐싱 테이블
 * - 월별 카테고리별 사용 금액을 저장
 * - 원형 차트 표시용으로 빠르게 접근
 * - 퍼센트는 UI에서 계산
 */
@Entity(
    tableName = "saving_stats",
    foreignKeys = [
        ForeignKey(
            entity = User::class,
            parentColumns = ["userId"],
            childColumns = ["userId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
    )
data class SavingStats(
    @PrimaryKey(autoGenerate = true) val statsId: Int = 0,
    val userId: Int,
    val month: String, // 예: "2025-08"
    val category: String, // 예: "식비",
    val totalAmount: Int // 합산된 금액
)
