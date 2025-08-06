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
import com.example.lovebug_project.databinding.ActivityLoginBinding

class LoginActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val binding = ActivityLoginBinding.inflate(layoutInflater)

        setContentView(binding.root)

        val loginButton = binding.loginBtn

        val signUpButton = binding.btnGoSignUp


        loginButton.setOnClickListener {
            val email = binding.emailArea.text.toString()
            val password = binding.passwordArea.text.toString()

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "이메일 또는 비밀번호를 입력해주세요.", Toast.LENGTH_SHORT).show()
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