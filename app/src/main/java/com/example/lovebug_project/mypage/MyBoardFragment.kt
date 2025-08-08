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
import com.example.lovebug_project.data.supabase.models.Post
import com.example.lovebug_project.data.supabase.models.PostWithProfile
import com.example.lovebug_project.utils.AuthHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MyBoardFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: MyBoardAdapter
    private var userPosts: List<PostWithProfile> = emptyList()
    private lateinit var btnBack2: ImageView

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_my_board, container, false)
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        btnBack2 = view.findViewById(R.id.btnBack2)
        btnBack2.setOnClickListener {
            // Activity의 OnBackPressedDispatcher를 통해 뒤로가기 이벤트를 전달합니다.
            // 이 프래그먼트가 addToBackStack()으로 추가되었다면,
            // 이전 프래그먼트나 Activity 상태로 돌아갑니다.
            activity?.onBackPressedDispatcher?.onBackPressed()
        }

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
            // 예를 들어 로그인 화면으로 이동하거나, 프래그먼트를 닫는 등의 처리를 할 수 있습니다.
            // activity?.onBackPressedDispatcher?.onBackPressed() // 프래그먼트 닫기 예시
            return
        }

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val postsResult = MyApplication.repositoryManager.postRepository.getPostsByUserId(currentUserUuid)
                postsResult.fold(
                    onSuccess = { posts ->
                        val postsWithProfile = posts.map { post ->
                            convertToPostWithProfile(post)
                        }
                        withContext(Dispatchers.Main) {
                            userPosts = postsWithProfile
                            adapter.setPosts(postsWithProfile)
                            if (postsWithProfile.isEmpty()) {
                                Toast.makeText(requireContext(), "작성한 게시물이 없습니다.", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    onFailure = { exception ->
                        withContext(Dispatchers.Main) {
                            Toast.makeText(requireContext(), "게시물을 불러오는데 실패했습니다: ${exception.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "오류가 발생했습니다: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private suspend fun convertToPostWithProfile(post: Post): PostWithProfile {
        val userProfile = MyApplication.repositoryManager.userRepository.getUserProfile(post.userId)
        return PostWithProfile(
            postId = post.postId,
            userId = post.userId,
            title = post.title,
            content = post.content,
            imageUrl = post.image,
            imagePath = null, // 필요에 따라 설정
            createdAt = post.createdAt,
            updatedAt = post.updatedAt,
            nickname = userProfile?.nickname ?: "알 수 없는 사용자",
            avatarUrl = userProfile?.avatarUrl
        )
    }

    private fun navigateToPostDetail(post: PostWithProfile) {
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
            likeCount = 0, // 실제 값으로 채워야 함
            commentCount = 0, // 실제 값으로 채워야 함
            isLiked = false, // 실제 값으로 채워야 함
            isBookmarked = false // 실제 값으로 채워야 함
        )

        val mainActivity = requireActivity() as? com.example.lovebug_project.MainActivity
        mainActivity?.let {
            it.findViewById<TextView>(R.id.tvBoardName)?.text = post.title
            it.findViewById<FrameLayout>(R.id.frame)?.visibility = View.GONE
            it.findViewById<FrameLayout>(R.id.frame2)?.visibility = View.VISIBLE
            it.findViewById<View>(R.id.clTitleBar)?.visibility = View.VISIBLE

            val bundle = Bundle().apply {
                putSerializable("postExtra", postExtra)
            }
            val detailFragment = BoardDetailFragment().apply {
                arguments = bundle
            }

            it.supportFragmentManager.beginTransaction()
                .replace(R.id.frame2, detailFragment) // frame2에 BoardDetailFragment를 표시
                .addToBackStack(null) // BoardDetailFragment에서 뒤로가기 시 MyBoardFragment로 돌아오도록 설정
                .commit()
        }
    }

    companion object {
        @JvmStatic
        fun newInstance() = MyBoardFragment()
    }
}
