package com.example.lovebug_project.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.lovebug_project.data.db.dao.BookmarkDao
import com.example.lovebug_project.data.db.dao.ChatDao
import com.example.lovebug_project.data.db.dao.ChatMessageDao
import com.example.lovebug_project.data.db.dao.CommentDao
import com.example.lovebug_project.data.db.dao.ExpenseDao
import com.example.lovebug_project.data.db.dao.LikeDao
import com.example.lovebug_project.data.db.dao.PostDao
import com.example.lovebug_project.data.db.dao.SavingGoalDao
import com.example.lovebug_project.data.db.dao.SavingStatsDao
import com.example.lovebug_project.data.db.dao.UserDao
import com.example.lovebug_project.data.db.entity.Bookmark
import com.example.lovebug_project.data.db.entity.Chat
import com.example.lovebug_project.data.db.entity.ChatMessage
import com.example.lovebug_project.data.db.entity.Comment
import com.example.lovebug_project.data.db.entity.Expense
import com.example.lovebug_project.data.db.entity.Like
import com.example.lovebug_project.data.db.entity.Post
import com.example.lovebug_project.data.db.entity.SavingGoal
import com.example.lovebug_project.data.db.entity.SavingStats
import com.example.lovebug_project.data.db.entity.User

// RoomDatabase 클래스
@Database(
    entities = [
        User::class,
        Expense::class,
        SavingGoal::class,
        SavingStats::class,
        Post::class,
        Comment::class,
        Like::class,
        Bookmark::class,
        Chat::class,
        ChatMessage::class
    ],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun userDao(): UserDao
    abstract fun expenseDao(): ExpenseDao
    abstract fun savingGoalDao(): SavingGoalDao
    abstract fun savingStatsDao(): SavingStatsDao
    abstract fun postDao(): PostDao
    abstract fun commentDao(): CommentDao
    abstract fun likeDao(): LikeDao
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun chatDao(): ChatDao
    abstract fun chatMessageDao(): ChatMessageDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            // 다중 인스턴스 방지를 위한 동기화 블록
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "lovebug_database"
                )
                .fallbackToDestructiveMigration() // 개발용 - 스키마 변경 허용
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}