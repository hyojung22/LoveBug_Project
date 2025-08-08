package com.example.lovebug_project.mypage

import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.lovebug_project.R
import com.example.lovebug_project.data.db.MyApplication
import com.example.lovebug_project.data.supabase.models.PostWithProfile
import com.example.lovebug_project.utils.AuthHelper
import com.example.lovebug_project.utils.loadProfileImage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 나의 게시물을 표시하는 어댑터
 */
class MyBoardAdapter(
    private val onItemClick: (PostWithProfile) -> Unit,
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.Main)
) : RecyclerView.Adapter<MyBoardAdapter.MyBoardViewHolder>() {
    
    private var postList: List<PostWithProfile> = emptyList()

    fun setPosts(posts: List<PostWithProfile>) {
        this.postList = posts
        notifyDataSetChanged()
    }

    inner class MyBoardViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val imgBoard: ImageView = itemView.findViewById(R.id.imgBoard)
        val imgProfile: ImageView = itemView.findViewById(R.id.imgProfile)
        val tvNick: TextView = itemView.findViewById(R.id.tvNick)
        val tvLike: TextView = itemView.findViewById(R.id.tvLike)
        val imgLike: ImageView = itemView.findViewById(R.id.imgLike)
        val imgComment: ImageView = itemView.findViewById(R.id.imgComment)
        val tvComment: TextView = itemView.findViewById(R.id.tvComment)
        val tvBoardTitle: TextView = itemView.findViewById(R.id.tvBoardTitle)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MyBoardViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.myboard_list_item, parent, false)
        return MyBoardViewHolder(view)
    }

    override fun onBindViewHolder(holder: MyBoardViewHolder, position: Int) {
        val post = postList[position]
        val context = holder.itemView.context

        // 게시글 정보 설정
        holder.tvBoardTitle.text = post.title
        holder.tvNick.text = post.nickname ?: "알 수 없는 사용자"
        
        // 프로필 이미지 로딩
        holder.imgProfile.loadProfileImage(post.avatarUrl)

        // 게시글 이미지 표시
        if (!post.imageUrl.isNullOrEmpty()) {
            Glide.with(context)
                .load(Uri.parse(post.imageUrl))
                .placeholder(R.drawable.app_logo)
                .error(R.drawable.app_logo)
                .into(holder.imgBoard)
        } else {
            holder.imgBoard.setImageResource(R.drawable.app_logo)
        }

        // 현재 사용자 UUID 가져오기
        val currentUserUuid = AuthHelper.getSupabaseUserId(context)
        
        // 좋아요, 댓글 개수 초기값 설정
        holder.tvLike.text = "0"
        holder.tvComment.text = "0"
        holder.imgLike.setImageResource(R.drawable.like_off)
        
        // Supabase에서 실제 좋아요와 댓글 개수 로드
        if (currentUserUuid != null) {
            coroutineScope.launch(Dispatchers.IO) {
                try {
                    // 좋아요 상태와 개수 조회
                    val isLikedResult = MyApplication.repositoryManager.postRepository.isPostLikedByUser(
                        post.postId, currentUserUuid
                    )
                    val likeCountResult = MyApplication.repositoryManager.postRepository.getLikeCountByPost(
                        post.postId
                    )
                    // 댓글 개수 조회
                    val commentCountResult = MyApplication.repositoryManager.postRepository.getCommentCountByPost(
                        post.postId
                    )
                    
                    withContext(Dispatchers.Main) {
                        // 좋아요 상태 업데이트
                        isLikedResult.fold(
                            onSuccess = { isLiked ->
                                holder.imgLike.setImageResource(
                                    if (isLiked) R.drawable.like_on else R.drawable.like_off
                                )
                            },
                            onFailure = {
                                holder.imgLike.setImageResource(R.drawable.like_off)
                            }
                        )
                        
                        // 좋아요 개수 업데이트
                        likeCountResult.fold(
                            onSuccess = { count ->
                                holder.tvLike.text = count.toString()
                            },
                            onFailure = {
                                holder.tvLike.text = "0"
                            }
                        )
                        
                        // 댓글 개수 업데이트
                        commentCountResult.fold(
                            onSuccess = { count ->
                                holder.tvComment.text = count.toString()
                            },
                            onFailure = {
                                holder.tvComment.text = "0"
                            }
                        )
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        // 에러 시 기본값 유지
                        holder.tvLike.text = "0"
                        holder.tvComment.text = "0"
                        holder.imgLike.setImageResource(R.drawable.like_off)
                    }
                }
            }
        }

        // 좋아요 버튼 클릭 이벤트
        holder.imgLike.setOnClickListener {
            if (currentUserUuid == null) {
                android.widget.Toast.makeText(context, "로그인이 필요합니다.", android.widget.Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            // 중복 클릭 방지
            holder.imgLike.isEnabled = false
            
            coroutineScope.launch(Dispatchers.IO) {
                try {
                    // 현재 좋아요 상태 확인
                    val isLikedResult = MyApplication.repositoryManager.postRepository.isPostLikedByUser(
                        post.postId, currentUserUuid
                    )
                    
                    isLikedResult.fold(
                        onSuccess = { isLiked ->
                            // 좋아요 토글
                            val result = if (isLiked) {
                                MyApplication.repositoryManager.postRepository.removeLike(post.postId, currentUserUuid)
                            } else {
                                MyApplication.repositoryManager.postRepository.addLike(post.postId, currentUserUuid)
                            }
                            
                            result.fold(
                                onSuccess = {
                                    // 최신 좋아요 개수 조회
                                    val countResult = MyApplication.repositoryManager.postRepository.getLikeCountByPost(post.postId)
                                    
                                    withContext(Dispatchers.Main) {
                                        countResult.fold(
                                            onSuccess = { newCount ->
                                                holder.imgLike.setImageResource(
                                                    if (!isLiked) R.drawable.like_on else R.drawable.like_off
                                                )
                                                holder.tvLike.text = newCount.toString()
                                            },
                                            onFailure = {
                                                // 개수 조회 실패시 UI만 토글
                                                holder.imgLike.setImageResource(
                                                    if (!isLiked) R.drawable.like_on else R.drawable.like_off
                                                )
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
                        },
                        onFailure = { exception ->
                            withContext(Dispatchers.Main) {
                                holder.imgLike.isEnabled = true
                                android.widget.Toast.makeText(
                                    context, 
                                    "좋아요 상태 확인 실패: ${exception.message}", 
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

        // 게시글 클릭 시 상세보기로 이동
        holder.itemView.setOnClickListener {
            onItemClick(post)
        }
    }

    override fun getItemCount(): Int = postList.size
}