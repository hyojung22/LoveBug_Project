package com.example.lovebug_project

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.example.lovebug_project.auth.LoginActivity
import com.example.lovebug_project.databinding.ActivitySplashBinding

class SplashActivity : AppCompatActivity() {

    private lateinit var binding : ActivitySplashBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 1. 스플래시 이미지들을 리스트로 만듭니다.
        // (리소스 이름은 소문자여야 합니다: splash1, splash2, ...)
        val splashImages = listOf(
            R.drawable.splash1,
            R.drawable.splash2,
            R.drawable.splash3,
            R.drawable.splash4
        )

        // 2. 리스트 중에서 랜덤으로 하나를 선택합니다.
        val randomImage = splashImages.random()

        // 3. 레이아웃의 ImageView(id: splashImageView)에 선택된 이미지를 설정합니다.
        binding.splashImageView.setImageResource(randomImage)


        // 4. 3초 후에 로그인 화면으로 이동합니다.
        Handler(Looper.getMainLooper()).postDelayed({
            val intent = Intent(this, LoginActivity::class.java)
            startActivity(intent)
            finish() // 스플래시 화면을 종료하여 뒤로 가기 시 다시 보이지 않게 합니다.
        }, 3000)
    }
}