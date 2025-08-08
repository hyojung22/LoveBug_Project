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
import com.example.lovebug_project.data.supabase.models.PostWithProfile
import com.example.lovebug_project.utils.AuthHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 북마크한 게시물을 표시하는 프래그먼트
 */
class BookMarkFragment : Fragment() {
    
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: MyBoardAdapter
    private var bookmarkedPosts: List<PostWithProfile> = emptyList()
    
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_book_mark, container, false)
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupRecyclerView(view)
        loadBookmarkedPosts()
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
            adapter = this@BookMarkFragment.adapter
        }
    }
    
    private fun loadBookmarkedPosts() {
        val currentUserUuid = AuthHelper.getSupabaseUserId(requireContext())
        
        if (currentUserUuid == null) {
            Toast.makeText(requireContext(), "로그인이 필요합니다.", Toast.LENGTH_SHORT).show()
            return
        }
        
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // 사용자의 북마크한 게시글 조회
                val bookmarksResult = MyApplication.repositoryManager.postRepository.getBookmarkedPostsByUser(currentUserUuid)
                
                bookmarksResult.fold(
                    onSuccess = { posts ->
                        withContext(Dispatchers.Main) {
                            bookmarkedPosts = posts
                            adapter.setPosts(posts)
                            
                            if (posts.isEmpty()) {
                                Toast.makeText(
                                    requireContext(), 
                                    "북마크한 게시물이 없습니다.", 
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    },
                    onFailure = { exception ->
                        withContext(Dispatchers.Main) {
                            Toast.makeText(
                                requireContext(), 
                                "북마크 목록을 불러오는데 실패했습니다: ${exception.message}", 
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
            isBookmarked = true // 북마크한 게시글이므로 true로 설정
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
    
    /**
     * 북마크 목록 새로고침
     */
    fun refreshBookmarks() {
        loadBookmarkedPosts()
    }
    
    companion object {
        /**
         * BookMarkFragment의 새 인스턴스 생성
         */
        @JvmStatic
        fun newInstance() = BookMarkFragment()
    }
}