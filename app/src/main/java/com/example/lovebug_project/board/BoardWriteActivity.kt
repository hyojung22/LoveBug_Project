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

            // 현재 로그인 유저 ID
            val currentUserId = getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
                .getInt("userId", -1)

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
            postDao.insert(newPost)

            // 저장된 최신 글 가져오기
            val savedPost = postDao.getAllPosts().firstOrNull() ?: return@setOnClickListener

            val userDao = MyApplication.database.userDao()
            val nickname = userDao.getUserById(savedPost.userId)?.nickname ?: "알 수 없음"

            val likeDao = MyApplication.database.likeDao()
            val commentDao = MyApplication.database.commentDao()

            val postWithExtras = PostWithExtras(
                post = savedPost,
                nickname = nickname,
                profileImage = null,
                likeCount = likeDao.getLikeCountByPost(savedPost.postId),
                commentCount = commentDao.getCommentCountByPost(savedPost.postId),
                isBookmarked = false
            )

            val intent = Intent(this, MainActivity::class.java).apply {
                putExtra("navigateToBoard", true)
            }
            startActivity(intent)
            finish()

//            // 상세 페이지로 이동
//            val fragment = BoardDetailFragment().apply {
//                arguments = Bundle().apply {
//                    putSerializable("postExtra", postWithExtras)
//                }
//            }
//
//            supportFragmentManager.beginTransaction()
//                .replace(R.id.frame, fragment) // FrameLayout ID 확인 필요
//                .addToBackStack(null)
//                .commit()
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