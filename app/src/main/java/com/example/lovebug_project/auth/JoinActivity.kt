package com.example.lovebug_project.auth

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.lovebug_project.R
import com.example.lovebug_project.data.db.MyApplication
import com.example.lovebug_project.data.db.entity.User
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

            if (name.isEmpty() || nickname.isEmpty() || id.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "모든 항목을 입력해주세요.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // User 객체 생성
            val newUser = User(
                username = name,
                nickname = nickname,
                userLoginId = id,
                password = password,
                profileImage = null
            )

            // DB에 저장
            MyApplication.database.userDao().insert(newUser)

            Toast.makeText(this, "등록 완료!", Toast.LENGTH_SHORT).show()

            // PayActivity로 이동
            startActivity(Intent(this, PayActivity::class.java))
            finish()


        }
    }
}