package com.example.lovebug_project.auth

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.lovebug_project.R
import com.example.lovebug_project.databinding.ActivityJoinBinding

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

        val signUpBtn = binding.btnRegister

        signUpBtn.setOnClickListener {

            val name = binding.inputName.text.toString().trim()
            val nickname = binding.inputNickname.text.toString().trim()
            val id = binding.inputId.text.toString().trim()
            val password = binding.inputPassword.text.toString().trim()

            if(name.isEmpty() || nickname.isEmpty() || id.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "모든 항목을 입력해주세요.", Toast.LENGTH_SHORT).show()
            } else {
                // 회원가입 성공 토스트 메시지를 보여줍니다.
                Toast.makeText(this, "등록 완료!", Toast.LENGTH_SHORT).show()

                // finish()를 호출하여 현재 액티비티(JoinActivity)를 종료합니다.
                // 이렇게 하면 이전 화면이었던 LoginActivity로 자동으로 돌아갑니다.
                finish()
            }
        }
    }
}