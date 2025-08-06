package com.example.lovebug_project.board

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
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

        // 현재 좋아요 상태 불러오기
        var isLiked = likeDao.isPostLikedByUser(currentUserId, postExtra.post.postId)
        var likeCount = likeDao.getLikeCountByPost(postExtra.post.postId)

        binding.tvNick.text = postExtra.nickname
        binding.tvLike.text = likeCount.toString()
        binding.tvComment.text = postExtra.commentCount.toString()
        binding.etContent.setText(postExtra.post.content)
        binding.imgLike.setImageResource(if (isLiked) R.drawable.like_on else R.drawable.like_off)

        // 썸네일 이미지
        if (!postExtra.post.image.isNullOrEmpty()) {
            Glide.with(requireContext())
                .load(Uri.parse(postExtra.post.image))
                .into(binding.imgBoard)
        } else {
            // 이미지가 없을 경우 기본 이미지 표시
            binding.imgBoard.setImageResource(R.drawable.ic_launcher_background)
        }

        // 좋아요 버튼 클릭 이벤트
        binding.imgLike.setOnClickListener {
            if (isLiked) {
                likeDao.deleteLike(currentUserId, postExtra.post.postId)
                likeCount--
            } else {
                likeDao.insert(Like(postId = postExtra.post.postId, userId = currentUserId))
                likeCount++
            }
            isLiked = !isLiked

            // UI 즉시 반영
            binding.imgLike.setImageResource(if (isLiked) R.drawable.like_on else R.drawable.like_off)
            binding.tvLike.text = likeCount.toString()

            // ✅ 목록 화면에 결과 전달
            parentFragmentManager.setFragmentResult(
                "likeUpdate",
                Bundle().apply {
                    putInt("postId", postExtra.post.postId)
                    putBoolean("isLiked", isLiked)
                }
            )
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