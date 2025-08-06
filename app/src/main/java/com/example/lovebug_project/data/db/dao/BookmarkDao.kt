package com.example.lovebug_project.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.lovebug_project.data.db.entity.Bookmark

@Dao
interface BookmarkDao {
    /**
     * 게시글 북마크 추가
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(bookmark: Bookmark)

    /**
     * 게시글 북마크 삭제
     */
    @Delete
    suspend fun delete(bookmark: Bookmark)

    /**
     * 유저가 북마크한 게시글 목록 가져오기
     */
    @Query("SELECT * FROM bookmarks WHERE userId = :userId")
    suspend fun getBookmarksByUser(userId: Int): List<Bookmark>
}