package com.example.lovebug_project.data.db

import android.app.Application
import androidx.room.Room
import com.example.lovebug_project.data.db.entity.Post
import com.example.lovebug_project.data.db.entity.User
import com.example.lovebug_project.data.repository.DatabaseRepository
import java.util.concurrent.Executors

/**
 * 앱 전체에서 사용할 Room DB 인스턴스를 전역으로 관리하는 Application 클래스
 */
class MyApplication : Application() {
    companion object {
        lateinit var database: AppDatabase
            private set
        
        lateinit var repository: DatabaseRepository
            private set
    }

    override fun onCreate() {
        super.onCreate()

        // Room DB 인스턴스 초기화
        database = Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java,
            "lovebug.db" // 실제 생성된 DB 파일 이름
        )
            .fallbackToDestructiveMigration() // 버전 변경 시 기존 DB 삭제
            .build()

        // Repository 인스턴스 초기화
        repository = DatabaseRepository.getInstance(database)

        // 백그라운드 스레드에서 테스트 데이터 삽입
        Executors.newSingleThreadExecutor().execute {
            insertTestDataIfNeeded()
        }
    }

    private fun insertTestDataIfNeeded() {
        val userDao = database.userDao()
        val postDao = database.postDao()

        // 유저가 이미 있으면 추가하지 않음
        val existingUser = userDao.login("qwer", "1234")
        if (existingUser != null) return

        // 1. 테스트 유저 추가
        val user = User(
            username = "테스트",
            userLoginId = "qwer",
            password = "1234",
            nickname = "체리마루",
            profileImage = null,
            sharedSavingStats = false
        )
        val userId = userDao.insert(user).toInt()  // 자동 생성된 id 받아오기

        // 2. 테스트 게시글 추가
        val testPost = Post(
            userId = userId,
            title = "안녕하세요",
            content = "이건 테스트 게시글입니다.",
            image = null,
            createdAt = "2025-08-06 15:00"
        )
        postDao.insert(testPost)
    }
}

