package com.example.lovebug_project.board

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.os.bundleOf
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.bumptech.glide.Glide
import com.example.lovebug_project.R
import com.example.lovebug_project.data.db.MyApplication
import com.example.lovebug_project.data.db.entity.Comment
import com.example.lovebug_project.data.db.entity.Like
import com.example.lovebug_project.data.db.entity.Post
import com.example.lovebug_project.data.db.entity.PostWithExtras
import com.example.lovebug_project.databinding.FragmentBoardDetailBinding
import com.example.lovebug_project.databinding.FragmentBoardMainBinding

class BoardDetailFragment : Fragment() {
    // binding 인스턴스를 nullable로 선언
    private var _binding: FragmentBoardDetailBinding? = null
    // 안전하게 binding을 꺼내 쓰는 프로퍼티
    private val binding get() = _binding!!

    private lateinit var commentAdapter: CommentAdapter
    private lateinit var postExtra: PostWithExtras // 전역으로 변경

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // inflate 대신 binding.inflate 사용
        _binding = FragmentBoardDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // 예: binding.spinnerSort, binding.rvBoard 등 바로 접근 가능

        postExtra = arguments?.getSerializable("postExtra") as? PostWithExtras ?: return

        // 🗑️ 삭제 버튼 클릭 핸들러
        requireActivity().findViewById<TextView>(R.id.btnDelete)
            .setOnClickListener {
                // 1) DB에서 삭제
                MyApplication.database.postDao().deleteById(postExtra.post.postId)
                // 2) 메인에 삭제 알림
                parentFragmentManager.setFragmentResult(
                    "postDeleted",
                    bundleOf("postId" to postExtra.post.postId)
                )
                // 3) UI 복구: 메인 목록 보여주고 상세 숨기기
                requireActivity().apply {
                    findViewById<FrameLayout>(R.id.frame).visibility = View.VISIBLE
                    findViewById<FrameLayout>(R.id.frame2).visibility = View.GONE
                    findViewById<View>(R.id.clTitleBar).visibility = View.GONE
                }
                // 4) 상세 프래그먼트 pop
                parentFragmentManager.popBackStack()
            }


        val currentUserId = requireContext()
            .getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
            .getInt("userId", -1)

        // 댓글 어댑터 초기화
        commentAdapter = CommentAdapter(
            currentUserId = currentUserId,
            onDeleteClick = { comment -> deleteComment(comment) },
            onUpdateClick = { comment, newContent -> updateComment(comment, newContent) },
            onListChanged = { count ->
                binding.tvComment.text = count.toString()
                sendCommentUpdate(count) // 메인 프래그먼트에 반영
            } // 🔹 여기서 바로 반영
        )

        binding.rvComment.layoutManager = LinearLayoutManager(requireContext())
        binding.rvComment.adapter = commentAdapter

        // 🔹 최신 댓글 개수로 초기화
        val initialCount = MyApplication.database.commentDao()
            .getCommentCountByPost(postExtra.post.postId)
        binding.tvComment.text = initialCount.toString()

        loadComments(postExtra.post.postId)



        // 댓글 등록 버튼
        binding.btnCommentRegister.setOnClickListener {
            val content = binding.etCommentContent.text.toString().trim()
            if (content.isNotEmpty()) {
                val now = System.currentTimeMillis().toString() // 날짜 포맷은 필요 시 변경
                MyApplication.database.commentDao().insert(
                    Comment(
                        postId = postExtra.post.postId,
                        userId = currentUserId,
                        content = content,
                        createdAt = now
                    )
                )
                binding.etCommentContent.text.clear()

                // 🔹 여기서만 호출하면 자동으로 리스트 + 카운트 갱신
                loadComments(postExtra.post.postId) // UI 즉시 갱신

                // 🔹 마지막 위치로 스크롤
                binding.rvComment.post {
                    binding.rvComment.scrollToPosition(commentAdapter.itemCount - 1)
                }
            }
        }

        val likeDao = MyApplication.database.likeDao()

        binding.tvNick.text = postExtra.nickname

        // 좋아요 상태 변수 초기화
        var isLiked = false
        var likeCount = 0
        
        // 기본 텍스트 및 이미지 세팅
        binding.tvComment.text = postExtra.commentCount.toString()
        binding.etContent.setText(postExtra.post.content)
        
        // 현재 좋아요 상태 불러오기 (코루틴으로 개선)
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                isLiked = likeDao.isPostLikedByUser(currentUserId, postExtra.post.postId)
                likeCount = likeDao.getLikeCountByPost(postExtra.post.postId)
                
                withContext(Dispatchers.Main) {
                    binding.tvLike.text = likeCount.toString()
                    binding.imgLike.setImageResource(if (isLiked) R.drawable.like_on else R.drawable.like_off)
                }
            } catch (e: Exception) {
                // 에러 발생 시 기본값으로 설정
                withContext(Dispatchers.Main) {
                    binding.tvLike.text = "0"
                    binding.imgLike.setImageResource(R.drawable.like_off)
                }
            }
        }

        // 게시물 이미지
        if (!postExtra.post.image.isNullOrEmpty()) {
            Glide.with(requireContext())
                .load(Uri.parse(postExtra.post.image))
                .into(binding.imgBoard)
        } else {
            // 이미지가 없을 경우 기본 이미지 표시
            binding.imgBoard.setImageResource(R.drawable.ic_launcher_background)
        }

        // 좋아요 버튼 클릭 이벤트 (코루틴 기반으로 개선)
        binding.imgLike.setOnClickListener {
            // 중복 클릭 방지
            binding.imgLike.isEnabled = false
            
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val newIsLiked: Boolean
                    if (isLiked) {
                        likeDao.deleteLike(currentUserId, postExtra.post.postId)
                        newIsLiked = false
                    } else {
                        likeDao.insert(Like(postId = postExtra.post.postId, userId = currentUserId))
                        newIsLiked = true
                    }
                    
                    // 최신 좋아요 수 가져오기
                    val newLikeCount = likeDao.getLikeCountByPost(postExtra.post.postId)
                    
                    // UI 스레드에서 UI 업데이트
                    withContext(Dispatchers.Main) {
                        isLiked = newIsLiked
                        likeCount = newLikeCount
                        
                        // UI 반영
                        binding.imgLike.setImageResource(if (isLiked) R.drawable.like_on else R.drawable.like_off)
                        binding.tvLike.text = likeCount.toString()
                        
                        // 목록 화면에 결과 전달
                        parentFragmentManager.setFragmentResult(
                            "likeUpdate",
                            Bundle().apply {
                                putInt("postId", postExtra.post.postId)
                                putBoolean("isLiked", isLiked)
                                putInt("likeCount", likeCount)
                            }
                        )
                        
                        // 버튼 다시 활성화
                        binding.imgLike.isEnabled = true
                    }
                } catch (e: Exception) {
                    // 에러 발생 시 UI 스레드에서 처리
                    withContext(Dispatchers.Main) {
                        binding.imgLike.isEnabled = true
                        // 에러 메시지 표시 (선택적)
                        // Toast.makeText(requireContext(), "좋아요 처리 중 오류가 발생했습니다.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        // 상단 프로필 이미지
        if (!postExtra.post.image.isNullOrEmpty()) {
            Glide.with(requireContext())
                .load(postExtra.post.image)
                .into(binding.imgProfile)
        } else {
            binding.imgProfile.setImageResource(R.drawable.ic_launcher_background)
        }

        // 하단 댓글 입력란 프로필 이미지
        if (!postExtra.profileImage.isNullOrEmpty()) {
            Glide.with(requireContext())
                .load(postExtra.profileImage)
                .into(binding.imgProfile2)
        } else {
            binding.imgProfile2.setImageResource(R.drawable.circle_button)
        }

    }

    override fun onDestroyView() {
        super.onDestroyView()
        // 메모리 누수 방지를 위해 반드시 null 처리
        _binding = null
    }

    private fun loadComments(postId: Int) {
        val comments = MyApplication.database.commentDao().getCommentsByPost(postId)
        commentAdapter.setComments(comments)

        // 🔹 여기서 최신 개수 갱신
        val count = MyApplication.database.commentDao().getCommentCountByPost(postId)
        binding.tvComment.text = count.toString()
        sendCommentUpdate(count) // 메인 프래그먼트에 반영
    }

    private fun deleteComment(comment: Comment) {
        MyApplication.database.commentDao().delete(comment)

        // 댓글 목록 다시 로드
        loadComments(comment.postId)
//
//        // 댓글 개수 반영
//        val count = MyApplication.database.commentDao().getCommentCountByPost(comment.postId)
//        binding.tvComment.text = count.toString()
    }

    private fun updateComment(comment: Comment, newContent: String) {
        val updatedAt = System.currentTimeMillis().toString()
        MyApplication.database.commentDao().updateCommentContent(comment.commentId, newContent, updatedAt)
        loadComments(comment.postId)
    }

    // 💡 메인 프래그먼트로 댓글 개수 전달
    private fun sendCommentUpdate(count: Int) {
        parentFragmentManager.setFragmentResult(
            "commentUpdate",
            Bundle().apply {
                putInt("postId", postExtra.post.postId)
                putInt("commentCount", count)
            }
        )
    }
}