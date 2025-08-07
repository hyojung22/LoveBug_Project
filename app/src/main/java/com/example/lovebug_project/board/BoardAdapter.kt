package com.example.lovebug_project.board

import android.content.Context
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import java.util.concurrent.Executors
import android.os.Handler
import android.os.Looper
import com.example.lovebug_project.R
import com.example.lovebug_project.data.db.MyApplication
import com.example.lovebug_project.data.db.entity.Like
import com.example.lovebug_project.data.db.entity.Post
import com.example.lovebug_project.data.db.entity.PostWithExtras
import com.example.lovebug_project.utils.loadProfileImage
import com.example.lovebug_project.utils.AuthHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class BoardAdapter(
    private val onItemClick: (PostWithExtras) -> Unit,
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.Main)
) : RecyclerView.Adapter<BoardAdapter.BoardViewHolder>() {
    private var postList: List<PostWithExtras> = emptyList()
    
    companion object {
        private val backgroundExecutor = Executors.newFixedThreadPool(2)
        private val mainHandler = Handler(Looper.getMainLooper())
    }

    fun setPosts(posts: List<PostWithExtras>) {
        this.postList = posts
        notifyDataSetChanged()
    }

    inner class BoardViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val imgBoard: ImageView = itemView.findViewById(R.id.imgBoard)
        val imgProfile : ImageView = itemView.findViewById(R.id.imgProfile)
        val tvNick : TextView = itemView.findViewById(R.id.tvNick)
        val tvLike : TextView = itemView.findViewById(R.id.tvLike)
        val imgLike : ImageView = itemView.findViewById(R.id.imgLike)
        val imgComment : ImageView = itemView.findViewById(R.id.imgComment)
        val tvComment : TextView = itemView.findViewById(R.id.tvComment)
        val imgBookmark : ImageView = itemView.findViewById(R.id.imgBookmark)
        val tvBoardTitle: TextView = itemView.findViewById(R.id.tvBoardTitle)

    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): BoardViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.board_list_item, parent,false)
        return BoardViewHolder(view)
    }

    override fun onBindViewHolder(
        holder: BoardViewHolder,
        position: Int
    ) {
        val postExtra = postList[position]
        val post = postExtra.post

        holder.tvBoardTitle.text = post.title
        holder.tvNick.text = postExtra.nickname
        holder.tvLike.text = postExtra.likeCount.toString()
        holder.tvComment.text = postExtra.commentCount.toString()
        
        // 프로필 이미지 로딩
        holder.imgProfile.loadProfileImage(postExtra.profileImage)

        val context = holder.itemView.context
        val sharedPref = context.getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
        val currentUserId = sharedPref.getInt("userId", -1)

        // 북마크 표시
        if (post.userId == currentUserId) {
            // 내가 쓴 글 → 북마크 숨기기
            holder.imgBookmark.visibility = View.GONE
        } else {
            holder.imgBookmark.visibility = View.VISIBLE
            holder.imgBookmark.setImageResource(
                if (postExtra.isBookmarked) R.drawable.bookmark_on else R.drawable.bookmark
            )
        }

        // 이미지 표시
        if (!post.image.isNullOrEmpty()) {
            Glide.with(holder.itemView.context)
                .load(Uri.parse(post.image))
                .into(holder.imgBoard)
        } else {
            holder.imgBoard.setImageResource(R.drawable.app_logo)
        }

        // 현재 사용자 UUID 가져오기
        val currentUserUuid = AuthHelper.getSupabaseUserId(context)
        
        // 좋아요 상태를 백그라운드에서 불러오기
        var isLiked = false
        var likeCount = 0
        
        // 초기 로딩 상태 설정 (기본값)
        updateLikeUI(holder, false, postExtra.likeCount)
        
        // Supabase에서 실제 좋아요 상태 로드
        if (currentUserUuid != null) {
            coroutineScope.launch(Dispatchers.IO) {
                try {
                    // 좋아요 상태와 개수를 동시에 조회
                    val isLikedResult = MyApplication.repositoryManager.postRepository.isPostLikedByUser(
                        post.postId, currentUserUuid
                    )
                    val likeCountResult = MyApplication.repositoryManager.postRepository.getLikeCountByPost(
                        post.postId
                    )
                    
                    withContext(Dispatchers.Main) {
                        isLikedResult.fold(
                            onSuccess = { liked ->
                                isLiked = liked
                                likeCountResult.fold(
                                    onSuccess = { count ->
                                        likeCount = count
                                        updateLikeUI(holder, isLiked, likeCount)
                                    },
                                    onFailure = {
                                        // 개수 조회 실패시 기본값 사용
                                        likeCount = postExtra.likeCount
                                        updateLikeUI(holder, isLiked, likeCount)
                                    }
                                )
                            },
                            onFailure = {
                                // 에러 시 기본값 유지
                                updateLikeUI(holder, false, postExtra.likeCount)
                            }
                        )
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        updateLikeUI(holder, false, postExtra.likeCount)
                    }
                }
            }
        }

        // 좋아요 버튼 클릭 (Supabase DB 연동)
        holder.imgLike.setOnClickListener {
            if (currentUserUuid == null) {
                android.widget.Toast.makeText(context, "로그인이 필요합니다.", android.widget.Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            // 중복 클릭 방지
            holder.imgLike.isEnabled = false
            
            coroutineScope.launch(Dispatchers.IO) {
                try {
                    val result = if (isLiked) {
                        // 좋아요 취소
                        MyApplication.repositoryManager.postRepository.removeLike(post.postId, currentUserUuid)
                    } else {
                        // 좋아요 추가
                        MyApplication.repositoryManager.postRepository.addLike(post.postId, currentUserUuid)
                    }
                    
                    result.fold(
                        onSuccess = {
                            // 성공시 최신 좋아요 개수 조회
                            val countResult = MyApplication.repositoryManager.postRepository.getLikeCountByPost(post.postId)
                            
                            withContext(Dispatchers.Main) {
                                countResult.fold(
                                    onSuccess = { newCount ->
                                        isLiked = !isLiked
                                        likeCount = newCount
                                        updateLikeUI(holder, isLiked, likeCount)
                                    },
                                    onFailure = {
                                        // 개수 조회 실패시 UI만 토글
                                        isLiked = !isLiked
                                        likeCount = if (isLiked) likeCount + 1 else maxOf(0, likeCount - 1)
                                        updateLikeUI(holder, isLiked, likeCount)
                                    }
                                )
                                holder.imgLike.isEnabled = true
                            }
                        },
                        onFailure = { exception ->
                            withContext(Dispatchers.Main) {
                                holder.imgLike.isEnabled = true
                                android.widget.Toast.makeText(
                                    context, 
                                    "좋아요 처리 실패: ${exception.message}", 
                                    android.widget.Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    )
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        holder.imgLike.isEnabled = true
                        android.widget.Toast.makeText(
                            context, 
                            "오류가 발생했습니다: ${e.message}", 
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        }

        // 클릭 시 이벤트
        holder.itemView.setOnClickListener {
            onItemClick(postExtra)
        }
    }

    override fun getItemCount(): Int = postList.size
}

// 좋아요 UI 업데이트 함수
private fun updateLikeUI(holder: BoardAdapter.BoardViewHolder, isLiked: Boolean, likeCount: Int) {
    holder.imgLike.setImageResource(if (isLiked) R.drawable.like_on else R.drawable.like_off)
    holder.tvLike.text = likeCount.toString()
}