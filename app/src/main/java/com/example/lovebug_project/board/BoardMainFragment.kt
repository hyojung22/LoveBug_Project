package com.example.lovebug_project.board

import android.content.Intent
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import com.example.lovebug_project.data.db.MyApplication
import com.example.lovebug_project.data.db.entity.Post
import com.example.lovebug_project.databinding.FragmentBoardMainBinding

class BoardMainFragment : Fragment() {

    // binding 인스턴스를 nullable로 선언
    private var _binding: FragmentBoardMainBinding? = null
    // 안전하게 binding을 꺼내 쓰는 프로퍼티
    private val binding get() = _binding!!

    // 전체 게시글 원본 리스트
    private val fullPostList = mutableListOf<Post>()

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

        // 어댑터 관련 변수
        boardAdapter = BoardAdapter()
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

            // 필터된 리스트 생성
            val filteredList = fullPostList.filter { post ->
                (filterByTitle && post.title.contains(keyword, ignoreCase = true)) ||
                (filterByContent && post.content.contains(keyword, ignoreCase = true))
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
                        fullPostList.sortByDescending { it.postId } // 최신이 위로 오도록
                    }
                    1 -> {
                        // 좋아요순
                        fullPostList.sortByDescending { post ->
                            MyApplication.database.likeDao().getLikeCountByPost(post.postId)
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

    private fun loadPostFromDB() {
        val posts = MyApplication.database.postDao().getAllPosts()

        fullPostList.clear()
        fullPostList.addAll(posts)
        boardAdapter.setPosts(fullPostList)

        if (posts.isEmpty()) {
            binding.rvBoard.visibility = View.GONE
            binding.tvNoBoard.visibility = View.VISIBLE
        } else {
            binding.rvBoard.visibility = View.VISIBLE
            binding.tvNoBoard.visibility = View.GONE
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // 메모리 누수 방지를 위해 반드시 null 처리
        _binding = null
    }
}