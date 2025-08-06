package com.example.lovebug_project

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4

import org.junit.Test
import org.junit.runner.RunWith

import org.junit.Assert.*

/**
 * Android 디바이스에서 실행될 계측 테스트입니다.
 *
 * [테스팅 문서](http://d.android.com/tools/testing)를 참조하세요.
 */
@RunWith(AndroidJUnit4::class)
class ExampleInstrumentedTest {
    @Test
    fun useAppContext() {
        // 테스트 중인 앱의 컨텍스트입니다.
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("com.example.lovebug_project", appContext.packageName)
    }
}