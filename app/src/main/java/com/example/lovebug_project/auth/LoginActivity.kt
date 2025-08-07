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
                            Toast.makeText(this@LoginActivity, "존재하지 않는 아이디이거나 비밀번호가 일치하지 않습니다.", Toast.LENGTH_SHORT).show()
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
}