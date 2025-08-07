package com.example.lovebug_project.data.repository

import com.example.lovebug_project.data.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.user.UserInfo
import io.github.jan.supabase.auth.user.UserSession
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.int
import io.github.jan.supabase.auth.status.SessionStatus

class SupabaseAuthRepository {
    private val supabase = SupabaseClient.client
    
    /**
     * Sign up a new user with email and password
     */
    suspend fun signUp(
        email: String,
        password: String,
        username: String,
        nickname: String,
        monthlyBudget: Int? = null
    ): Result<UserInfo?> {
        return try {
            val data = mutableMapOf(
                "username" to JsonPrimitive(username),
                "nickname" to JsonPrimitive(nickname),
                "display_name" to JsonPrimitive(nickname)
            )
            
            // 월별 목표 지출 금액이 제공된 경우 추가
            monthlyBudget?.let {
                data["monthly_budget"] = JsonPrimitive(it)
            }
            
            val result = supabase.auth.signUpWith(Email) {
                this.email = email
                this.password = password
                this.data = JsonObject(data)
            }
            Result.success(result)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Sign in with email and password
     */
    suspend fun signIn(email: String, password: String): Result<UserSession> {
        return try {
            supabase.auth.signInWith(Email) {
                this.email = email
                this.password = password
            }
            val session = supabase.auth.currentSessionOrNull()
                ?: throw IllegalStateException("Sign in succeeded but no session found")
            Result.success(session)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Sign out the current user
     */
    suspend fun signOut(): Result<Unit> {
        return try {
            supabase.auth.signOut()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Get current user session
     */
    fun getCurrentSession(): UserSession? {
        return supabase.auth.currentSessionOrNull()
    }
    
    /**
     * Get current user
     */
    fun getCurrentUser(): UserInfo? {
        return supabase.auth.currentUserOrNull()
    }
    
    /**
     * Observe authentication state changes
     */
    fun observeAuthState(): Flow<SessionStatus> {
        return supabase.auth.sessionStatus
    }
    
    /**
     * Update user metadata (nickname, profile image, monthly budget, etc.)
     */
    suspend fun updateUserMetadata(
        nickname: String? = null,
        profileImage: String? = null,
        sharedSavingStats: Boolean? = null,
        monthlyBudget: Int? = null
    ): Result<UserInfo> {
        return try {
            val updates = mutableMapOf<String, JsonPrimitive>()
            nickname?.let { updates["nickname"] = JsonPrimitive(it) }
            profileImage?.let { updates["profile_image"] = JsonPrimitive(it) }
            sharedSavingStats?.let { updates["shared_saving_stats"] = JsonPrimitive(it) }
            monthlyBudget?.let { updates["monthly_budget"] = JsonPrimitive(it) }
            
            val result = supabase.auth.updateUser {
                data = if (updates.isNotEmpty()) JsonObject(updates) else null
            }
            Result.success(result)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Get user's monthly budget from metadata (legacy method for backward compatibility)
     */
    fun getUserMonthlyBudget(): Int? {
        return getCurrentUser()?.userMetadata?.get("monthly_budget")?.toString()?.toIntOrNull()
    }
    
    /**
     * 특정 월의 목표 지출 금액 조회
     * @param yearMonth YYYY-MM 형식 (예: "2025-01")
     * @return 해당 월의 목표 금액, 설정되지 않은 경우 default 값 또는 null
     */
    fun getMonthlyBudgetForMonth(yearMonth: String): Int? {
        val currentUser = getCurrentUser()
        val monthlyBudgets = currentUser?.userMetadata?.get("monthly_budgets")?.jsonObject
        
        return if (monthlyBudgets != null) {
            // 특정 월에 설정된 값이 있으면 해당 값 반환
            monthlyBudgets[yearMonth]?.jsonPrimitive?.int
                ?: monthlyBudgets["default"]?.jsonPrimitive?.int // 없으면 default 값
        } else {
            // monthly_budgets가 없으면 기존 monthly_budget 값 사용 (하위 호환성)
            getUserMonthlyBudget()
        }
    }
    
    /**
     * 특정 월의 목표 지출 금액 설정
     * @param yearMonth YYYY-MM 형식 (예: "2025-01")
     * @param budget 목표 지출 금액
     */
    suspend fun setMonthlyBudgetForMonth(yearMonth: String, budget: Int): Result<UserInfo> {
        return try {
            val currentUser = getCurrentUser()
            val existingBudgetsJson = currentUser?.userMetadata?.get("monthly_budgets")?.jsonObject
            
            // 기존 budget 데이터를 mutableMap으로 변환
            val existingBudgets = mutableMapOf<String, JsonPrimitive>()
            existingBudgetsJson?.forEach { (key, value) ->
                existingBudgets[key] = value.jsonPrimitive
            }
            
            // 특정 월의 예산 업데이트
            existingBudgets[yearMonth] = JsonPrimitive(budget)
            
            val result = supabase.auth.updateUser {
                data = JsonObject(mapOf(
                    "monthly_budgets" to JsonObject(existingBudgets)
                ))
            }
            Result.success(result)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * 기본 월별 목표 지출 금액 설정 (새로운 월에 대한 기본값)
     * @param budget 기본 목표 지출 금액
     */
    suspend fun setDefaultMonthlyBudget(budget: Int): Result<UserInfo> {
        return try {
            val currentUser = getCurrentUser()
            val existingBudgetsJson = currentUser?.userMetadata?.get("monthly_budgets")?.jsonObject
            
            // 기존 budget 데이터를 mutableMap으로 변환
            val existingBudgets = mutableMapOf<String, JsonPrimitive>()
            existingBudgetsJson?.forEach { (key, value) ->
                existingBudgets[key] = value.jsonPrimitive
            }
            
            // default 값 설정
            existingBudgets["default"] = JsonPrimitive(budget)
            
            val result = supabase.auth.updateUser {
                data = JsonObject(mapOf(
                    "monthly_budgets" to JsonObject(existingBudgets),
                    "monthly_budget" to JsonPrimitive(budget) // 하위 호환성 유지
                ))
            }
            Result.success(result)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * 모든 월별 목표 지출 금액 조회
     * @return Map<String, Int> - 월별 목표 금액 맵 ("YYYY-MM" -> 금액)
     */
    fun getAllMonthlyBudgets(): Map<String, Int> {
        val currentUser = getCurrentUser()
        val monthlyBudgets = currentUser?.userMetadata?.get("monthly_budgets")?.jsonObject
        
        return monthlyBudgets?.mapValues { (_, value) -> 
            value.jsonPrimitive.int 
        } ?: emptyMap()
    }
    
    /**
     * 초기 설정 완료 여부 확인
     * @return true if initial setup is completed, false otherwise
     */
    fun isInitialSetupCompleted(): Boolean {
        val currentUser = getCurrentUser()
        return currentUser?.userMetadata?.get("initial_setup_completed")?.toString()?.toBooleanStrictOrNull() ?: false
    }
    
    /**
     * 초기 설정 완료로 표시
     */
    suspend fun setInitialSetupCompleted(): Result<UserInfo> {
        return try {
            val result = supabase.auth.updateUser {
                data = JsonObject(mapOf(
                    "initial_setup_completed" to JsonPrimitive(true)
                ))
            }
            Result.success(result)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Delete user account
     */
    suspend fun deleteAccount(): Result<Unit> {
        return try {
            // Supabase doesn't have a direct delete method in client SDK
            // You would need to call a server-side function or use the admin API
            // For now, we'll just sign out
            supabase.auth.signOut()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}