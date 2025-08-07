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
import com.example.lovebug_project.data.supabase.models.Post
import com.example.lovebug_project.databinding.ActivityBoardWriteBinding
import com.example.lovebug_project.utils.AuthHelper
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
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

            // 현재 로그인 유저 ID 검증 (Supabase UUID)
            val currentSupabaseUserId = AuthHelper.getSupabaseUserId(this)
            
            if (currentSupabaseUserId == null) {
                Toast.makeText(this, "로그인이 필요합니다.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // 비동기 작업으로 게시글 생성
            lifecycleScope.launch {
                try {
                    // 작성 시간 (ISO 형식)
                    val now = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault()).format(Date())

                    // 이미지 업로드 (있는 경우)
                    val uploadedImageUrl = if (selectedImageUri != null) {
                        uploadImageToSupabase(selectedImageUri!!, currentSupabaseUserId)
                    } else null

                    // Post 객체 생성
                    val newPost = Post(
                        userId = currentSupabaseUserId, // Supabase Auth UUID
                        title = title,
                        content = content,
                        image = uploadedImageUrl, // Supabase Storage URL 저장
                        createdAt = now
                    )

                    // Enhanced Supabase repository를 통해 게시글 생성 (캐시 포함)
                    val repositoryManager = MyApplication.repositoryManager
                    val result = repositoryManager.cachedPostRepository.createPost(newPost)
                    
                    result.fold(
                        onSuccess = { createdPost ->
                            // 성공 메시지
                            Toast.makeText(this@BoardWriteActivity, "게시글이 등록되었습니다.", Toast.LENGTH_SHORT).show()
                            
                            // MainActivity로 돌아가면서 게시판 탭으로 이동
                            val intent = Intent(this@BoardWriteActivity, MainActivity::class.java).apply {
                                putExtra("navigateToBoard", true)
                                putExtra("newPostId", createdPost.postId)
                                // FLAG_CLEAR_TOP을 사용해 기존 MainActivity 인스턴스를 재사용
                                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                            }
                            startActivity(intent)
                            finish()
                        },
                        onFailure = { exception ->
                            Toast.makeText(this@BoardWriteActivity, "게시글 등록 실패: ${exception.message}", Toast.LENGTH_SHORT).show()
                        }
                    )
                } catch (e: Exception) {
                    Toast.makeText(this@BoardWriteActivity, "게시글 등록 실패: ${e.message}", Toast.LENGTH_SHORT).show()
                }
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

    /**
     * Supabase Storage에 이미지 업로드
     */
    private suspend fun uploadImageToSupabase(imageUri: Uri, userId: String): String? {
        return try {
            val repositoryManager = MyApplication.repositoryManager
            
            // 버킷 초기화 및 존재 확인
            val initResult = repositoryManager.imageRepository.initializeBucket()
            if (initResult.isFailure) {
                Toast.makeText(this, "버킷 초기화 실패: ${initResult.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
            }
            
            val result = repositoryManager.imageRepository.uploadPostImage(
                context = this,
                imageUri = imageUri,
                userId = userId
            )
            
            result.fold(
                onSuccess = { imageUrl ->
                    Toast.makeText(this, "이미지 업로드 완료", Toast.LENGTH_SHORT).show()
                    imageUrl
                },
                onFailure = { exception ->
                    Toast.makeText(this, "이미지 업로드 실패: ${exception.message}", Toast.LENGTH_LONG).show()
                    null
                }
            )
        } catch (e: Exception) {
            Toast.makeText(this, "이미지 업로드 중 오류 발생: ${e.message}", Toast.LENGTH_LONG).show()
            null
        }
    }

    companion object {
        private const val REQUEST_IMAGE_PICK = 1001
    }
}