package com.example.lovebug_project

import android.os.Bundle
import android.view.View
import androidx.activity.addCallback // OnBackPressedCallback 대신 enable을 사용하려면 activity-ktx 의존성 확인
// import androidx.activity.OnBackPressedCallback // 명시적으로 사용하려면 import
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.lovebug_project.board.BoardMainFragment
// import com.example.lovebug_project.chat.ChatFragment // 사용되지 않는 import
import com.example.lovebug_project.chatlist.ChatListFragment
import com.example.lovebug_project.databinding.ActivityMainBinding
// import com.example.lovebug_project.databinding.FragmentHomeBinding // 사용되지 않는 import
import com.example.lovebug_project.home.HomeFragment
import com.example.lovebug_project.mypage.MypageFragment
import com.example.lovebug_project.utils.AuthHelper
import com.example.lovebug_project.auth.LoginActivity
import android.content.Intent
import android.widget.Toast
import androidx.fragment.app.Fragment // Fragment 클래스 import

class MainActivity : AppCompatActivity() {

    private lateinit var binding : ActivityMainBinding
    private var backPressedTime: Long = 0

    // 현재 R.id.frame 에 표시된 프래그먼트가 MypageFragment인지 식별하기 위한 플래그
    // 좀 더 나은 방법은 MypageFragment에 태그를 주고 findFragmentByTag로 확인하는 것이지만,
    // 최소 변경을 위해 간단한 플래그를 사용할 수 있습니다. (단, 복잡한 상황에서는 부적절할 수 있음)
    // private var isMypageFragmentVisible = false // 이 방법 대신 backStackEntryCount 사용

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!AuthHelper.isLoggedIn(this)) {
            val intent = Intent(this, LoginActivity::class.java)
            startActivity(intent)
            finish()
            return
        }

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // 하드웨어 뒤로가기 버튼 처리
        // OnBackPressedCallback 생성 시 enabled = true 로 설정해야 콜백이 활성화됩니다.
        onBackPressedDispatcher.addCallback(this /* lifecycleOwner */, true /* enabled */) {
            // 콜백 내용 (기존 로직과 통합)
            val frame2 = findViewById<View>(R.id.frame2)
            // val frame = findViewById<View>(R.id.frame) // 직접 사용되지 않음
            // val titleBar = findViewById<View>(R.id.clTitleBar) // 직접 사용되지 않음

            if (frame2.visibility == View.VISIBLE) {
                // 상세 페이지(frame2) 보이는 경우 -> 이전 프래그먼트(BoardMainFragment 또는 MypageFragment 내부의 상세 등)로
                supportFragmentManager.popBackStack() // 상세페이지 프래그먼트를 pop
                resetToMainView() // UI 복원 (frame, frame2, clTitleBar 가시성)

                // 상세 페이지에서 돌아왔을 때, 이전 탭으로 네비게이션 아이템을 설정해야 할 수 있음
                // 예를 들어, BoardMainFragment에서 상세로 갔다가 돌아오면 btnBoard가 선택되어야 함.
                // 현재는 고정된 탭으로 설정하거나, 아무것도 안 함.
                // 이전 코드에서는 binding.nav.selectedItemId = R.id.btnBoard 로 했었음.
                // 이 부분은 상세 페이지로 이동할 때 어떤 탭에서 왔는지 정보를 저장했다가 복원하는 것이 가장 좋음.
                // 여기서는 일단 주석 처리.
                // binding.nav.selectedItemId = R.id.btnBoard
            }
            // <<<--- 새로운 조건 추가: MypageFragment 등이 백스택에 있는 경우 ---<<<
            else if (supportFragmentManager.backStackEntryCount > 0) {
                // frame2가 보이지 않고, 메인 프래그먼트 백스택에 항목이 있는 경우
                // (예: MypageFragment에서 HomeFragment로 돌아가려는 경우)
                supportFragmentManager.popBackStack() // MypageFragment를 pop
                // MypageFragment가 닫히면, 그 이전 프래그먼트(예: HomeFragment)가 보이게 됨.
                // 이때 하단 네비게이션 탭도 이전 프래그먼트에 맞게 업데이트해야 함.
                updateBottomNavSelection() // 백스택 pop 후 현재 프래그먼트에 맞게 탭 업데이트 함수 호출
            }
            // --- 여기까지 새로운 조건 ---
            else {
                // 백스택에 아무것도 없고, frame2도 안 보이면 -> "2번 눌러 종료" 로직
                val currentTime = System.currentTimeMillis()
                if (currentTime - backPressedTime < 2000) {
                    finish()
                } else {
                    backPressedTime = currentTime
                    Toast.makeText(applicationContext, getString(R.string.back_press_exit_message), Toast.LENGTH_SHORT).show()
                }
            }
        } // onBackPressedDispatcher.addCallback 끝

        findViewById<View>(R.id.btnBack).setOnClickListener { // 타이틀 바의 뒤로가기 버튼
            // 이 버튼은 주로 frame2 (상세 페이지)가 보일 때 사용됨.
            supportFragmentManager.popBackStack()
            resetToMainView()
            // binding.nav.selectedItemId = R.id.btnBoard // 이전 코드. 상세페이지가 게시판에서 왔다는 가정.
            // 역시 어떤 탭으로 돌아갈지 결정하는 로직 필요.
        }

        handleNavigationIntent(intent) // 이 함수는 초기 프래그먼트 설정에 영향을 줄 수 있음

        binding.nav.setOnItemSelectedListener { menuItem ->
            resetToMainView() // 다른 탭으로 이동 시 상세 페이지(frame2) 숨김

            val transaction = supportFragmentManager.beginTransaction()

            when (menuItem.itemId) {
                R.id.btnHome -> {
                    transaction.replace(R.id.frame, HomeFragment())
                    // HomeFragment는 백스택에 추가하지 않음 (기존 동작 유지)
                }
                R.id.btnBoard -> {
                    transaction.replace(R.id.frame, BoardMainFragment())
                    // BoardMainFragment도 백스택에 추가하지 않음 (기존 동작 유지)
                }
                R.id.btnChat -> {
                    transaction.replace(R.id.frame, ChatListFragment())
                    // ChatListFragment도 백스택에 추가하지 않음 (기존 동작 유지)
                }
                R.id.btnMyPage -> {
                    transaction.replace(R.id.frame, MypageFragment())
                    // *** MypageFragment만 백스택에 추가 ***
                    transaction.addToBackStack("MypageFragment") // 태그는 식별을 위해 추가 (null도 가능)
                }
            }
            // 중요: addToBackStack을 사용했다면, commit() 전에 호출해야 함.
            // 그리고 이 트랜잭션이 백스택에 영향을 미치므로,
            // 다른 프래그먼트로 교체 시 이전 백스택 상태에 따라 동작이 달라질 수 있음을 유의.
            // 예를 들어 Home -> MyPage (백스택) -> Board (MyPage 대체, 백스택 없음).
            // 이 상태에서 뒤로가기 누르면 앱 종료 (Board가 백스택에 없으므로).
            // 모든 탭 프래그먼트를 일관되게 백스택에 넣거나, 아니면 Home만 제외하고 넣는 등의 전략 필요.
            // 여기서는 요청대로 MypageFragment만 백스택에 넣는 것으로 가정.
            transaction.commit()
            true
        }

        // 초기 프래그먼트 설정 (onCreate가 처음 호출될 때만)
        if (savedInstanceState == null) {
            if (intent?.getBooleanExtra("navigateToBoard", false) == true) {
                // navigateToBoard 인텐트가 있으면 BoardMainFragment를 표시
                supportFragmentManager.beginTransaction()
                    .replace(R.id.frame, BoardMainFragment())
                    .commit()
                binding.nav.selectedItemId = R.id.btnBoard
            } else {
                // 그 외의 경우, HomeFragment를 기본으로 표시
                supportFragmentManager.beginTransaction()
                    .replace(R.id.frame, HomeFragment())
                    .commit()
                binding.nav.selectedItemId = R.id.btnHome // 하단 네비게이션도 Home으로 설정
            }
        }
    } // onCreate 끝

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Activity가 이미 실행 중일 때 새로운 인텐트를 받으면 처리
        handleNavigationIntent(intent)
    }

    private fun handleNavigationIntent(intent: Intent?) {
        if (intent?.getBooleanExtra("navigateToBoard", false) == true) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.frame, BoardMainFragment())
                .commit()
            binding.nav.selectedItemId = R.id.btnBoard
        }
        // 다른 인텐트 기반 네비게이션 로직이 있다면 여기에 추가
    }

    private fun resetToMainView() {
        findViewById<View>(R.id.frame2).visibility = View.GONE
        findViewById<View>(R.id.frame).visibility = View.VISIBLE
        findViewById<View>(R.id.clTitleBar).visibility = View.GONE
    }

    // 백스택 pop 후 현재 프래그먼트에 맞게 하단 네비게이션 아이템을 업데이트하는 함수
    private fun updateBottomNavSelection() {
        val currentFragment = supportFragmentManager.findFragmentById(R.id.frame)
        when (currentFragment) {
            is HomeFragment -> binding.nav.selectedItemId = R.id.btnHome
            is BoardMainFragment -> binding.nav.selectedItemId = R.id.btnBoard
            is ChatListFragment -> binding.nav.selectedItemId = R.id.btnChat
            is MypageFragment -> binding.nav.selectedItemId = R.id.btnMyPage
            // 만약 MypageFragment에서 popBackStack 후 이전 프래그먼트가 MypageFragment가 아니라면
            // (즉, MypageFragment가 스택의 최상단이 아니었다면) 이 로직은 문제가 될 수 있음.
            // 하지만 현재 구조에서는 MypageFragment에서 뒤로가면 그 이전의 탭 프래그먼트가 나올 것이므로 괜찮음.
        }
    }
}
