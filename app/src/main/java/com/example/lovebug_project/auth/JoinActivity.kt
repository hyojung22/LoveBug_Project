package com.example.lovebug_project.auth

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.example.lovebug_project.data.db.MyApplication
import com.example.lovebug_project.databinding.ActivityJoinBinding
import kotlinx.coroutines.launch
import java.util.regex.Pattern

class JoinActivity : AppCompatActivity() {
    private var isLoading = false

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
            registerUser(binding)
        }
    }

    private fun setLoadingState(binding: ActivityJoinBinding, loading: Boolean) {
        isLoading = loading
        binding.btnRegister.isEnabled = !loading
        binding.btnRegister.text = if (loading) "처리 중..." else "등록"
        binding.loadingIndicator.visibility = if (loading) View.VISIBLE else View.GONE
        binding.loadingMessage.visibility = if (loading) View.VISIBLE else View.GONE
    }

    private fun registerUser(binding: ActivityJoinBinding) {
        // 중복 클릭 방지
        if (isLoading) {
            return
        }

        val name = binding.inputName.text.toString().trim()
        val nickname = binding.inputNickname.text.toString().trim()
        val email = binding.inputId.text.toString().trim()
        val password = binding.inputPassword.text.toString().trim()

        // 입력값 검증
        if (name.isEmpty() || nickname.isEmpty() || email.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "모든 항목을 입력해주세요.", Toast.LENGTH_SHORT).show()
            return
        }

        // 이메일 형식 검증
        if (!isValidEmail(email)) {
            Toast.makeText(this, "올바른 이메일 형식을 입력해주세요.", Toast.LENGTH_SHORT).show()
            return
        }

        // 비밀번호 검증
        val passwordValidation = validatePassword(password)
        if (!passwordValidation.first) {
            Toast.makeText(this, passwordValidation.second, Toast.LENGTH_LONG).show()
            return
        }

        // 로딩 상태 시작
        setLoadingState(binding, true)
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
                        setLoadingState(binding, false) // 로딩 종료
                        userInfo?.let { user ->
                            // 회원가입 성공 시 사용자 ID 저장
                            val sharedPref = getSharedPreferences("user_prefs", MODE_PRIVATE)
                            sharedPref.edit().putString("supabase_user_id", user.id).apply()
                            
                            // 이메일 인증 안내 다이얼로그 표시 
                            // (DB 트리거가 자동으로 profiles 테이블에 레코드 생성함)
                            showEmailVerificationDialog(email)
                        } ?: run {
                            Toast.makeText(this@JoinActivity, "회원가입 중 오류가 발생했습니다.", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onFailure = { exception: Throwable ->
                        setLoadingState(binding, false) // 로딩 종료
                        Log.e("JoinActivity", "Sign up failed", exception)
                        handleSignUpError(exception)
                    }
                )
            } catch (e: Exception) {
                setLoadingState(binding, false) // 로딩 종료
                Log.e("JoinActivity", "Sign up error", e)
                Toast.makeText(this@JoinActivity, "회원가입 중 오류가 발생했습니다.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun isValidEmail(email: String): Boolean {
        val emailPattern = "[a-zA-Z0-9._-]+@[a-z]+\\.+[a-z]+"
        return Pattern.compile(emailPattern).matcher(email).matches()
    }

    private fun validatePassword(password: String): Pair<Boolean, String> {
        return when {
            password.length < 6 -> Pair(false, "비밀번호는 최소 6자 이상이어야 합니다.")
            password.length > 72 -> Pair(false, "비밀번호는 72자를 초과할 수 없습니다.")
            !password.any { it.isLetter() } -> Pair(false, "비밀번호는 최소 1개의 문자를 포함해야 합니다.")
            !password.any { it.isDigit() } -> Pair(false, "비밀번호는 최소 1개의 숫자를 포함해야 합니다.")
            else -> Pair(true, "")
        }
    }

    private fun handleSignUpError(exception: Throwable) {
        val errorMessage = when {
            exception.message?.contains("already registered", ignoreCase = true) == true || 
            exception.message?.contains("User already registered", ignoreCase = true) == true -> 
                "이미 등록된 이메일입니다."
            exception.message?.contains("Invalid email", ignoreCase = true) == true -> 
                "올바른 이메일 주소를 입력해주세요."
            exception.message?.contains("Password should be", ignoreCase = true) == true || 
            exception.message?.contains("password", ignoreCase = true) == true -> 
                "비밀번호는 최소 6자 이상이어야 합니다."
            exception.message?.contains("network", ignoreCase = true) == true -> 
                "네트워크 연결을 확인해주세요."
            else -> 
                "회원가입 중 오류가 발생했습니다: ${exception.message ?: "알 수 없는 오류"}"
        }
        Toast.makeText(this, errorMessage, Toast.LENGTH_LONG).show()
    }

    private fun showEmailVerificationDialog(email: String) {
        AlertDialog.Builder(this)
            .setTitle("이메일 인증 필요")
            .setMessage(
                "회원가입이 완료되었습니다!\n\n" +
                "$email 으로 인증 이메일을 발송했습니다.\n" +
                "이메일의 인증 링크를 클릭하여 계정을 활성화한 후 로그인해주세요.\n\n" +
                "※ 스팸 폴더도 확인해보세요."
            )
            .setPositiveButton("확인") { _, _ ->
                // 로그인 화면으로 이동
                val intent = Intent(this, LoginActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
                startActivity(intent)
                finish()
            }
            .setCancelable(false)
            .show()
    }
}