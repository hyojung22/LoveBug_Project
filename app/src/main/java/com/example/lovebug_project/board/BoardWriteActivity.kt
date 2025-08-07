package com.example.lovebug_project.board

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.bumptech.glide.Glide
import com.example.lovebug_project.MainActivity
import com.example.lovebug_project.R
import com.example.lovebug_project.data.db.MyApplication
import com.example.lovebug_project.data.db.entity.Post
import com.example.lovebug_project.data.db.entity.PostWithExtras
import com.example.lovebug_project.databinding.ActivityBoardWriteBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class BoardWriteActivity : AppCompatActivity() {

    private lateinit var binding : ActivityBoardWriteBinding
    private var selectedImageUri: Uri? = null // 선택한 이미지 경로

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityBoardWriteBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // 뒤로 가기 버튼
        binding.btnBack.setOnClickListener {
            finish()
        }

        // 이미지 선택
        binding.img.setOnClickListener {
            val intent = Intent(Intent.ACTION_PICK).apply {
                type = "image/*"
            }
            startActivityForResult(intent, REQUEST_IMAGE_PICK)
        }

        // 등록하기 버튼 클릭 시 게시글 등록 DB 반영
        binding.btnRegister.setOnClickListener {
            val title = binding.etTitle.text.toString().trim()
            val content = binding.etContent.text.toString().trim()

            // 제목/내용 유효성 검사
            if (title.isEmpty() || content.isEmpty()) {
                Toast.makeText(this, "제목과 내용을 모두 입력하세요.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // 현재 로그인 유저 ID 검증
            val currentUserId = getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
                .getInt("userId", -1)
            
            if (currentUserId == -1) {
                Toast.makeText(this, "로그인이 필요합니다.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            try {
                // 작성 시간 (형식 : yyyy-MM-dd HH:mm)
                val now = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())

                // Post 객체 생성
                val newPost = Post(
                    userId = currentUserId,
                    title = title,
                    content = content,
                    image = selectedImageUri?.toString(), // 이미지 URI 저장
                    createdAt = now
                )

                val postDao = MyApplication.database.postDao()
                // Insert하고 반환된 ID를 받음
                val newId = postDao.insert(newPost).toInt()
                
                // 성공 메시지
                Toast.makeText(this, "게시글이 등록되었습니다.", Toast.LENGTH_SHORT).show()
                
                // MainActivity로 돌아가면서 게시판 탭으로 이동
                val intent = Intent(this, MainActivity::class.java).apply {
                    putExtra("navigateToBoard", true)
                    putExtra("newPostId", newId)
                    // FLAG_CLEAR_TOP을 사용해 기존 MainActivity 인스턴스를 재사용
                    flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                }
                startActivity(intent)
                finish()
                
            } catch (e: Exception) {
                // 데이터베이스 오류 처리
                Toast.makeText(this, "게시글 등록 중 오류가 발생했습니다.", Toast.LENGTH_SHORT).show()
                // 로그 출력 (개발용)
                e.printStackTrace()
            }
        }
    }

    // 이미지 선택 결과 처리
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == REQUEST_IMAGE_PICK && resultCode == Activity.RESULT_OK) {
            selectedImageUri = data?.data
            Glide.with(this)
                .load(selectedImageUri)
                .into(binding.img) // 선택한 이미지 미리보기
        }
    }

    companion object {
        private const val REQUEST_IMAGE_PICK = 1001
    }
}