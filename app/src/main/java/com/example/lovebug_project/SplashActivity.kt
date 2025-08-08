package com.example.lovebug_project

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.lovebug_project.MainActivity
import com.example.lovebug_project.auth.LoginActivity
import com.example.lovebug_project.auth.PayActivity
import com.example.lovebug_project.databinding.ActivitySplashBinding
import com.example.lovebug_project.data.db.MyApplication
import com.example.lovebug_project.utils.AuthHelper
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

class SplashActivity : AppCompatActivity() {

    private lateinit var binding : ActivitySplashBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 1. 스플래시 이미지들을 리스트로 만듭니다.
        val splashImages = listOf(
            R.drawable.splash1,
            R.drawable.splash2,
            R.drawable.splash3,
            R.drawable.splash4
        )

        // 2. 리스트 중에서 랜덤으로 하나를 선택합니다.
        val randomImage = splashImages.random()

        // 3. 레이아웃의 ImageView에 선택된 이미지를 설정합니다.
        binding.splashImageView.setImageResource(randomImage)

        // 4. 세션 확인 후 적절한 화면으로 이동
        checkSessionAndNavigate()
    }
    
    /**
     * 기존 세션을 확인하고 적절한 화면으로 이동
     */
    private fun checkSessionAndNavigate() {
        lifecycleScope.launch {
            try {
                Log.d("SplashActivity", "🔍 Checking existing session...")
                
                // 최소 스플래시 표시 시간 보장
                delay(1500) 
                
                // 세션 상태 확인 (타임아웃 설정)
                val sessionStatus = withTimeoutOrNull(3000) {
                    MyApplication.authRepository.observeAuthState().first()
                }
                
                when (sessionStatus) {
                    is SessionStatus.Authenticated -> {
                        Log.d("SplashActivity", "✅ Valid session found, user: ${sessionStatus.session.user?.email}")
                        
                        // SharedPreferences에 사용자 ID 저장 (기존 코드와의 호환성)
                        val userPref = getSharedPreferences("user_prefs", MODE_PRIVATE)
                        userPref.edit().putString("supabase_user_id", sessionStatus.session.user?.id).apply()
                        
                        // 초기 설정 완료 여부 확인
                        val isInitialSetupCompleted = MyApplication.authRepository.isInitialSetupCompleted()
                        
                        if (!isInitialSetupCompleted) {
                            // 초기 설정 미완료 - PayActivity로 이동
                            navigateToActivity(PayActivity::class.java)
                        } else {
                            // 초기 설정 완료 - MainActivity로 이동
                            navigateToActivity(MainActivity::class.java)
                        }
                    }
                    
                    is SessionStatus.NotAuthenticated -> {
                        Log.d("SplashActivity", "❌ No valid session found")
                        // SharedPreferences 정리
                        clearUserPreferences()
                        navigateToActivity(LoginActivity::class.java)
                    }
                    
                    is SessionStatus.Initializing -> {
                        Log.d("SplashActivity", "⏳ Session loading...")
                        // 세션 로딩 중 - 조금 더 기다리기
                        delay(1000)
                        // 재귀적으로 다시 체크
                        checkSessionAndNavigate()
                        return@launch
                    }
                    
                    is SessionStatus.RefreshFailure -> {
                        Log.w("SplashActivity", "⚠️ Session refresh failed: ${sessionStatus.cause}")
                        // 세션 갱신 실패 - 로그인 화면으로 이동
                        clearUserPreferences()
                        navigateToActivity(LoginActivity::class.java)
                    }
                    
                    null -> {
                        Log.w("SplashActivity", "⏰ Session check timeout")
                        // 타임아웃 - SharedPreferences 기반 체크로 폴백
                        if (AuthHelper.isLoggedIn(this@SplashActivity)) {
                            navigateToActivity(MainActivity::class.java)
                        } else {
                            navigateToActivity(LoginActivity::class.java)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("SplashActivity", "💥 Error during session check", e)
                // 오류 발생 시 로그인 화면으로 이동
                clearUserPreferences()
                navigateToActivity(LoginActivity::class.java)
            }
        }
    }
    
    /**
     * 사용자 기본 설정 정리
     */
    private fun clearUserPreferences() {
        val userPref = getSharedPreferences("user_prefs", MODE_PRIVATE)
        userPref.edit()
            .remove("supabase_user_id")
            .remove("userId")
            .apply()
    }
    
    /**
     * 지정된 액티비티로 이동
     */
    private fun navigateToActivity(activityClass: Class<*>) {
        val intent = Intent(this, activityClass)
        startActivity(intent)
        finish()
    }
}