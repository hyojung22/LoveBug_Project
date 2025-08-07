package com.example.lovebug_project.data.supabase.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Supabase Expense 테이블 모델
 * 사용자 지출 내역
 */
@Serializable
data class Expense(
    @SerialName("expense_id") val expenseId: Int = 0,
    @SerialName("user_id") val userId: String, // Supabase Auth user UUID
    val date: String, // "2025-08-06"
    val category: String, // 예: "식비"
    val amount: Int,
    val memo: String? = null,
    @SerialName("created_at") val createdAt: String? = null
)