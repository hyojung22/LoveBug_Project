package com.example.lovebug_project.auth

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
// MainActivity를 사용하기 위해 import 합니다. (패키지 경로는 프로젝트에 맞게 확인해주세요)
import com.example.lovebug_project.MainActivity
import com.example.lovebug_project.R
import com.example.lovebug_project.databinding.ActivityWelcomeBinding

class WelcomeActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val binding = ActivityWelcomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

//        // 화면 전체(root 뷰)에 클릭 리스너를 설정합니다.
//        binding.root.setOnClickListener {
//            // MainActivity로 화면을 전환하기 위한 Intent를 생성합니다.
//            val intent = Intent(this, MainActivity::class.java)
//            // 생성한 Intent를 실행하여 MainActivity를 시작합니다.
//            startActivity(intent)
//            // 현재 WelcomeActivity를 종료하여, 뒤로가기 시 다시 보이지 않도록 합니다.
//            finish()
//        }
//
//        // 기존의 EditText에 대한 클릭 리스너는 이제 필요 없으므로 삭제하거나 주석 처리합니다.
//        /*
//        val etWelcome = binding.etWelcome
//        etWelcome.setOnClickListener {
//            val text = binding.etWelcome.text.toString()
//        }
//        */


        // Process : 프로그램을 실행시키는 것
        // Thread : 최소 작업 단위, 프로세스 안에서 동작하는 작업
        Handler().postDelayed({
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
        }, 3000)
    }
}