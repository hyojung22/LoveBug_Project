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
import com.example.lovebug_project.R
import com.example.lovebug_project.data.db.MyApplication
import com.example.lovebug_project.data.db.entity.Like
import com.example.lovebug_project.data.db.entity.Post
import com.example.lovebug_project.data.db.entity.PostWithExtras

class BoardAdapter(
    private val onItemClick: (PostWithExtras) -> Unit
) : RecyclerView.Adapter<BoardAdapter.BoardViewHolder>() {
    private var postList: List<PostWithExtras> = emptyList()

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
            holder.imgBoard.setImageResource(R.drawable.ic_launcher_background)
        }

        // 좋아요 상태 가져오기
        val likeDao = MyApplication.database.likeDao()
        var isLiked = likeDao.isPostLikedByUser(currentUserId, post.postId)
        updateLikeUI(holder, isLiked, likeDao.getLikeCountByPost(post.postId))

        // 좋아요 버튼 클릭
        holder.imgLike.setOnClickListener {
            if (isLiked) {
                // 좋아요 취소
                likeDao.deleteLike(currentUserId, post.postId)
            } else {
                // 좋아요 추가
                likeDao.insert(Like(postId = post.postId, userId = currentUserId))
            }
            // 상태 반전
            isLiked = !isLiked

            // DB에서 최신 개수 가져와서 UI 업데이트
            val newCount = likeDao.getLikeCountByPost(post.postId)
            updateLikeUI(holder, isLiked, newCount)
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