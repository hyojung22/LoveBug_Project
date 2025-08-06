package com.example.lovebug_project

import android.os.Bundle
import android.view.View
import androidx.activity.addCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.example.lovebug_project.board.BoardMainFragment
import com.example.lovebug_project.chat.ChatFragment
import com.example.lovebug_project.chatlist.ChatListFragment
import com.example.lovebug_project.databinding.ActivityMainBinding
import com.example.lovebug_project.databinding.FragmentHomeBinding
import com.example.lovebug_project.home.HomeFragment
import com.example.lovebug_project.mypage.MypageFragment

class MainActivity : AppCompatActivity() {

    private lateinit var binding : ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
//        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (intent.getBooleanExtra("navigateToBoard", false)) {
            binding.nav.selectedItemId = R.id.btnBoard
        }

        // 하드웨어 뒤로가기 버튼 처리
        onBackPressedDispatcher.addCallback(this) {
            val frame2 = findViewById<View>(R.id.frame2)
            val frame = findViewById<View>(R.id.frame)
            val titleBar = findViewById<View>(R.id.clTitleBar)

            if (frame2.visibility == View.VISIBLE) {
                // 상세 페이지 → 리스트 페이지로
                supportFragmentManager.popBackStack()
                frame2.visibility = View.GONE
                frame.visibility = View.VISIBLE
                titleBar.visibility = View.GONE
            } else {
                // 기본 동작 (앱 종료)
                finish()
            }
        }

        findViewById<View>(R.id.btnBack).setOnClickListener {
            supportFragmentManager.popBackStack()

            findViewById<View>(R.id.frame2).visibility = View.GONE
            findViewById<View>(R.id.frame).visibility = View.VISIBLE
            findViewById<View>(R.id.clTitleBar).visibility = View.GONE
        }

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
                R.id.btnChat -> { // 수정된 부분
                    supportFragmentManager.beginTransaction()
                        .replace(R.id.frame, ChatListFragment()).commit() // ChatListFragment로 변경
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