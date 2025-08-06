package com.example.lovebug_project.data.db

import android.app.Application
import androidx.room.Room

/**
 * 앱 전체에서 사용할 Room DB 인스턴스를 전역으로 관리하는 Application 클래스
 */
class MyApplication : Application() {
    companion object {
        lateinit var database: AppDatabase
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
            .allowMainThreadQueries() // 메인 스레드에서 DB 접근 허용
            .fallbackToDestructiveMigration() // 버전 변경 시 기존 DB 삭제
            .build()
    }
}