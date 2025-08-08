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
import com.example.lovebug_project.data.supabase.models.Comment
import com.example.lovebug_project.data.db.entity.Like
import com.example.lovebug_project.data.db.entity.Post
import com.example.lovebug_project.data.db.entity.PostWithExtras
import com.example.lovebug_project.utils.AuthHelper
import com.example.lovebug_project.databinding.FragmentBoardDetailBinding
import com.example.lovebug_project.databinding.FragmentBoardMainBinding
import com.example.lovebug_project.utils.loadProfileImage

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
                // 삭제 확인 다이얼로그 표시
                android.app.AlertDialog.Builder(requireContext())
                    .setTitle("게시글 삭제")
                    .setMessage("정말로 이 게시글을 삭제하시겠습니까?")
                    .setPositiveButton("삭제") { _, _ ->
                        // 1) Supabase에서 게시글 삭제
                        lifecycleScope.launch(Dispatchers.IO) {
                            try {
                                val result = MyApplication.repositoryManager.postRepository.deletePost(postExtra.post.postId)
                                
                                withContext(Dispatchers.Main) {
                                    result.fold(
                                        onSuccess = {
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
                                            
                                            // 성공 메시지 표시
                                            android.widget.Toast.makeText(
                                                requireContext(),
                                                "게시글이 삭제되었습니다.",
                                                android.widget.Toast.LENGTH_SHORT
                                            ).show()
                                        },
                                        onFailure = { exception ->
                                            // 삭제 실패 처리
                                            android.widget.Toast.makeText(
                                                requireContext(),
                                                "게시글 삭제 실패: ${exception.message}",
                                                android.widget.Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    )
                                }
                            } catch (e: Exception) {
                                withContext(Dispatchers.Main) {
                                    android.widget.Toast.makeText(
                                        requireContext(),
                                        "게시글 삭제 중 오류 발생: ${e.message}",
                                        android.widget.Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        }
                    }
                    .setNegativeButton("취소", null)
                    .show()
            }


        // Supabase 사용자 UUID 가져오기
        val currentUserUuid = AuthHelper.getSupabaseUserId(requireContext())

        // 댓글 어댑터 초기화
        commentAdapter = CommentAdapter(
            currentUserId = currentUserUuid,
            userRepository = MyApplication.repositoryManager.userRepository,
            coroutineScope = lifecycleScope,
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
        // TODO: Implement Supabase comment count
        val initialCount = 0 // MyApplication.postRepository.getCommentCount(postExtra.post.postId)
        binding.tvComment.text = initialCount.toString()

        loadComments(postExtra.post.postId)



        // 댓글 등록 버튼
        binding.btnCommentRegister.setOnClickListener {
            val content = binding.etCommentContent.text.toString().trim()
            if (content.isNotEmpty()) {
                lifecycleScope.launch(Dispatchers.IO) {
                    try {
                        // Supabase에서 현재 사용자 UUID 가져오기
                        val currentUserUuid = AuthHelper.getSupabaseUserId(requireContext())
                        if (currentUserUuid != null) {
                            val comment = Comment(
                                postId = postExtra.post.postId,
                                userId = currentUserUuid,
                                content = content
                            )
                            
                            val result = MyApplication.repositoryManager.postRepository.createComment(comment)
                            
                            withContext(Dispatchers.Main) {
                                result.fold(
                                    onSuccess = {
                                        binding.etCommentContent.text.clear()
                                        loadComments(postExtra.post.postId) // UI 즉시 갱신
                                        
                                        // 🔹 마지막 위치로 스크롤
                                        binding.rvComment.post {
                                            binding.rvComment.scrollToPosition(commentAdapter.itemCount - 1)
                                        }
                                    },
                                    onFailure = { exception ->
                                        // 에러 처리 - Toast 메시지 표시
                                        android.widget.Toast.makeText(
                                            requireContext(), 
                                            "댓글 등록 실패: ${exception.message}", 
                                            android.widget.Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                )
                            }
                        } else {
                            withContext(Dispatchers.Main) {
                                android.widget.Toast.makeText(
                                    requireContext(), 
                                    "로그인이 필요합니다.", 
                                    android.widget.Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            android.widget.Toast.makeText(
                                requireContext(), 
                                "댓글 등록 중 오류 발생: ${e.message}", 
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
            }
        }

        binding.tvNick.text = postExtra.nickname

        // 좋아요와 북마크 상태 변수 초기화
        var isLiked = false
        var likeCount = 0
        var isBookmarked = false
        
        // 기본 텍스트 및 이미지 세팅
        binding.tvComment.text = postExtra.commentCount.toString()
        binding.etContent.setText(postExtra.post.content)
        
        // 현재 사용자의 게시글인지 확인
        val sharedPref = requireContext().getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
        val currentUserId = sharedPref.getInt("userId", -1)
        val isMyPost = postExtra.post.userId == currentUserId
        
        // 북마크 버튼 가시성 설정 (내 게시글이면 숨김)
        binding.imgBookmark.visibility = if (isMyPost) View.GONE else View.VISIBLE
        
        // Supabase 좋아요 및 북마크 기능 구현
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                if (currentUserUuid != null) {
                    // 좋아요 상태 조회
                    val isLikedResult = MyApplication.repositoryManager.postRepository.isPostLikedByUser(
                        postExtra.post.postId, currentUserUuid
                    )
                    val likeCountResult = MyApplication.repositoryManager.postRepository.getLikeCountByPost(
                        postExtra.post.postId
                    )
                    
                    // 북마크 상태 조회 (내 게시글이 아닐 때만)
                    val isBookmarkedResult = if (!isMyPost) {
                        MyApplication.repositoryManager.postRepository.isPostBookmarkedByUser(
                            postExtra.post.postId, currentUserUuid
                        )
                    } else null
                    
                    withContext(Dispatchers.Main) {
                        // 좋아요 상태 처리
                        isLikedResult.fold(
                            onSuccess = { liked ->
                                isLiked = liked
                                likeCountResult.fold(
                                    onSuccess = { count ->
                                        likeCount = count
                                        binding.tvLike.text = likeCount.toString()
                                        binding.imgLike.setImageResource(
                                            if (isLiked) R.drawable.like_on else R.drawable.like_off
                                        )
                                    },
                                    onFailure = {
                                        // 개수 조회 실패시 기본값
                                        likeCount = 0
                                        binding.tvLike.text = "0"
                                        binding.imgLike.setImageResource(
                                            if (isLiked) R.drawable.like_on else R.drawable.like_off
                                        )
                                    }
                                )
                            },
                            onFailure = {
                                // 에러 발생 시 기본값으로 설정
                                binding.tvLike.text = "0"
                                binding.imgLike.setImageResource(R.drawable.like_off)
                            }
                        )
                        
                        // 북마크 상태 처리
                        isBookmarkedResult?.fold(
                            onSuccess = { bookmarked ->
                                isBookmarked = bookmarked
                                binding.imgBookmark.setImageResource(
                                    if (isBookmarked) R.drawable.bookmark_on else R.drawable.bookmark
                                )
                            },
                            onFailure = {
                                // 에러 발생 시 기본값
                                binding.imgBookmark.setImageResource(R.drawable.bookmark)
                            }
                        ) ?: run {
                            // 내 게시글인 경우
                            binding.imgBookmark.setImageResource(R.drawable.bookmark)
                        }
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        binding.tvLike.text = "0"
                        binding.imgLike.setImageResource(R.drawable.like_off)
                        binding.imgBookmark.setImageResource(R.drawable.bookmark)
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.tvLike.text = "0"
                    binding.imgLike.setImageResource(R.drawable.like_off)
                    binding.imgBookmark.setImageResource(R.drawable.bookmark)
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
            binding.imgBoard.setImageResource(R.drawable.app_logo)
        }

        // Supabase 좋아요 클릭 핸들러
        binding.imgLike.setOnClickListener {
            if (currentUserUuid == null) {
                android.widget.Toast.makeText(requireContext(), "로그인이 필요합니다.", android.widget.Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            // 중복 클릭 방지
            binding.imgLike.isEnabled = false
            
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val result = if (isLiked) {
                        // 좋아요 취소
                        MyApplication.repositoryManager.postRepository.removeLike(postExtra.post.postId, currentUserUuid)
                    } else {
                        // 좋아요 추가
                        MyApplication.repositoryManager.postRepository.addLike(postExtra.post.postId, currentUserUuid)
                    }
                    
                    result.fold(
                        onSuccess = {
                            // 성공시 최신 좋아요 개수 조회
                            val countResult = MyApplication.repositoryManager.postRepository.getLikeCountByPost(postExtra.post.postId)
                            
                            withContext(Dispatchers.Main) {
                                countResult.fold(
                                    onSuccess = { newLikeCount ->
                                        // UI 업데이트
                                        isLiked = !isLiked
                                        likeCount = newLikeCount
                                        
                                        binding.imgLike.setImageResource(
                                            if (isLiked) R.drawable.like_on else R.drawable.like_off
                                        )
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
                                    },
                                    onFailure = {
                                        // 개수 조회 실패시 UI만 토글
                                        isLiked = !isLiked
                                        likeCount = if (isLiked) likeCount + 1 else maxOf(0, likeCount - 1)
                                        
                                        binding.imgLike.setImageResource(
                                            if (isLiked) R.drawable.like_on else R.drawable.like_off
                                        )
                                        binding.tvLike.text = likeCount.toString()
                                    }
                                )
                                
                                // 버튼 다시 활성화
                                binding.imgLike.isEnabled = true
                            }
                        },
                        onFailure = { exception ->
                            withContext(Dispatchers.Main) {
                                binding.imgLike.isEnabled = true
                                android.widget.Toast.makeText(
                                    requireContext(), 
                                    "좋아요 처리 실패: ${exception.message}", 
                                    android.widget.Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    )
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        binding.imgLike.isEnabled = true
                        android.widget.Toast.makeText(
                            requireContext(), 
                            "오류가 발생했습니다: ${e.message}", 
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        }

        // Supabase 북마크 클릭 핸들러
        binding.imgBookmark.setOnClickListener {
            if (currentUserUuid == null) {
                android.widget.Toast.makeText(requireContext(), "로그인이 필요합니다.", android.widget.Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            // 내 게시글인지 다시 한 번 확인
            if (isMyPost) {
                android.widget.Toast.makeText(requireContext(), "내가 작성한 글은 북마크할 수 없습니다.", android.widget.Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            // 중복 클릭 방지
            binding.imgBookmark.isEnabled = false
            
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val result = if (isBookmarked) {
                        // 북마크 취소
                        MyApplication.repositoryManager.postRepository.removeBookmark(postExtra.post.postId, currentUserUuid)
                    } else {
                        // 북마크 추가
                        MyApplication.repositoryManager.postRepository.addBookmark(postExtra.post.postId, currentUserUuid)
                    }
                    
                    result.fold(
                        onSuccess = {
                            withContext(Dispatchers.Main) {
                                // UI 업데이트
                                isBookmarked = !isBookmarked
                                binding.imgBookmark.setImageResource(
                                    if (isBookmarked) R.drawable.bookmark_on else R.drawable.bookmark
                                )
                                
                                // 목록 화면에 결과 전달
                                parentFragmentManager.setFragmentResult(
                                    "bookmarkUpdate",
                                    Bundle().apply {
                                        putInt("postId", postExtra.post.postId)
                                        putBoolean("isBookmarked", isBookmarked)
                                    }
                                )
                                
                                // 버튼 다시 활성화
                                binding.imgBookmark.isEnabled = true
                                
                                val message = if (isBookmarked) "북마크에 추가되었습니다." else "북마크에서 제거되었습니다."
                                android.widget.Toast.makeText(requireContext(), message, android.widget.Toast.LENGTH_SHORT).show()
                            }
                        },
                        onFailure = { exception ->
                            withContext(Dispatchers.Main) {
                                binding.imgBookmark.isEnabled = true
                                android.widget.Toast.makeText(
                                    requireContext(), 
                                    "북마크 처리 실패: ${exception.message}", 
                                    android.widget.Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    )
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        binding.imgBookmark.isEnabled = true
                        android.widget.Toast.makeText(
                            requireContext(), 
                            "오류가 발생했습니다: ${e.message}", 
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        }

        // 상단 프로필 이미지
        binding.imgProfile.loadProfileImage(postExtra.profileImage)

        // 하단 댓글 입력란 프로필 이미지
        binding.imgProfile2.loadProfileImage(postExtra.profileImage)

    }

    override fun onDestroyView() {
        super.onDestroyView()
        // 메모리 누수 방지를 위해 반드시 null 처리
        _binding = null
    }

    private fun loadComments(postId: Int) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // Supabase에서 댓글 목록 가져오기
                val commentsResult = MyApplication.repositoryManager.postRepository.getCommentsByPostId(postId)
                val countResult = MyApplication.repositoryManager.postRepository.getCommentCountByPost(postId)
                
                withContext(Dispatchers.Main) {
                    commentsResult.fold(
                        onSuccess = { comments ->
                            commentAdapter.setComments(comments)
                            
                            // 댓글 개수 업데이트
                            countResult.fold(
                                onSuccess = { count ->
                                    binding.tvComment.text = count.toString()
                                    sendCommentUpdate(count)
                                },
                                onFailure = {
                                    // 개수 조회 실패시 리스트 크기로 대체
                                    binding.tvComment.text = comments.size.toString()
                                    sendCommentUpdate(comments.size)
                                }
                            )
                        },
                        onFailure = { exception ->
                            // 에러 처리 - 빈 리스트로 설정
                            commentAdapter.setComments(emptyList())
                            binding.tvComment.text = "0"
                            sendCommentUpdate(0)
                            
                            android.widget.Toast.makeText(
                                requireContext(),
                                "댓글을 불러올 수 없습니다: ${exception.message}",
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                        }
                    )
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    // 예외 처리
                    commentAdapter.setComments(emptyList())
                    binding.tvComment.text = "0"
                    sendCommentUpdate(0)
                    
                    android.widget.Toast.makeText(
                        requireContext(),
                        "댓글 로딩 중 오류 발생: ${e.message}",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun deleteComment(comment: Comment) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val result = MyApplication.repositoryManager.postRepository.deleteComment(comment.commentId)
                
                withContext(Dispatchers.Main) {
                    result.fold(
                        onSuccess = {
                            // 댓글 목록 다시 로드
                            loadComments(comment.postId)
                            android.widget.Toast.makeText(
                                requireContext(),
                                "댓글이 삭제되었습니다.",
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                        },
                        onFailure = { exception ->
                            android.widget.Toast.makeText(
                                requireContext(),
                                "댓글 삭제 실패: ${exception.message}",
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                        }
                    )
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    android.widget.Toast.makeText(
                        requireContext(),
                        "댓글 삭제 중 오류 발생: ${e.message}",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun updateComment(comment: Comment, newContent: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val result = MyApplication.repositoryManager.postRepository.updateComment(comment.commentId, newContent)
                
                withContext(Dispatchers.Main) {
                    result.fold(
                        onSuccess = {
                            // 댓글 목록 다시 로드
                            loadComments(comment.postId)
                            android.widget.Toast.makeText(
                                requireContext(),
                                "댓글이 수정되었습니다.",
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                        },
                        onFailure = { exception ->
                            android.widget.Toast.makeText(
                                requireContext(),
                                "댓글 수정 실패: ${exception.message}",
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                        }
                    )
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    android.widget.Toast.makeText(
                        requireContext(),
                        "댓글 수정 중 오류 발생: ${e.message}",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
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