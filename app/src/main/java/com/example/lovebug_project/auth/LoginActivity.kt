package com.example.lovebug_project.auth

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.example.lovebug_project.MainActivity
import com.example.lovebug_project.R
import com.example.lovebug_project.data.db.MyApplication
import com.example.lovebug_project.databinding.ActivityLoginBinding
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val binding = ActivityLoginBinding.inflate(layoutInflater)

        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // 로그인 버튼
        val loginButton = binding.loginBtn

        // 회원가입 하러가기 버튼
        val signUpButton = binding.btnGoSignUp

        loginButton.setOnClickListener {
            val email = binding.emailArea.text.toString()
            val password = binding.passwordArea.text.toString()

            // 입력창 공란이면
            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "이메일 또는 비밀번호를 입력해주세요.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Supabase로 로그인 시도
            lifecycleScope.launch {
                try {
                    val result = MyApplication.authRepository.signIn(email, password)
                    
                    result.fold(
                        onSuccess = { session: io.github.jan.supabase.auth.user.UserSession ->
                            // 로그인 성공 시 사용자 ID 저장
                            val sharedPref = getSharedPreferences("user_prefs", MODE_PRIVATE)
                            sharedPref.edit().putString("supabase_user_id", session.user?.id).apply()
                            
                            // 메인 페이지로 이동
                            val intent = Intent(this@LoginActivity, MainActivity::class.java)
                            startActivity(intent)
                            finish() // 로그인 액티비티 종료
                        },
                        onFailure = { exception: Throwable ->
                            // 로그인 실패
                            Log.e("LoginActivity", "Login failed", exception)
                            handleSignInError(exception)
                        }
                    )
                } catch (e: Exception) {
                    Log.e("LoginActivity", "Login error", e)
                    Toast.makeText(this@LoginActivity, "로그인 중 오류가 발생했습니다.", Toast.LENGTH_SHORT).show()
                }
            }
        }

        signUpButton.setOnClickListener {
            // Intent를 생성하여 현재 액티비티(this)에서 JoinActivity로 이동하도록 설정합니다.
            val intent = Intent(this, JoinActivity::class.java)

            // startActivity() 메소드에 intent를 전달하여 새로운 액티비티를 시작합니다.
            startActivity(intent)
        }
    }

    private fun handleSignInError(exception: Throwable) {
        val errorMessage = when {
            exception.message?.contains("Invalid login credentials", ignoreCase = true) == true ||
            exception.message?.contains("invalid", ignoreCase = true) == true -> 
                "이메일 또는 비밀번호가 일치하지 않습니다."
            exception.message?.contains("Email not confirmed", ignoreCase = true) == true ||
            exception.message?.contains("email_not_confirmed", ignoreCase = true) == true -> 
                "이메일 인증이 완료되지 않았습니다.\n이메일의 인증 링크를 클릭해주세요."
            exception.message?.contains("Too many requests", ignoreCase = true) == true -> 
                "너무 많은 로그인 시도가 있었습니다.\n잠시 후 다시 시도해주세요."
            exception.message?.contains("network", ignoreCase = true) == true -> 
                "네트워크 연결을 확인해주세요."
            exception.message?.contains("User not found", ignoreCase = true) == true -> 
                "등록되지 않은 이메일입니다."
            else -> 
                "로그인 중 오류가 발생했습니다.\n이메일과 비밀번호를 확인해주세요."
        }
        Toast.makeText(this, errorMessage, Toast.LENGTH_LONG).show()
    }
}