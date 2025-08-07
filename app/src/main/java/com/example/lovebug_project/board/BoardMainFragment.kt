package com.example.lovebug_project.board

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.FrameLayout
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.lovebug_project.MainActivity
import com.example.lovebug_project.R
import com.example.lovebug_project.data.db.MyApplication
import com.example.lovebug_project.data.db.entity.PostWithExtras
import com.example.lovebug_project.databinding.FragmentBoardMainBinding
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class BoardMainFragment : Fragment() {

    // binding 인스턴스를 nullable로 선언
    private var _binding: FragmentBoardMainBinding? = null
    // 안전하게 binding을 꺼내 쓰는 프로퍼티
    private val binding get() = _binding!!

    // 전체 게시글 원본 리스트
    private val fullPostList = mutableListOf<PostWithExtras>()

    // 리사이클러뷰 어댑터
    private lateinit var boardAdapter : BoardAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // inflate 대신 binding.inflate 사용
        _binding = FragmentBoardMainBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // 예: binding.spinnerSort, binding.rvBoard 등 바로 접근 가능

        // 🗑️ 삭제 리스너: 상세에서 전달된 삭제 이벤트 수신
        parentFragmentManager.setFragmentResultListener("postDeleted", viewLifecycleOwner) { _, bundle ->
            val deletedId = bundle.getInt("postId")
            fullPostList.removeAll { it.post.postId == deletedId }
            boardAdapter.setPosts(fullPostList)
        }

        // ✅ 좋아요 변경 결과 수신
        parentFragmentManager.setFragmentResultListener("likeUpdate", viewLifecycleOwner) { _, bundle ->
            val postId = bundle.getInt("postId")
            val isLiked = bundle.getBoolean("isLiked")

            // 해당 게시물 찾아서 좋아요 수 업데이트
            val index = fullPostList.indexOfFirst { it.post.postId == postId }
            if (index != -1) {
                val post = fullPostList[index]
                val newLikeCount = if (isLiked) post.likeCount + 1 else post.likeCount - 1
                fullPostList[index] = post.copy(likeCount = newLikeCount)

                // UI 새로고침
                boardAdapter.notifyItemChanged(index)
            }
        }

        // 💡 댓글 업데이트 수신
        parentFragmentManager.setFragmentResultListener("commentUpdate", viewLifecycleOwner) { _, bundle ->
            val postId = bundle.getInt("postId")
            val newCount = bundle.getInt("commentCount")
            updateCommentCount(postId, newCount)
        }

        // 어댑터 관련 변수
        boardAdapter = BoardAdapter { selectedPost ->
            val mainActivity = requireActivity() as MainActivity

            // 제목 변경
            mainActivity.findViewById<TextView>(R.id.tvBoardName).text = selectedPost.post.title

            // frame -> frame2로 전환
            mainActivity.findViewById<FrameLayout>(R.id.frame).visibility = View.GONE
            mainActivity.findViewById<FrameLayout>(R.id.frame2).visibility = View.VISIBLE

            // TitleBar 보이게
            mainActivity.findViewById<View>(R.id.clTitleBar).visibility = View.VISIBLE

            val bundle = Bundle().apply {
                putSerializable("postExtra", selectedPost)
            }
            val detailFragment = BoardDetailFragment().apply {
                arguments = bundle
            }

            mainActivity.supportFragmentManager.beginTransaction()
                .replace(R.id.frame2, detailFragment) // replace 위치는 프로젝트 구조에 따라 다름
                .addToBackStack(null) // 뒤로가기 가능하게
                .commit()
        }
        binding.rvBoard.layoutManager = LinearLayoutManager(requireContext())
        binding.rvBoard.adapter = boardAdapter

        // 글 작성 버튼 클릭 시 게시물 작성 페이지로 이동
        binding.btnWrite.circleButton.setOnClickListener {
            val intent = Intent(requireActivity(), BoardWriteActivity::class.java)
            startActivity(intent)
        }

        // 검색창 관련 변수 (include에 있는)
        val etSearch = binding.inCludeBoardSearchLayout.etSearch
        val btnSearch = binding.inCludeBoardSearchLayout.btnSearch
        val checkTitle = binding.inCludeBoardSearchLayout.checkTitle
        val checkContent = binding.inCludeBoardSearchLayout.checkContent

        loadPostFromDB()

        // 검색 버튼 클릭 시 필터링 처리
        btnSearch.setOnClickListener {
            val keyword = etSearch.text.toString().trim()
            val filterByTitle = checkTitle.isChecked
            val filterByContent = checkContent.isChecked

            val filteredList = fullPostList.filter { postWithExtras ->
                val title = postWithExtras.post.title
                val content = postWithExtras.post.content

                when {
                    // 제목 or 내용 체크된 경우
                    filterByTitle || filterByContent -> {
                        val matchTitle = filterByTitle && title.contains(keyword, ignoreCase = true)
                        val matchContent = filterByContent && content.contains(keyword, ignoreCase = true)
                        matchTitle || matchContent
                    }

                    // 체크박스 둘 다 선택 안된 경우 → 제목만 필터링
                    else -> {
                        title.contains(keyword, ignoreCase = true)
                    }
                }
            }

            // 결과 표시
            if (filteredList.isEmpty()) {
                boardAdapter.setPosts(emptyList())
                binding.rvBoard.visibility = View.GONE
                binding.tvNoBoard.visibility = View.VISIBLE
            } else {
                boardAdapter.setPosts(filteredList)
                binding.rvBoard.visibility = View.VISIBLE
                binding.tvNoBoard.visibility = View.GONE
            }
        }

        // 최신순/좋아요순 정렬
        binding.spinnerSort.setSelection(0) // 기본값: 최신순
        binding.spinnerSort.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                when (position) {
                    0 -> {
                        // 최신순
                        fullPostList.sortByDescending { it.post.postId } // 최신이 위로 오도록
                    }
                    1 -> {
                        // 좋아요순
                        fullPostList.sortByDescending {
                            MyApplication.database.likeDao().getLikeCountByPost(it.post.postId)
                        }
                    }
                }

                boardAdapter.setPosts(fullPostList)

                if (fullPostList.isEmpty()) {
                    binding.rvBoard.visibility = View.GONE
                    binding.tvNoBoard.visibility = View.VISIBLE
                } else {
                    binding.rvBoard.visibility = View.VISIBLE
                    binding.tvNoBoard.visibility = View.GONE
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}

        }

    }

    // 화면 복귀 시 목록 갱신
    override fun onResume() {
        super.onResume()
        loadPostFromDB()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // 메모리 누수 방지를 위해 반드시 null 처리
        _binding = null
    }

    private fun loadPostFromDB() {
        lifecycleScope.launch {
            try {
                val postDao = MyApplication.database.postDao()
                val userDao = MyApplication.database.userDao()
                val likeDao = MyApplication.database.likeDao()
                val commentDao = MyApplication.database.commentDao()
                val bookmarkDao = MyApplication.database.bookmarkDao()

                // IO 스레드에서 데이터베이스 작업 수행
                val postWithExtrasList = withContext(Dispatchers.IO) {
                    val posts = postDao.getAllPosts()
                    val currentUserId = getLoggedInUserId() // 예시로 현재 로그인된 유저 ID

                    posts.map { post ->
                        val nickname = userDao.getUserById(post.userId)?.nickname ?: "알 수 없음"
                        val likeCount = likeDao.getLikeCountByPost(post.postId)
                        val commentCount = commentDao.getCommentCountByPost(post.postId)
                        // 내가 쓴 글이 아니라면 북마크 여부 확인
                        val isBookmarked = if (post.userId != currentUserId) {
                            bookmarkDao.isPostBookmarkedByUser(currentUserId, post.postId)
                        } else {
                            false
                        }

                        PostWithExtras(
                            post = post,
                            nickname = nickname,
                            profileImage = null,
                            likeCount = likeCount,
                            commentCount = commentCount,
                            isBookmarked = isBookmarked
                        )
                    }
                }

                // UI 업데이트는 메인 스레드에서 수행
                boardAdapter.setPosts(postWithExtrasList)
                fullPostList.clear()
                fullPostList.addAll(postWithExtrasList) // 필터링용 원본 유지

                binding.rvBoard.visibility = if (postWithExtrasList.isEmpty()) View.GONE else View.VISIBLE
                binding.tvNoBoard.visibility = if (postWithExtrasList.isEmpty()) View.VISIBLE else View.GONE

            } catch (e: Exception) {
                e.printStackTrace()
                // 에러 처리 (필요시 사용자에게 알림)
            }
        }
    }

    private fun getLoggedInUserId(): Int {
        val sharedPref = requireContext().getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
        return sharedPref.getInt("userId", -1)
    }

    // 💡 댓글 수 갱신
    private fun updateCommentCount(postId: Int, newCount: Int) {
        val index = fullPostList.indexOfFirst { it.post.postId == postId }
        if (index != -1) {
            val post = fullPostList[index]
            fullPostList[index] = post.copy(commentCount = newCount)
            boardAdapter.notifyItemChanged(index)
        }
    }
}