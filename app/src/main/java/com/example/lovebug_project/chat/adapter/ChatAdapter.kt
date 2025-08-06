package com.example.lovebug_project.chat.adapter

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
// import androidx.compose.ui.semantics.text // 이 줄 삭제
import androidx.recyclerview.widget.RecyclerView
import com.example.lovebug_project.R
import com.example.lovebug_project.chat.model.Message // 올바른 Message 모델 import
import com.example.lovebug_project.databinding.ItemChatMessageReceivedBinding
import com.example.lovebug_project.databinding.ItemChatMessageSentBinding

class ChatAdapter(
    private val messages: MutableList<Message>, // 타입 변경: com.example.lovebug_project.chat.model.Message
    private val currentUserId: String,
    private val onMessageDoubleClicked: (messageId: String) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val VIEW_TYPE_SENT = 1
        private const val VIEW_TYPE_RECEIVED = 2
    }

    override fun getItemViewType(position: Int): Int {
        val message = messages[position]
        // 이제 message는 올바른 타입이므로 senderId 필드에 접근 가능
        return if (message.senderId == currentUserId) VIEW_TYPE_SENT else VIEW_TYPE_RECEIVED
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == VIEW_TYPE_SENT) {
            val binding = ItemChatMessageSentBinding.inflate(inflater, parent, false)
            SentMessageViewHolder(binding)
        } else {
            val binding = ItemChatMessageReceivedBinding.inflate(inflater, parent, false)
            ReceivedMessageViewHolder(binding)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val message = messages[position]
        when (holder) {
            is SentMessageViewHolder -> holder.bind(message, onMessageDoubleClicked)
            is ReceivedMessageViewHolder -> holder.bind(message, onMessageDoubleClicked)
        }
    }

    override fun getItemCount(): Int = messages.size

    fun addMessage(message: Message) { // 타입 변경: com.example.lovebug_project.chat.model.Message
        messages.add(message)
        notifyItemInserted(messages.size - 1)
    }

    inner class SentMessageViewHolder(private val binding: ItemChatMessageSentBinding) :
        RecyclerView.ViewHolder(binding.root) {

        private var lastClickTime: Long = 0
        private val DOUBLE_CLICK_TIME_DELTA: Long = 300

        fun bind(
            message: Message, // 타입 변경: com.example.lovebug_project.chat.model.Message
            messageDoubleClickCallback: (messageId: String) -> Unit
        ) {
            // 이제 message는 올바른 타입이므로 text, timestamp, isLikedByCurrentUser, messageId 필드에 접근 가능
            binding.textViewMessageText.text = message.text
            binding.textViewTimestamp.text = formatTimestamp(message.timestamp)

            binding.textViewLikeCount.visibility = View.GONE

            if (message.isLikedByCurrentUser) {
                binding.imageViewLikeIcon.visibility = View.VISIBLE
                binding.imageViewLikeIcon.setImageResource(R.drawable.like_on)
                binding.layoutLikes.visibility = View.VISIBLE
            } else {
                binding.imageViewLikeIcon.visibility = View.GONE
                binding.layoutLikes.visibility = View.GONE
            }

            binding.textViewMessageText.setOnClickListener {
                val clickTime = System.currentTimeMillis()
                if (clickTime - lastClickTime < DOUBLE_CLICK_TIME_DELTA) {
                    if (message.messageId.isNotBlank()) {
                        messageDoubleClickCallback(message.messageId)
                    }
                }
                lastClickTime = clickTime
            }
        }
    }

    inner class ReceivedMessageViewHolder(private val binding: ItemChatMessageReceivedBinding) :
        RecyclerView.ViewHolder(binding.root) {

        private var lastClickTime: Long = 0
        private val DOUBLE_CLICK_TIME_DELTA: Long = 300

        // 이 ViewHolder의 bind 함수는 이미 올바른 Message 타입을 사용하고 있었으므로 수정할 필요가 없었습니다.
        // 하지만 일관성을 위해 다른 부분과 함께 검토합니다.
        fun bind(
            message: Message, // 이미 com.example.lovebug_project.chat.model.Message 타입이었음
            messageDoubleClickCallback: (messageId: String) -> Unit
        ) {
            binding.textViewMessageText.text = message.text
            binding.textViewTimestamp.text = formatTimestamp(message.timestamp)

            binding.textViewLikeCount.visibility = View.GONE

            if (message.isLikedByCurrentUser) {
                binding.imageViewLikeIcon.visibility = View.VISIBLE
                binding.imageViewLikeIcon.setImageResource(R.drawable.like_on)
                binding.layoutLikes.visibility = View.VISIBLE
            } else {
                binding.imageViewLikeIcon.visibility = View.GONE
                binding.layoutLikes.visibility = View.GONE
            }

            binding.textViewMessageText.setOnClickListener {
                val clickTime = System.currentTimeMillis()
                if (clickTime - lastClickTime < DOUBLE_CLICK_TIME_DELTA) {
                    if (message.messageId.isNotBlank()) {
                        messageDoubleClickCallback(message.messageId)
                    }
                }
                lastClickTime = clickTime
            }
        }
    }

    private fun formatTimestamp(timestamp: Long): String {
        return try {
            val sdf = java.text.SimpleDateFormat("a hh:mm", java.util.Locale.getDefault())
            sdf.format(java.util.Date(timestamp))
        } catch (e: Exception) {
            Log.e("ChatAdapter", "Error formatting timestamp", e)
            ""
        }
    }
}
