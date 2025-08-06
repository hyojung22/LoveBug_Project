package com.example.lovebug_project.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

/**
 * 사용자 지출 내역 테이블
 * - 날짜별 지출 내역 등록
 * - 카테고리, 금액, 메모 포함
 */
@Entity(
    tableName = "expenses",
    foreignKeys = [ForeignKey(
        entity = User::class,
        parentColumns = ["userId"],
        childColumns = ["userId"],
        onDelete = ForeignKey.CASCADE
    )]
)
data class Expense(
    @PrimaryKey(autoGenerate = true) val expenseId: Int = 0,
    val userId: Int,
    val date: String, // "2025-08-06"
    val category: String, // 예: "식비"
    val amount: Int,
    val memo: String? // 한 줄 메모
)