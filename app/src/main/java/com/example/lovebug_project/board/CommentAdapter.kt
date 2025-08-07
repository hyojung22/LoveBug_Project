package com.example.lovebug_project.board

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.lovebug_project.R
import com.example.lovebug_project.data.db.MyApplication
import com.example.lovebug_project.data.db.entity.Comment

class CommentAdapter(
    private val currentUserId: Int, // 현재 로그인한 사용자 ID
    private val onDeleteClick: (Comment) -> Unit, // 삭제
    private val onUpdateClick: (Comment, String) -> Unit, // 수정 완료 시 DB 반영
    private val onListChanged: ((Int) -> Unit)? = null // 🔹 추가: 리스트 변경 시 콜백
): RecyclerView.Adapter<CommentAdapter.CommentViewHolder>() {

    private var commentList: List<Comment> = emptyList()

    fun setComments(comments: List<Comment>) {
        commentList = comments
        notifyDataSetChanged()
        onListChanged?.invoke(commentList.size) // 🔹 리스트 개수 콜백 호출
    }
    inner class CommentViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val imgProfile: ImageView = itemView.findViewById(R.id.imgProfile)
        val tvNick: TextView = itemView.findViewById(R.id.tvNick)
        val tvCommentContent: TextView = itemView.findViewById(R.id.tvCommentContent)
        val tvTime: TextView = itemView.findViewById(R.id.tvTime)
        val btnDelete: TextView = itemView.findViewById(R.id.btnDeleteComment)
        val btnEdit: TextView = itemView.findViewById(R.id.btnEditComment)
        val btnFinish: TextView = itemView.findViewById(R.id.btnfinishComment)

    }


    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): CommentViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.comment_list_item, parent, false)
        return CommentViewHolder(view)
    }

    override fun onBindViewHolder(
        holder: CommentViewHolder,
        position: Int
    ) {
        val comment = commentList[position]
        // TODO: Implement Supabase user lookup
        // val user = MyApplication.authRepository.getUserById(comment.userId)

        // 닉네임 - temporary placeholder
        holder.tvNick.text = "사용자"

        // 프로필 이미지 - temporary placeholder
        holder.imgProfile.setImageResource(R.drawable.circle_button)

        // 댓글 내용, 시간
        holder.tvCommentContent.text = comment.content
        holder.tvCommentContent.isEnabled = false // 기본은 읽기 모드
        holder.tvTime.text = comment.updatedAt ?: comment.createdAt

        // 자기 댓글만 수정/삭제 버튼 보이기
        if (comment.userId == currentUserId) {
            holder.btnEdit.visibility = View.VISIBLE
            holder.btnDelete.visibility = View.VISIBLE
        } else {
            holder.btnEdit.visibility = View.GONE
            holder.btnDelete.visibility = View.GONE
        }

        // 삭제 버튼
        holder.btnDelete.setOnClickListener {
            onDeleteClick(comment)
        }

        // 수정 버튼 클릭
        holder.btnEdit.setOnClickListener {
            holder.btnEdit.visibility = View.GONE
            holder.btnDelete.visibility = View.GONE
            holder.btnFinish.visibility = View.VISIBLE

            holder.tvCommentContent.isEnabled = true
            holder.tvCommentContent.requestFocus()
        }

        // 완료 버튼
        holder.btnFinish.setOnClickListener {
            val newContent = holder.tvCommentContent.text.toString().trim()
            if (newContent.isNotEmpty() && newContent != comment.content) {
                onUpdateClick(comment, newContent) // DB 반영
                // Note: comment.content is val, so we can't reassign it
                // The content will be updated when the adapter refreshes with new data
                holder.tvTime.text = "${System.currentTimeMillis()} (수정됨)" // 필요 시 포맷 변경
            }

            holder.tvCommentContent.isEnabled = false
            holder.btnFinish.visibility = View.GONE
            holder.btnEdit.visibility = View.VISIBLE
            holder.btnDelete.visibility = View.VISIBLE
        }
    }

    override fun getItemCount(): Int = commentList.size


}