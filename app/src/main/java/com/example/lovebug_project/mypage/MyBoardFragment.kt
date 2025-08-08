package com.example.lovebug_project.mypage

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.lovebug_project.R
import com.example.lovebug_project.board.BoardDetailFragment
import com.example.lovebug_project.data.db.MyApplication
import com.example.lovebug_project.data.db.entity.PostWithExtras
import com.example.lovebug_project.data.supabase.models.Post
import com.example.lovebug_project.data.supabase.models.PostWithProfile
import com.example.lovebug_project.utils.AuthHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 나의 게시물을 표시하는 프래그먼트
 */
class MyBoardFragment : Fragment() {
    
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: MyBoardAdapter
    private var userPosts: List<PostWithProfile> = emptyList()
    
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_my_board, container, false)
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupRecyclerView(view)
        loadUserPosts()
    }
    
    private fun setupRecyclerView(view: View) {
        recyclerView = view.findViewById(R.id.recyclerView)
        
        adapter = MyBoardAdapter(
            onItemClick = { post ->
                navigateToPostDetail(post)
            },
            coroutineScope = lifecycleScope
        )
        
        recyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@MyBoardFragment.adapter
        }
    }
    
    private fun loadUserPosts() {
        val currentUserUuid = AuthHelper.getSupabaseUserId(requireContext())
        
        if (currentUserUuid == null) {
            Toast.makeText(requireContext(), "로그인이 필요합니다.", Toast.LENGTH_SHORT).show()
            return
        }
        
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // 사용자의 게시글 조회
                val postsResult = MyApplication.repositoryManager.postRepository.getPostsByUserId(currentUserUuid)
                
                postsResult.fold(
                    onSuccess = { posts ->
                        // Post를 PostWithProfile로 변환하면서 사용자 정보 추가
                        val postsWithProfile = posts.map { post ->
                            convertToPostWithProfile(post)
                        }
                        
                        withContext(Dispatchers.Main) {
                            userPosts = postsWithProfile
                            adapter.setPosts(postsWithProfile)
                            
                            if (postsWithProfile.isEmpty()) {
                                Toast.makeText(
                                    requireContext(), 
                                    "작성한 게시물이 없습니다.", 
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    },
                    onFailure = { exception ->
                        withContext(Dispatchers.Main) {
                            Toast.makeText(
                                requireContext(), 
                                "게시물을 불러오는데 실패했습니다: ${exception.message}", 
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                )
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        requireContext(), 
                        "오류가 발생했습니다: ${e.message}", 
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }
    
    private suspend fun convertToPostWithProfile(post: Post): PostWithProfile {
        // 현재 사용자의 프로필 정보 가져오기
        val userProfile = MyApplication.repositoryManager.userRepository.getUserProfile(post.userId)
        
        return PostWithProfile(
            postId = post.postId,
            userId = post.userId,
            title = post.title,
            content = post.content,
            imageUrl = post.image,
            imagePath = null,
            createdAt = post.createdAt,
            updatedAt = post.updatedAt,
            nickname = userProfile?.nickname ?: "알 수 없는 사용자",
            avatarUrl = userProfile?.avatarUrl
        )
    }
    
    private fun navigateToPostDetail(post: PostWithProfile) {
        // PostWithProfile을 PostWithExtras로 변환
        val postExtra = PostWithExtras(
            post = com.example.lovebug_project.data.db.entity.Post(
                postId = post.postId,
                userId = post.userId.hashCode(),
                title = post.title,
                content = post.content,
                image = post.imageUrl,
                createdAt = post.createdAt
            ),
            nickname = post.nickname ?: "알 수 없는 사용자",
            profileImage = post.avatarUrl,
            likeCount = 0,
            commentCount = 0,
            isLiked = false,
            isBookmarked = false
        )

        // MainActivity에서 frame 전환 및 BoardDetailFragment로 이동
        val mainActivity = requireActivity() as com.example.lovebug_project.MainActivity

        // 제목 변경
        mainActivity.findViewById<TextView>(R.id.tvBoardName)?.text = post.title

        // frame -> frame2로 전환
        mainActivity.findViewById<FrameLayout>(R.id.frame)?.visibility = View.GONE
        mainActivity.findViewById<FrameLayout>(R.id.frame2)?.visibility = View.VISIBLE

        // TitleBar 보이게
        mainActivity.findViewById<View>(R.id.clTitleBar)?.visibility = View.VISIBLE

        val bundle = Bundle().apply {
            putSerializable("postExtra", postExtra)
        }
        val detailFragment = BoardDetailFragment().apply {
            arguments = bundle
        }

        mainActivity.supportFragmentManager.beginTransaction()
            .replace(R.id.frame2, detailFragment)
            .addToBackStack(null)
            .commit()
    }
    
    companion object {
        /**
         * MyBoardFragment의 새 인스턴스 생성
         */
        @JvmStatic
        fun newInstance() = MyBoardFragment()
    }
}