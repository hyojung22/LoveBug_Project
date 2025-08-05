package com.example.lovebug_project

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.example.lovebug_project.board.BoardMainFragment
import com.example.lovebug_project.chat.ChatFragment
import com.example.lovebug_project.databinding.ActivityMainBinding
import com.example.lovebug_project.databinding.FragmentHomeBinding
import com.example.lovebug_project.home.HomeFragment
import com.example.lovebug_project.mypage.MypageFragment

class MainActivity : AppCompatActivity() {

    private lateinit var binding : ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 초기화 화면으로 쓸 수 있는 fragment를 지정!
        supportFragmentManager.beginTransaction()
            .replace(R.id.frame, HomeFragment()).commit()

        // 각각의 버튼을 클릭했을 때 해당하는 fragment가 띄워지도록 연결
        binding.nav.setOnItemSelectedListener {
            // 선택된 버튼을 판단!
            when(it.itemId) {
                R.id.btnHome -> {
                    supportFragmentManager.beginTransaction()
                        .replace(R.id.frame, HomeFragment()).commit()
                }
                R.id.btnBoard -> {
                    supportFragmentManager.beginTransaction()
                        .replace(R.id.frame, BoardMainFragment()).commit()
                }
                R.id.btnChat -> {
                    supportFragmentManager.beginTransaction()
                        .replace(R.id.frame, ChatFragment()).commit()
                }
                R.id.btnMyPage -> {
                    supportFragmentManager.beginTransaction()
                        .replace(R.id.frame, MypageFragment()).commit()
                }
            }
            true
        }
    }
}