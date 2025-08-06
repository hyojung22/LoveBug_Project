package com.example.lovebug_project.home

import java.time.LocalDate

data class ExpenseData(
    val id: Long = 0L,
    val date: LocalDate,
    val amount: Int,
    val description: String = "",
    val category: String = "기타"
)

data class MonthlyBudget(
    val yearMonth: String, // "2025-08" format
    val targetAmount: Int,
    val expenses: List<ExpenseData> = emptyList()
) {
    val totalExpense: Int
        get() = expenses.sumOf { it.amount }
    
    val remainingAmount: Int
        get() = targetAmount - totalExpense
    
    val isOverBudget: Boolean
        get() = totalExpense > targetAmount
}

data class DailyExpense(
    val date: LocalDate,
    val totalAmount: Int,
    val expenses: List<ExpenseData>
)