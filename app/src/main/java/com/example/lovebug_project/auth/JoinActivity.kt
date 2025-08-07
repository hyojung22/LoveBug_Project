package com.example.lovebug_project.auth

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
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

        // "등록" 버튼에 대한 클릭 리스너를 한 번만 설정합니다.
        binding.btnRegister.setOnClickListener {

            val name = binding.inputName.text.toString().trim()
            val nickname = binding.inputNickname.text.toString().trim()
            val id = binding.inputId.text.toString().trim()
            val password = binding.inputPassword.text.toString().trim()

            // 모든 필드가 채워져 있는지 확인합니다.
            if (name.isEmpty() || nickname.isEmpty() || id.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "모든 항목을 입력해주세요.", Toast.LENGTH_SHORT).show()
            } else {
                // 모든 필드가 채워져 있다면,
                // 1. "등록 완료!" 토스트 메시지를 보여줍니다.
                Toast.makeText(this, "등록 완료!", Toast.LENGTH_SHORT).show()

                // 2. PayActivity로 이동할 Intent를 생성하고 실행합니다.
                val intent = Intent(this, PayActivity::class.java)
                startActivity(intent)

                // 3. 현재 JoinActivity를 종료하여 뒤로 가기 버튼으로 돌아오지 않도록 합니다.
                finish()
            }
        }
    }
}