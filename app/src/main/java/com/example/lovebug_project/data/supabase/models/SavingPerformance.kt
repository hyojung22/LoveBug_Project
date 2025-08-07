package com.example.lovebug_project.data.supabase.models

import kotlinx.serialization.Serializable

/**
 * 절약 성과 데이터 모델
 * 사용자의 월별 카테고리별 절약 성과를 나타냄
 */
@Serializable
data class SavingPerformance(
    val userId: String,
    val yearMonth: String, // "2025-08" 형식
    val totalBudget: Double,
    val totalSpent: Double,
    val totalSaved: Double,
    val savingRate: Double, // 절약률 (0.0 ~ 1.0)
    val categorySavings: List<CategorySaving>
)

/**
 * 카테고리별 절약 성과
 */
@Serializable
data class CategorySaving(
    val categoryName: String,
    val budget: Double? = null, // 카테고리별 예산 (없으면 전체 예산에서 비례 계산)
    val spent: Double,
    val saved: Double, // 절약 금액 (budget - spent), 음수면 초과 지출
    val savingRate: Double, // 절약률
    val percentage: Int, // 전체 지출에서 차지하는 비율
    val color: String // 차트 색상
)

/**
 * 월별 절약 성과 요약
 */
@Serializable
data class MonthlySavingSummary(
    val userId: String,
    val yearMonth: String,
    val totalBudget: Double,
    val totalSpent: Double,
    val totalSaved: Double,
    val savingRate: Double,
    val topSavingCategory: String?, // 가장 많이 절약한 카테고리
    val topSpendingCategory: String? // 가장 많이 지출한 카테고리
)