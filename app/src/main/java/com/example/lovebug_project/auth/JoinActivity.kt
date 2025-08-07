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
import com.example.lovebug_project.data.db.MyApplication
import com.example.lovebug_project.databinding.ActivityJoinBinding
import kotlinx.coroutines.launch

class JoinActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val binding = ActivityJoinBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // "등록" 버튼에 대한 클릭 리스너를 한 번만 설정합니다.
        binding.btnRegister.setOnClickListener {

            val name = binding.inputName.text.toString().trim()
            val nickname = binding.inputNickname.text.toString().trim()
            val email = binding.inputId.text.toString().trim() // 이메일로 사용
            val password = binding.inputPassword.text.toString().trim()

            // 모든 필드가 채워져 있는지 확인합니다.
            if (name.isEmpty() || nickname.isEmpty() || email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "모든 항목을 입력해주세요.", Toast.LENGTH_SHORT).show()
            } else {
                // Supabase로 회원가입 시도
                lifecycleScope.launch {
                    try {
                        val result = MyApplication.authRepository.signUp(
                            email = email,
                            password = password,
                            username = name,
                            nickname = nickname
                        )
                        
                        result.fold(
                            onSuccess = { userInfo: io.github.jan.supabase.auth.user.UserInfo? ->
                                userInfo?.let { user ->
                                    // 회원가입 성공 시 사용자 ID 저장
                                    val sharedPref = getSharedPreferences("user_prefs", MODE_PRIVATE)
                                    sharedPref.edit().putString("supabase_user_id", user.id).apply()
                                    
                                    // "등록 완료!" 토스트 메시지를 보여줍니다.
                                    Toast.makeText(this@JoinActivity, "등록 완료!", Toast.LENGTH_SHORT).show()
                                    
                                    // PayActivity로 이동할 Intent를 생성하고 실행합니다.
                                    val intent = Intent(this@JoinActivity, PayActivity::class.java)
                                    startActivity(intent)
                                    
                                    // 현재 JoinActivity를 종료하여 뒤로 가기 버튼으로 돌아오지 않도록 합니다.
                                    finish()
                                } ?: run {
                                    // Handle case where userInfo is null (shouldn't happen on success)
                                    Toast.makeText(this@JoinActivity, "회원가입 중 오류가 발생했습니다.", Toast.LENGTH_SHORT).show()
                                }
                            },
                            onFailure = { exception: Throwable ->
                                Log.e("JoinActivity", "Sign up failed", exception)
                                val errorMessage = when {
                                    exception.message?.contains("already registered") == true -> 
                                        "이미 등록된 이메일입니다."
                                    exception.message?.contains("password") == true -> 
                                        "비밀번호는 최소 6자 이상이어야 합니다."
                                    else -> 
                                        "회원가입 중 오류가 발생했습니다."
                                }
                                Toast.makeText(this@JoinActivity, errorMessage, Toast.LENGTH_SHORT).show()
                            }
                        )
                    } catch (e: Exception) {
                        Log.e("JoinActivity", "Sign up error", e)
                        Toast.makeText(this@JoinActivity, "회원가입 중 오류가 발생했습니다.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }
}