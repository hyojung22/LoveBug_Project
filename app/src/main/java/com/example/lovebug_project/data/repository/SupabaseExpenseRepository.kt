package com.example.lovebug_project.data.repository

import com.example.lovebug_project.data.supabase.SupabaseClient
import com.example.lovebug_project.data.supabase.models.Expense
import io.github.jan.supabase.postgrest.postgrest

class SupabaseExpenseRepository {
    private val supabase = SupabaseClient.client
    
    /**
     * 사용자의 모든 지출 내역 조회
     */
    suspend fun getExpensesByUserId(userId: String): Result<List<Expense>> {
        return try {
            val expenses = supabase.postgrest.from("expenses")
                .select {
                    filter {
                        eq("user_id", userId)
                    }
                }
                .decodeList<Expense>()
            Result.success(expenses)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * 특정 날짜의 지출 내역 조회
     */
    suspend fun getExpensesByDate(userId: String, date: String): Result<List<Expense>> {
        return try {
            val expenses = supabase.postgrest.from("expenses")
                .select {
                    filter {
                        eq("user_id", userId)
                        eq("date", date)
                    }
                }
                .decodeList<Expense>()
            Result.success(expenses)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * 날짜 범위별 지출 내역 조회
     */
    suspend fun getExpensesByDateRange(userId: String, startDate: String, endDate: String): Result<List<Expense>> {
        return try {
            val expenses = supabase.postgrest.from("expenses")
                .select {
                    filter {
                        eq("user_id", userId)
                        gte("date", startDate)
                        lte("date", endDate)
                    }
                }
                .decodeList<Expense>()
            Result.success(expenses)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * 새 지출 내역 추가
     */
    suspend fun createExpense(expense: Expense): Result<Expense> {
        return try {
            val result = supabase.postgrest.from("expenses")
                .insert(expense) {
                    select()
                }
                .decodeSingle<Expense>()
            Result.success(result)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * 지출 내역 수정
     */
    suspend fun updateExpense(expenseId: Int, expense: Expense): Result<Expense> {
        return try {
            val result = supabase.postgrest.from("expenses")
                .update(expense) {
                    select()
                    filter {
                        eq("expense_id", expenseId)
                    }
                }
                .decodeSingle<Expense>()
            Result.success(result)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * 지출 내역 삭제
     */
    suspend fun deleteExpense(expenseId: Int): Result<Unit> {
        return try {
            supabase.postgrest.from("expenses")
                .delete {
                    filter {
                        eq("expense_id", expenseId)
                    }
                }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * 카테고리별 지출 합계 조회
     */
    suspend fun getExpensesByCategory(userId: String): Result<Map<String, Int>> {
        return try {
            val expenses = supabase.postgrest.from("expenses")
                .select {
                    filter {
                        eq("user_id", userId)
                    }
                }
                .decodeList<Expense>()
            
            val categoryTotals = expenses.groupBy { it.category }
                .mapValues { (_, expenses) -> expenses.sumOf { it.amount } }
            
            Result.success(categoryTotals)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}