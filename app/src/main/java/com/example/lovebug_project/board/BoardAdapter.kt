package com.example.lovebug_project.board

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.lovebug_project.R
import com.example.lovebug_project.data.db.entity.Post

class BoardAdapter : RecyclerView.Adapter<BoardAdapter.BoardViewHolder>() {
    private var postList: List<Post> = emptyList()

    fun setPosts(posts: List<Post>) {
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
        val post = postList[position]

        // 제목
        holder.tvBoardTitle.text = post.title

        // 이미지 (Glide로 로드, 이미지 없을 경우 기본 이미지)
        if (!post.image.isNullOrEmpty()) {
            Glide.with(holder.itemView.context)
                .load(post.image)
                .into(holder.imgBoard)
        } else {
            holder.imgBoard.setImageResource(R.drawable.ic_launcher_background) // 기본 이미지
        }
    }

    override fun getItemCount(): Int = postList.size
}