package com.example.lovebug_project.data.repository

import com.example.lovebug_project.data.supabase.models.CategorySaving
import com.example.lovebug_project.data.supabase.models.MonthlySavingSummary
import com.example.lovebug_project.data.supabase.models.SavingPerformance
import com.example.lovebug_project.mypage.CategoryData
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.max

/**
 * 절약 성과 관련 비즈니스 로직을 처리하는 Repository
 * 지출 데이터와 예산 데이터를 조합하여 절약 성과를 계산
 */
class SupabaseSavingRepository {
    
    private val expenseRepository = SupabaseExpenseRepository()
    private val authRepository = SupabaseAuthRepository()
    
    // 카테고리별 기본 색상 매핑
    private val categoryColors = mapOf(
        "식비" to "#FFC3A0",
        "교통" to "#A3D6E3", 
        "쇼핑" to "#C9E4A6",
        "문화/여가" to "#E4B4D1",
        "의료/건강" to "#FFB3B3",
        "교육" to "#B3D9FF",
        "기타" to "#D1C4E9"
    )
    
    /**
     * 특정 월의 절약 성과 조회
     * @param userId 사용자 ID
     * @param yearMonth "2025-08" 형식의 년월
     */
    suspend fun getSavingPerformanceForMonth(userId: String, yearMonth: String): Result<SavingPerformance?> {
        return try {
            // 해당 월의 예산 조회
            val monthlyBudget = authRepository.getMonthlyBudgetForMonth(yearMonth)?.toDouble()
                ?: return Result.success(null) // 예산이 설정되지 않은 경우
            
            // 해당 월의 지출 내역 조회
            val startDate = "$yearMonth-01"
            val endDate = getLastDayOfMonth(yearMonth)
            
            val expensesResult = expenseRepository.getExpensesByDateRange(userId, startDate, endDate)
            if (expensesResult.isFailure) {
                return Result.failure(expensesResult.exceptionOrNull() ?: Exception("지출 데이터 조회 실패"))
            }
            
            val expenses = expensesResult.getOrNull() ?: emptyList()
            val totalSpent = expenses.sumOf { it.amount }
            val totalSaved = monthlyBudget - totalSpent
            val savingRate = if (monthlyBudget > 0) (totalSaved / monthlyBudget).coerceIn(-1.0, 1.0) else 0.0
            
            // 카테고리별 지출 계산
            val categoryExpenses = expenses.groupBy { it.category }
                .mapValues { (_, expenses) -> expenses.sumOf { it.amount } }
            
            // 카테고리별 절약 성과 계산
            val categorySavings = calculateCategorySavings(categoryExpenses, totalSpent, monthlyBudget)
            
            val savingPerformance = SavingPerformance(
                userId = userId,
                yearMonth = yearMonth,
                totalBudget = monthlyBudget,
                totalSpent = totalSpent,
                totalSaved = totalSaved,
                savingRate = savingRate,
                categorySavings = categorySavings
            )
            
            Result.success(savingPerformance)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * 현재 월의 절약 성과 조회
     */
    suspend fun getCurrentMonthSavingPerformance(userId: String): Result<SavingPerformance?> {
        val currentYearMonth = getCurrentYearMonth()
        return getSavingPerformanceForMonth(userId, currentYearMonth)
    }
    
    /**
     * 절약 성과를 CategoryData 리스트로 변환 (기존 UI 호환성)
     */
    suspend fun getSavingPerformanceAsCategoryData(userId: String, yearMonth: String? = null): Result<List<CategoryData>> {
        return try {
            val targetYearMonth = yearMonth ?: getCurrentYearMonth()
            val performanceResult = getSavingPerformanceForMonth(userId, targetYearMonth)
            
            if (performanceResult.isFailure) {
                return Result.failure(performanceResult.exceptionOrNull() ?: Exception("절약 성과 조회 실패"))
            }
            
            val performance = performanceResult.getOrNull()
                ?: return Result.success(getDefaultCategoryData()) // 데이터가 없으면 기본값 반환
            
            val categoryDataList = performance.categorySavings.map { categorySaving ->
                CategoryData(
                    cg_name = categorySaving.categoryName,
                    percentage = categorySaving.percentage,
                    color = categorySaving.color
                )
            }
            
            Result.success(categoryDataList)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * 월별 절약 성과 요약 조회
     */
    suspend fun getMonthlySavingSummary(userId: String, yearMonth: String): Result<MonthlySavingSummary?> {
        return try {
            val performanceResult = getSavingPerformanceForMonth(userId, yearMonth)
            
            if (performanceResult.isFailure) {
                return Result.failure(performanceResult.exceptionOrNull() ?: Exception("절약 성과 조회 실패"))
            }
            
            val performance = performanceResult.getOrNull() ?: return Result.success(null)
            
            val topSavingCategory = performance.categorySavings
                .filter { it.saved > 0 }
                .maxByOrNull { it.saved }?.categoryName
                
            val topSpendingCategory = performance.categorySavings
                .maxByOrNull { it.spent }?.categoryName
            
            val summary = MonthlySavingSummary(
                userId = userId,
                yearMonth = yearMonth,
                totalBudget = performance.totalBudget,
                totalSpent = performance.totalSpent,
                totalSaved = performance.totalSaved,
                savingRate = performance.savingRate,
                topSavingCategory = topSavingCategory,
                topSpendingCategory = topSpendingCategory
            )
            
            Result.success(summary)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * 카테고리별 절약 성과 계산
     */
    private fun calculateCategorySavings(
        categoryExpenses: Map<String, Double>,
        totalSpent: Double,
        totalBudget: Double
    ): List<CategorySaving> {
        if (categoryExpenses.isEmpty()) return emptyList()
        
        return categoryExpenses.map { (category, spent) ->
            val percentage = if (totalSpent > 0) {
                ((spent / totalSpent) * 100).toInt()
            } else 0
            
            // 카테고리별 예산은 전체 예산에서 지출 비율로 계산 (실제로는 더 정교한 로직 필요)
            val categoryBudget = totalBudget * (spent / max(totalSpent, 0.01))
            val saved = categoryBudget - spent
            val savingRate = if (categoryBudget > 0) (saved / categoryBudget).coerceIn(-1.0, 1.0) else 0.0
            
            CategorySaving(
                categoryName = category,
                budget = categoryBudget,
                spent = spent,
                saved = saved,
                savingRate = savingRate,
                percentage = percentage,
                color = categoryColors[category] ?: getDefaultColorForCategory(category)
            )
        }.sortedByDescending { it.spent } // 지출액 기준 내림차순 정렬
    }
    
    /**
     * 데이터가 없을 때 기본 CategoryData 반환
     */
    private fun getDefaultCategoryData(): List<CategoryData> {
        return listOf(
            CategoryData("데이터 없음", 100, "#E0E0E0")
        )
    }
    
    /**
     * 카테고리별 기본 색상 생성
     */
    private fun getDefaultColorForCategory(category: String): String {
        val colors = listOf("#FFC3A0", "#A3D6E3", "#C9E4A6", "#E4B4D1", "#FFB3B3", "#B3D9FF", "#D1C4E9")
        return colors[category.hashCode().rem(colors.size).let { if (it < 0) it + colors.size else it }]
    }
    
    /**
     * 현재 년월 반환 (YYYY-MM 형식)
     */
    private fun getCurrentYearMonth(): String {
        val calendar = Calendar.getInstance()
        val dateFormat = SimpleDateFormat("yyyy-MM", Locale.getDefault())
        return dateFormat.format(calendar.time)
    }
    
    /**
     * 해당 월의 마지막 날짜 계산
     */
    private fun getLastDayOfMonth(yearMonth: String): String {
        val parts = yearMonth.split("-")
        val year = parts[0].toInt()
        val month = parts[1].toInt()
        
        val calendar = Calendar.getInstance()
        calendar.set(year, month - 1, 1) // Calendar.MONTH는 0부터 시작
        val lastDay = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
        
        return "$yearMonth-${String.format("%02d", lastDay)}"
    }
}