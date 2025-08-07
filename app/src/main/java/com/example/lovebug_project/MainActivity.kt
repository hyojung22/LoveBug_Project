package com.example.lovebug_project

import android.os.Bundle
import android.view.View
import androidx.activity.addCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.lovebug_project.board.BoardMainFragment
import com.example.lovebug_project.chat.ChatFragment
import com.example.lovebug_project.chatlist.ChatListFragment
import com.example.lovebug_project.databinding.ActivityMainBinding
import com.example.lovebug_project.databinding.FragmentHomeBinding
import com.example.lovebug_project.home.HomeFragment
import com.example.lovebug_project.mypage.MypageFragment
import com.example.lovebug_project.utils.AuthHelper
import com.example.lovebug_project.auth.LoginActivity
import android.content.Intent

class MainActivity : AppCompatActivity() {

    private lateinit var binding : ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 로그인 상태 확인
        if (!AuthHelper.isLoggedIn(this)) {
            // 로그인되어 있지 않으면 LoginActivity로 이동
            val intent = Intent(this, LoginActivity::class.java)
            startActivity(intent)
            finish()
            return
        }
        
//        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
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
                
                // 하단 네비게이션을 Board 탭으로 설정 (게시글 목록으로 돌아가므로)
                binding.nav.selectedItemId = R.id.btnBoard
            } else {
                // 기본 동작 (앱 종료)
                finish()
            }
        }

        findViewById<View>(R.id.btnBack).setOnClickListener {
            // 하드웨어 백버튼과 동일한 로직: 이전 Fragment로 복귀
            supportFragmentManager.popBackStack()
            
            // UI를 메인 뷰로 복원
            resetToMainView()
            
            // 하단 네비게이션을 Board 탭으로 설정 (게시글 목록으로 돌아가므로)
            binding.nav.selectedItemId = R.id.btnBoard
        }

        // Intent 처리 - 게시글 작성 후 돌아왔는지 확인
        handleNavigationIntent(intent)

        // 각각의 버튼을 클릭했을 때 해당하는 fragment가 띄워지도록 연결
        binding.nav.setOnItemSelectedListener {
            // 상세 페이지에서 메인 페이지로 복원
            resetToMainView()
            
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
    
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Handle the new intent when activity is already running
        handleNavigationIntent(intent)
    }
    
    private fun handleNavigationIntent(intent: Intent) {
        if (intent.getBooleanExtra("navigateToBoard", false)) {
            // 게시판 탭으로 이동
            supportFragmentManager.beginTransaction()
                .replace(R.id.frame, BoardMainFragment()).commit()
            // 하단 네비게이션도 게시판으로 설정
            binding.nav.selectedItemId = R.id.btnBoard
        } else if (!isFinishing) {
            // 초기화 화면으로 쓸 수 있는 fragment를 지정!
            supportFragmentManager.beginTransaction()
                .replace(R.id.frame, HomeFragment()).commit()
        }
    }
    
    /**
     * UI를 메인 뷰 상태로 복원합니다.
     * 상세 페이지(frame2)를 숨기고 메인 화면(frame)을 표시하며, 상단 타이틀바를 숨깁니다.
     */
    private fun resetToMainView() {
        findViewById<View>(R.id.frame2).visibility = View.GONE
        findViewById<View>(R.id.frame).visibility = View.VISIBLE
        findViewById<View>(R.id.clTitleBar).visibility = View.GONE
    }

}