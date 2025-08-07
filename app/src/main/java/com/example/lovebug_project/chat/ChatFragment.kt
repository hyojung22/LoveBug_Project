package com.example.lovebug_project.chat

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.lovebug_project.chat.adapter.ChatAdapter
import com.example.lovebug_project.chat.model.Message
import com.example.lovebug_project.data.repository.SupabaseChatRepository
import com.example.lovebug_project.data.supabase.models.Chat
import com.example.lovebug_project.data.supabase.models.ChatMessage
import com.example.lovebug_project.databinding.FragmentChatBinding
import com.example.lovebug_project.utils.AuthHelper
import com.example.lovebug_project.utils.hideKeyboard
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.format.DateTimeParseException

class ChatFragment : Fragment() {

    private var _binding: FragmentChatBinding? = null
    private val binding get() = _binding!!

    private lateinit var chatAdapter: ChatAdapter
    private val messagesList = mutableListOf<Message>()
    private var currentUserId: String? = null
    private var chatRoomId: Int = -1
    private var otherUserId: String? = null
    
    private val chatRepository = SupabaseChatRepository()
    private var currentChat: Chat? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Get current user ID from AuthHelper
        currentUserId = AuthHelper.getSupabaseUserId(requireContext())
        
        // Get partner user ID from arguments
        otherUserId = arguments?.getString("partnerUserId")
        
        Log.d("ChatFragment", "onCreate: currentUserId=$currentUserId, otherUserId=$otherUserId")
        
        if (currentUserId == null) {
            Log.e("ChatFragment", "No authenticated user found!")
            return
        }
        
        if (otherUserId == null) {
            Log.e("ChatFragment", "No partner user ID provided!")
            return
        }
        
        // Initialize chat room
        initializeChatRoom()
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

        if (currentUserId == null || otherUserId == null) {
            Log.e("ChatFragment", "Cannot setup view without authenticated user or partner user ID")
            return
        }

        setupRecyclerView()
        setupSendButton()
        setupKeyboardDismissListener()
        // Note: loadChatHistory() and subscribeToRealtimeMessages() will be called after chat room is initialized
    }

    private fun setupRecyclerView() {
        chatAdapter = ChatAdapter(
            messagesList,
            currentUserId ?: "",
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
            if (messageText.isNotEmpty() && currentUserId != null && chatRoomId != -1) {
                sendMessage(messageText)
                binding.editTextMessage.text.clear()
                binding.editTextMessage.hideKeyboard()
            } else {
                Log.w("ChatFragment", "Cannot send message: text empty or chat not initialized")
            }
        }
    }

    /**
     * Send message using Supabase real-time chat
     */
    private fun sendMessage(text: String) {
        val userId = currentUserId ?: return
        
        lifecycleScope.launch {
            try {
                val sentMessage = chatRepository.sendMessage(chatRoomId, userId, text)
                if (sentMessage != null) {
                    Log.d("ChatFragment", "Message sent successfully: ${sentMessage.messageId}")
                    // Update chat timestamp
                    chatRepository.updateChatTimestamp(chatRoomId)
                } else {
                    Log.e("ChatFragment", "Failed to send message")
                    // TODO: Show error to user
                }
            } catch (e: Exception) {
                Log.e("ChatFragment", "Error sending message", e)
                // TODO: Show error to user
            }
        }
    }

    private fun handleLikeToggle(messageId: String) {
        if (currentUserId.isNullOrBlank() || messageId.isBlank()) {
            Log.w("ChatFragment", "Cannot toggle like: User or Message ID is blank.")
            return
        }

        val messageIndex = messagesList.indexOfFirst { it.messageId == messageId }
        if (messageIndex != -1) {
            val message = messagesList[messageIndex]

            message.isLikedByCurrentUser = !message.isLikedByCurrentUser // 상태 반전

            // likedBy 리스트와 likeCount는 내부적으로 관리 (UI에는 카운트 미표시)
            val userId = currentUserId!!
            if (message.isLikedByCurrentUser) {
                if (!message.likedBy.contains(userId)) {
                    message.likedBy.add(userId)
                }
            } else {
                message.likedBy.remove(userId)
            }
            message.likeCount = message.likedBy.size // 이 값은 UI에 직접 사용되진 않음

            chatAdapter.notifyItemChanged(messageIndex)

            Log.d("ChatFragment", "Like status toggled for message: $messageId. Liked: ${message.isLikedByCurrentUser}")
        } else {
            Log.w("ChatFragment", "Message with ID $messageId not found for like toggle.")
        }
    }

    /**
     * Initialize or get existing chat room
     */
    private fun initializeChatRoom() {
        val userId = currentUserId ?: return
        val partnerId = otherUserId ?: return
        
        lifecycleScope.launch {
            try {
                val chat = chatRepository.createOrGetChatRoom(userId, partnerId)
                if (chat != null) {
                    currentChat = chat
                    chatRoomId = chat.chatId
                    Log.d("ChatFragment", "Chat room initialized: ${chat.chatId}")
                    
                    // Now that chat room is initialized, load history and subscribe to realtime messages
                    loadChatHistory()
                    subscribeToRealtimeMessages()
                } else {
                    Log.e("ChatFragment", "Failed to initialize chat room")
                }
            } catch (e: Exception) {
                Log.e("ChatFragment", "Error initializing chat room", e)
            }
        }
    }
    
    /**
     * Load chat history from Supabase
     */
    private fun loadChatHistory() {
        if (chatRoomId == -1) {
            Log.w("ChatFragment", "Chat room not initialized, cannot load history")
            return
        }
        
        lifecycleScope.launch {
            try {
                val chatMessages = chatRepository.getChatMessages(chatRoomId)
                val localMessages = chatMessages.map { it.toLocalMessage() }
                
                messagesList.clear()
                messagesList.addAll(localMessages)
                chatAdapter.notifyDataSetChanged()
                
                if (messagesList.isNotEmpty()) {
                    binding.recyclerViewChat.scrollToPosition(chatAdapter.itemCount - 1)
                }
                
                Log.d("ChatFragment", "Loaded ${messagesList.size} messages")
                
            } catch (e: Exception) {
                Log.e("ChatFragment", "Error loading chat history", e)
            }
        }
    }
    
    /**
     * Subscribe to real-time message updates
     */
    private fun subscribeToRealtimeMessages() {
        if (chatRoomId == -1) {
            Log.w("ChatFragment", "Chat room not initialized, cannot subscribe to messages")
            return
        }
        
        lifecycleScope.launch {
            try {
                val messageFlow = chatRepository.subscribeToNewMessages(chatRoomId)
                messageFlow
                    .catch { error ->
                        Log.e("ChatFragment", "Error in realtime subscription", error)
                    }
                    .collect { chatMessage ->
                        val localMessage = chatMessage.toLocalMessage()
                        
                        // Only add if not already in list (avoid duplicates)
                        if (messagesList.none { it.messageId == localMessage.messageId }) {
                            messagesList.add(localMessage)
                            chatAdapter.notifyItemInserted(messagesList.size - 1)
                            binding.recyclerViewChat.smoothScrollToPosition(chatAdapter.itemCount - 1)
                            
                            Log.d("ChatFragment", "Received real-time message: ${chatMessage.messageId}")
                        }
                    }
            } catch (e: Exception) {
                Log.e("ChatFragment", "Failed to establish realtime subscription", e)
            }
        }
    }
    
    /**
     * Convert ChatMessage (Supabase) to Message (local)
     */
    private fun ChatMessage.toLocalMessage(): Message {
        val timestampMs = try {
            Instant.parse(this.timestamp).toEpochMilli()
        } catch (e: DateTimeParseException) {
            System.currentTimeMillis()
        }
        
        return Message(
            messageId = this.messageId.toString(),
            senderId = this.senderId,
            text = this.message,
            timestamp = timestampMs,
            chatRoomId = this.chatId.toString()
        )
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
