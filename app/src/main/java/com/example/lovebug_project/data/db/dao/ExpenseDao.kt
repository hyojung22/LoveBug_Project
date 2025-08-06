package com.example.lovebug_project.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.lovebug_project.data.db.entity.Expense

@Dao
interface ExpenseDao {
    /**
     * 지출 내역 추가
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(expense: Expense)

    /**
     * 지출 내역 삭제
     */
    @Delete
    fun delete(expense: Expense)

    /**
     * 지출 내역 수정
     */
    @Update
    fun update(expense: Expense)

    /**
     * 특정 유저의 특정 월 지출 내역 가져오기
     */
    @Query("SELECT * FROM expenses WHERE userId = :userId AND date LIKE :month || '%'")
    fun getExpenseByMonth(userId: Int, month: String): List<Expense>

    /**
     * 특정 날짜의 지출 내역 가져오기
     */
    @Query("SELECT * FROM expenses WHERE userId = :userId AND date = :date")
    fun getExpensesByDate(userId: Int, date: String): List<Expense>
}