package com.example.lovebug_project.chatlist.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.lovebug_project.data.supabase.models.ChatUserSearchResult
import com.example.lovebug_project.databinding.ItemUserSearchResultBinding

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

            // TODO: 프로필 이미지 로딩 구현 (Glide 또는 Coil 사용)
            // if (!user.avatarUrl.isNullOrEmpty()) {
            //     Glide.with(binding.imageViewProfile.context)
            //         .load(user.avatarUrl)
            //         .placeholder(R.drawable.default_profile_image)
            //         .into(binding.imageViewProfile)
            // }

            binding.root.setOnClickListener {
                onUserClick(user)
            }
        }
    }
}