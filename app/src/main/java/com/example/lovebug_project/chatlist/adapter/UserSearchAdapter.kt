package com.example.lovebug_project.chatlist.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.lovebug_project.data.supabase.models.ChatUserSearchResult
import com.example.lovebug_project.databinding.ItemUserSearchResultBinding
import com.example.lovebug_project.utils.loadProfileImage

/**
 * Adapter for displaying user search results in the new chat dialog
 */
class UserSearchAdapter(
    private var users: List<ChatUserSearchResult>,
    private val onUserClick: (ChatUserSearchResult) -> Unit
) : RecyclerView.Adapter<UserSearchAdapter.UserSearchViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UserSearchViewHolder {
        val binding = ItemUserSearchResultBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return UserSearchViewHolder(binding)
    }

    override fun onBindViewHolder(holder: UserSearchViewHolder, position: Int) {
        holder.bind(users[position])
    }

    override fun getItemCount(): Int = users.size

    fun updateUsers(newUsers: List<ChatUserSearchResult>) {
        users = newUsers
        notifyDataSetChanged()
    }

    inner class UserSearchViewHolder(
        private val binding: ItemUserSearchResultBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(user: ChatUserSearchResult) {
            binding.textViewNickname.text = user.nickname

            // 프로필 이미지 로딩 구현
            binding.imageViewProfile.loadProfileImage(user.avatarUrl)

            binding.root.setOnClickListener {
                onUserClick(user)
            }
        }
    }
}