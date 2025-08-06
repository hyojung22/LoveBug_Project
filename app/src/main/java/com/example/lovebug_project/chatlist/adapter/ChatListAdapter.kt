package com.example.lovebug_project.chatlist.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.lovebug_project.R
import com.example.lovebug_project.chatlist.model.ChatRoom
import com.example.lovebug_project.databinding.ItemChatRoomBinding // ViewBinding 사용
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ChatListAdapter(
    private var chatRooms: List<ChatRoom>,
    private val onItemClicked: (ChatRoom) -> Unit // 아이템 클릭 시 호출될 콜백 함수
) : RecyclerView.Adapter<ChatListAdapter.ChatRoomViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatRoomViewHolder {
        val binding = ItemChatRoomBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ChatRoomViewHolder(binding, onItemClicked)
    }

    override fun onBindViewHolder(holder: ChatRoomViewHolder, position: Int) {
        holder.bind(chatRooms[position])
    }

    override fun getItemCount(): Int = chatRooms.size

    fun updateData(newChatRooms: List<ChatRoom>) {
        chatRooms = newChatRooms
        notifyDataSetChanged() // DiffUtil 사용 권장
    }

    inner class ChatRoomViewHolder(
        private val binding: ItemChatRoomBinding,
        private val onItemClicked: (ChatRoom) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(chatRoom: ChatRoom) {
            binding.textViewPartnerName.text = chatRoom.partnerName
            binding.textViewLastMessage.text = chatRoom.lastMessage ?: "아직 대화가 없습니다."
            binding.textViewTimestamp.text = formatTimestamp(chatRoom.timestamp)
            // TODO: Glide나 Coil 같은 라이브러리로 프로필 이미지 로드
            // if (chatRoom.partnerProfileImageUrl != null) {
            //     Glide.with(binding.imageViewProfile.context).load(chatRoom.partnerProfileImageUrl).into(binding.imageViewProfile)
            // } else {
            //     binding.imageViewProfile.setImageResource(R.mipmap.ic_launcher_round) // 기본 이미지
            // }

            binding.root.setOnClickListener {
                onItemClicked(chatRoom)
            }
        }

        private fun formatTimestamp(timestamp: Long): String {
            val sdf = SimpleDateFormat("a hh:mm", Locale.getDefault())
            return sdf.format(Date(timestamp))
        }
    }
}