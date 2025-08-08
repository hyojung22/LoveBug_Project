package com.example.lovebug_project.mypage

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView // ImageView import 추가
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
    private lateinit var btnBack3: ImageView // btnBack3 변수 선언 (타입은 실제 UI에 맞게)

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_book_mark, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // btnBack3 초기화 및 클릭 리스너 설정
        btnBack3 = view.findViewById(R.id.btnBack3) // fragment_book_mark.xml에 이 ID가 있어야 함
        btnBack3.setOnClickListener {
            // Activity의 OnBackPressedDispatcher를 통해 뒤로가기 이벤트를 전달합니다.
            // MainActivity에서 BookMarkFragment를 .addToBackStack(null)으로 추가했다면,
            // 이 호출은 BookMarkFragment를 닫고 이전 화면으로 돌아가게 합니다.
            activity?.onBackPressedDispatcher?.onBackPressed()
        }

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
            // 예를 들어 로그인 화면으로 이동하거나, 프래그먼트를 닫는 등의 처리를 할 수 있습니다.
            // activity?.onBackPressedDispatcher?.onBackPressed() // 프래그먼트 닫기 예시
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
                userId = post.userId.hashCode(), // UUID.hashCode()는 고유성을 보장하지 않을 수 있습니다. Long 타입 ID를 사용하거나 다른 방법을 고려하세요.
                title = post.title,
                content = post.content,
                image = post.imageUrl,
                createdAt = post.createdAt
            ),
            nickname = post.nickname ?: "알 수 없는 사용자",
            profileImage = post.avatarUrl,
            likeCount = 0, // 실제 좋아요 수로 채워야 합니다.
            commentCount = 0, // 실제 댓글 수로 채워야 합니다.
            isLiked = false, // 실제 좋아요 여부로 채워야 합니다.
            isBookmarked = true // 북마크한 게시글이므로 true로 설정
        )

        // MainActivity에서 frame 전환 및 BoardDetailFragment로 이동
        // requireActivity()가 MainActivity 타입이라는 것을 확신할 수 있을 때만 as를 사용합니다.
        // 좀 더 안전하게 하려면 as? 를 사용하고 null 체크를 할 수 있습니다.
        val mainActivity = requireActivity() as? com.example.lovebug_project.MainActivity

        mainActivity?.let {
            // 제목 변경
            it.findViewById<TextView>(R.id.tvBoardName)?.text = post.title

            // frame -> frame2로 전환
            it.findViewById<FrameLayout>(R.id.frame)?.visibility = View.GONE
            it.findViewById<FrameLayout>(R.id.frame2)?.visibility = View.VISIBLE

            // TitleBar 보이게
            it.findViewById<View>(R.id.clTitleBar)?.visibility = View.VISIBLE

            val bundle = Bundle().apply {
                putSerializable("postExtra", postExtra)
            }
            val detailFragment = BoardDetailFragment().apply {
                arguments = bundle
            }

            it.supportFragmentManager.beginTransaction()
                .replace(R.id.frame2, detailFragment)
                .addToBackStack(null) // BoardDetailFragment에서 뒤로가기 시 BookMarkFragment로 돌아오도록 설정
                .commit()
        }
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
