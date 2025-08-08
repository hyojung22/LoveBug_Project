package com.example.lovebug_project.chat.adapter

import android.graphics.Color
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.example.lovebug_project.R
import com.example.lovebug_project.chat.model.Message // 올바른 Message 모델 import
import com.example.lovebug_project.databinding.ItemChatMessageReceivedBinding
import com.example.lovebug_project.databinding.ItemChatMessageSentBinding
import com.example.lovebug_project.utils.loadProfileImage

/**
 * 강화된 ChatAdapter - Optimistic UI 및 메시지 상태 처리 지원
 * - 메시지 상태별 시각적 피드백 (보냈 중, 실패, 임시)
 * - DiffUtil을 통한 효율적인 UI 업데이트
 * - 재시도 기능 및 동기화 최적화
 */
class ChatAdapter(
    private val messages: MutableList<Message>, // 타입 변경: com.example.lovebug_project.chat.model.Message
    private val currentUserId: String,
    private val onMessageDoubleClicked: (messageId: String) -> Unit,
    private val onRetryMessage: (messageId: String) -> Unit = {} // 재시도 콜백 추가
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val VIEW_TYPE_SENT = 1
        private const val VIEW_TYPE_RECEIVED = 2
        
        /**
         * 메시지 상태 선명 체크 유틸리티
         */
        @JvmStatic
        fun getMessageStateDescription(message: Message): String {
            return when {
                message.isFailed -> "전송 실패 (재시도 가능)"
                message.isPending -> "전송 중..."
                message.isTemporary -> "임시 저장"
                message.isCompleted() -> "전송 완료"
                else -> "알 수 없음"
            }
        }
        
        /**
         * 메시지 리스트 유효성 검사
         */
        @JvmStatic
        fun validateMessageList(messages: List<Message>): Boolean {
            // 중복 ID 체크
            val ids = messages.map { it.messageId }
            if (ids.size != ids.toSet().size) {
                Log.w("ChatAdapter", "⚠️ Duplicate message IDs found in list")
                return false
            }
            
            // 빈 메시지 체크
            val emptyMessages = messages.filter { it.text.isBlank() && it.messageId.isNotBlank() }
            if (emptyMessages.isNotEmpty()) {
                Log.w("ChatAdapter", "⚠️ ${emptyMessages.size} empty messages found")
            }
            
            return true
        }
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

    /**
     * 개선된 메시지 추가 메소드
     */
    fun addMessage(message: Message) {
        synchronized(messages) {
            messages.add(message)
        }
        notifyItemInserted(messages.size - 1)
    }
    
    /**
     * DiffUtil을 사용한 효율적인 대량 업데이트
     */
    fun updateMessages(newMessages: List<Message>) {
        val diffCallback = MessageDiffCallback(messages, newMessages)
        val diffResult = DiffUtil.calculateDiff(diffCallback)
        
        synchronized(messages) {
            messages.clear()
            messages.addAll(newMessages)
        }
        
        diffResult.dispatchUpdatesTo(this)
    }
    
    /**
     * 특정 메시지 업데이트 (상태 변경 시 사용)
     */
    fun updateMessage(messageId: String, updatedMessage: Message) {
        synchronized(messages) {
            val index = messages.indexOfFirst { it.messageId == messageId }
            if (index != -1) {
                messages[index] = updatedMessage
                notifyItemChanged(index)
                Log.d("ChatAdapter", "✅ Message updated: $messageId at position $index")
            } else {
                Log.w("ChatAdapter", "⚠️ Message not found for update: $messageId")
            }
        }
    }
    
    /**
     * 메시지 제거
     */
    fun removeMessage(messageId: String) {
        synchronized(messages) {
            val index = messages.indexOfFirst { it.messageId == messageId }
            if (index != -1) {
                messages.removeAt(index)
                notifyItemRemoved(index)
                Log.d("ChatAdapter", "✅ Message removed: $messageId at position $index")
            }
        }
    }
    
    /**
     * DiffCallback for efficient list updates
     */
    private class MessageDiffCallback(
        private val oldList: List<Message>,
        private val newList: List<Message>
    ) : DiffUtil.Callback() {
        
        override fun getOldListSize(): Int = oldList.size
        override fun getNewListSize(): Int = newList.size
        
        override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            return oldList[oldItemPosition].messageId == newList[newItemPosition].messageId
        }
        
        override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            val oldMessage = oldList[oldItemPosition]
            val newMessage = newList[newItemPosition]
            
            return oldMessage.text == newMessage.text &&
                oldMessage.isPending == newMessage.isPending &&
                oldMessage.isFailed == newMessage.isFailed &&
                oldMessage.isTemporary == newMessage.isTemporary &&
                oldMessage.isLikedByCurrentUser == newMessage.isLikedByCurrentUser &&
                oldMessage.timestamp == newMessage.timestamp
        }
    }

    inner class SentMessageViewHolder(private val binding: ItemChatMessageSentBinding) :
        RecyclerView.ViewHolder(binding.root) {

        private var lastClickTime: Long = 0
        private val DOUBLE_CLICK_TIME_DELTA: Long = 300

        fun bind(
            message: Message, // 타입 변경: com.example.lovebug_project.chat.model.Message
            messageDoubleClickCallback: (messageId: String) -> Unit
        ) {
            // 기본 내용 바인딩
            binding.textViewMessageText.text = message.text
            binding.textViewTimestamp.text = formatTimestamp(message.timestamp)
            
            // 보낸 사람 이름 바인딩 (내가 보낸 메시지)
            binding.textViewSenderName.text = message.senderName.ifEmpty { "나" }
            
            // 메시지 상태에 따른 시각적 피드백 처리
            applyMessageStateVisuals(message, binding.root, binding.textViewMessageText)
            
            // 좋아요 처리
            binding.textViewLikeCount.visibility = View.GONE
            if (message.isLikedByCurrentUser) {
                binding.imageViewLikeIcon.visibility = View.VISIBLE
                binding.imageViewLikeIcon.setImageResource(R.drawable.like_on)
                binding.layoutLikes.visibility = View.VISIBLE
            } else {
                binding.imageViewLikeIcon.visibility = View.GONE
                binding.layoutLikes.visibility = View.GONE
            }
            
            // 이벤트 처리 (더블클릭, 재시도)
            setupMessageClickHandlers(message, messageDoubleClickCallback)
        }
        
        /**
         * 메시지 상태에 따른 시각적 피드백 설정
         */
        private fun applyMessageStateVisuals(message: Message, rootView: View, textView: View) {
            when {
                message.isFailed -> {
                    // 실패 상태: 빨간색 테두리, 반투명
                    rootView.alpha = 0.7f
                    textView.setBackgroundColor(Color.parseColor("#FFEBEE")) // Light red background
                    Log.d("ChatAdapter", "❌ Failed message visual applied: ${message.messageId}")
                }
                message.isPending -> {
                    // 전송 중 상태: 반투명
                    rootView.alpha = 0.6f
                    textView.setBackgroundColor(Color.parseColor("#FFF3E0")) // Light orange background
                    Log.d("ChatAdapter", "⏳ Pending message visual applied: ${message.messageId}")
                }
                message.isTemporary -> {
                    // 임시 상태: 살짝 반투명
                    rootView.alpha = 0.8f
                    textView.setBackgroundColor(Color.parseColor("#F3E5F5")) // Light purple background
                    Log.d("ChatAdapter", "📄 Temporary message visual applied: ${message.messageId}")
                }
                else -> {
                    // 정상 상태: 기본 스타일
                    rootView.alpha = 1.0f
                    textView.setBackgroundColor(Color.parseColor("#E8F5E8")) // Light green background
                }
            }
        }
        
        /**
         * 메시지 클릭 이벤트 설정 (더블클릭, 재시도)
         */
        private fun setupMessageClickHandlers(message: Message, messageDoubleClickCallback: (messageId: String) -> Unit) {
            binding.textViewMessageText.setOnClickListener {
                val clickTime = System.currentTimeMillis()
                
                // 실패한 메시지의 경우 재시도 특별 처리
                if (message.isFailed && message.canRetry()) {
                    Log.d("ChatAdapter", "🔄 Retry failed message: ${message.messageId}")
                    onRetryMessage(message.messageId)
                    return@setOnClickListener
                }
                
                // 일반적인 더블클릭 처리
                if (clickTime - lastClickTime < DOUBLE_CLICK_TIME_DELTA) {
                    if (message.messageId.isNotBlank()) {
                        messageDoubleClickCallback(message.messageId)
                    }
                }
                lastClickTime = clickTime
            }
            
            // 롤프레스 처리
            binding.textViewMessageText.setOnLongClickListener {
                if (message.isFailed) {
                    Log.d("ChatAdapter", "🔄 Long press retry for failed message: ${message.messageId}")
                    onRetryMessage(message.messageId)
                    true
                } else {
                    false
                }
            }
        }
    }

    inner class ReceivedMessageViewHolder(private val binding: ItemChatMessageReceivedBinding) :
        RecyclerView.ViewHolder(binding.root) {

        private var lastClickTime: Long = 0
        private val DOUBLE_CLICK_TIME_DELTA: Long = 300

        fun bind(
            message: Message, // 이미 com.example.lovebug_project.chat.model.Message 타입이었음
            messageDoubleClickCallback: (messageId: String) -> Unit
        ) {
            // 기본 내용 바인딩
            binding.textViewMessageText.text = message.text
            binding.textViewTimestamp.text = formatTimestamp(message.timestamp)
            
            // 프로필 이미지와 이름 바인딩
            binding.textViewSenderName.text = message.senderName.ifEmpty { "Unknown" }
            
            // 프로필 이미지 로딩 구현
            binding.imageViewSenderProfile.loadProfileImage(message.senderProfileImageUrl)
            
            // 받은 메시지 상태 처리 (보통 정상 상태이지만 예를 들어 시스템 오류 등이 있을 수 있음)
            applyReceivedMessageStateVisuals(message, binding.root, binding.textViewMessageText)
            
            // 좋아요 처리
            binding.textViewLikeCount.visibility = View.GONE
            if (message.isLikedByCurrentUser) {
                binding.imageViewLikeIcon.visibility = View.VISIBLE
                binding.imageViewLikeIcon.setImageResource(R.drawable.like_on)
                binding.layoutLikes.visibility = View.VISIBLE
            } else {
                binding.imageViewLikeIcon.visibility = View.GONE
                binding.layoutLikes.visibility = View.GONE
            }
            
            // 이벤트 처리
            setupReceivedMessageClickHandlers(message, messageDoubleClickCallback)
        }
        
        /**
         * 받은 메시지 상태에 따른 시각적 피드백
         */
        private fun applyReceivedMessageStateVisuals(message: Message, rootView: View, textView: View) {
            // 받은 메시지는 보통 정상 상태이지만, 예외적인 경우 처리
            when {
                // 예비: 시스템 오류나 데이터 손상으로 인한 문제 메시지
                message.text.isEmpty() -> {
                    rootView.alpha = 0.5f
                    textView.setBackgroundColor(Color.parseColor("#FFCDD2")) // Light red for errors
                    Log.w("ChatAdapter", "⚠️ Empty received message: ${message.messageId}")
                }
                else -> {
                    // 정상 상태
                    rootView.alpha = 1.0f
                    textView.setBackgroundColor(Color.parseColor("#F5F5F5")) // Light gray for received
                }
            }
        }
        
        /**
         * 받은 메시지 클릭 이벤트 설정
         */
        private fun setupReceivedMessageClickHandlers(message: Message, messageDoubleClickCallback: (messageId: String) -> Unit) {
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
