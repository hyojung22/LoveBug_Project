package com.example.lovebug_project.chat

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.lovebug_project.chat.adapter.ChatAdapter
import com.example.lovebug_project.chat.model.Message
import com.example.lovebug_project.databinding.FragmentChatBinding
import com.example.lovebug_project.utils.hideKeyboard

class ChatFragment : Fragment() {

    private var _binding: FragmentChatBinding? = null
    private val binding get() = _binding!!

    private lateinit var chatAdapter: ChatAdapter
    private val messagesList = mutableListOf<Message>()
    private var currentUserId: String = "test_user_id"
    private var chatRoomId: String = "test_room_id"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d("ChatFragment", "onCreate: currentUserId=$currentUserId, chatRoomId=$chatRoomId")
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentChatBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        setupSendButton()
        setupKeyboardDismissListener()
        addInitialTestMessages()
    }

    private fun setupRecyclerView() {
        chatAdapter = ChatAdapter(
            messagesList,
            currentUserId,
            onMessageDoubleClicked = { messageId -> // 더블 클릭 콜백만 사용
                Log.d("ChatFragment", "Message double clicked, toggling like for ID: $messageId")
                handleLikeToggle(messageId)
            }
        )
        binding.recyclerViewChat.apply {
            layoutManager = LinearLayoutManager(requireContext()).apply {
                stackFromEnd = true
            }
            adapter = chatAdapter
        }
    }

    private fun setupSendButton() {
        binding.buttonSend.setOnClickListener {
            val messageText = binding.editTextMessage.text.toString().trim()
            if (messageText.isNotEmpty()) {
                sendLocalMessage(messageText)
                binding.editTextMessage.text.clear()
                binding.editTextMessage.hideKeyboard()
            }
        }
    }

    private fun sendLocalMessage(text: String) {
        if (currentUserId.isBlank()) {
            Log.w("ChatFragment", "Cannot send message, currentUserId is blank (local).")
            return
        }
        val newMessage = Message(
            messageId = "local_${System.currentTimeMillis()}",
            senderId = currentUserId,
            text = text,
            timestamp = System.currentTimeMillis(),
            chatRoomId = chatRoomId
        )
        messagesList.add(newMessage)
        chatAdapter.notifyItemInserted(messagesList.size - 1)
        binding.recyclerViewChat.smoothScrollToPosition(chatAdapter.itemCount - 1)
        Log.d("ChatFragment", "Local message sent: ${newMessage.text}")
    }

    private fun handleLikeToggle(messageId: String) {
        if (currentUserId.isBlank() || messageId.isBlank()) {
            Log.w("ChatFragment", "Cannot toggle like: User or Message ID is blank.")
            return
        }

        val messageIndex = messagesList.indexOfFirst { it.messageId == messageId }
        if (messageIndex != -1) {
            val message = messagesList[messageIndex]

            message.isLikedByCurrentUser = !message.isLikedByCurrentUser // 상태 반전

            // likedBy 리스트와 likeCount는 내부적으로 관리 (UI에는 카운트 미표시)
            if (message.isLikedByCurrentUser) {
                if (!message.likedBy.contains(currentUserId)) {
                    message.likedBy.add(currentUserId)
                }
            } else {
                message.likedBy.remove(currentUserId)
            }
            message.likeCount = message.likedBy.size // 이 값은 UI에 직접 사용되진 않음

            chatAdapter.notifyItemChanged(messageIndex)

            Log.d("ChatFragment", "Like status toggled for message: $messageId. Liked: ${message.isLikedByCurrentUser}")
        } else {
            Log.w("ChatFragment", "Message with ID $messageId not found for like toggle.")
        }
    }

    private fun addInitialTestMessages() {
        if (messagesList.isEmpty()) {
            val testMessages = listOf(
                Message("local_0", "other_user_test_id", "안녕! (상대방 메시지)", System.currentTimeMillis() - 20000, chatRoomId),
                Message("local_1", currentUserId, "응 안녕! (내 메시지)", System.currentTimeMillis() - 10000, chatRoomId),
                Message("local_2", "other_user_test_id", "이 메시지를 더블 클릭 해보세요.", System.currentTimeMillis() - 5000, chatRoomId)
            )
            messagesList.addAll(testMessages)
            chatAdapter.notifyDataSetChanged()
            binding.recyclerViewChat.scrollToPosition(chatAdapter.itemCount - 1)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupKeyboardDismissListener() {
        binding.recyclerViewChat.setOnTouchListener { _, _ ->
            binding.editTextMessage.hideKeyboard()
            false
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        Log.d("ChatFragment", "onDestroyView")
    }
}
