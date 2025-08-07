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
import io.github.jan.supabase.realtime.RealtimeChannel
import io.github.jan.supabase.realtime.realtime
import io.github.jan.supabase.realtime.channel
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.Serializable

/**
 * Data class for presence tracking
 */
@Serializable
data class PresenceData(val userId: String, val username: String)

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
    
    // Realtime channels
    private var messageChannel: RealtimeChannel? = null
    private var presenceChannel: RealtimeChannel? = null
    private val onlineUsers = mutableSetOf<String>()
    private var isPartnerOnline = false

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
        // Note: loadChatHistory() and WebSocket connection will be established after chat room is initialized
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
            Log.d("ChatFragment", "Send button clicked. Message: '$messageText', userId: $currentUserId, chatRoomId: $chatRoomId")
            
            if (messageText.isNotEmpty() && currentUserId != null && chatRoomId != -1) {
                Log.d("ChatFragment", "Calling sendMessage with: '$messageText'")
                sendMessage(messageText)
                binding.editTextMessage.text.clear()
                binding.editTextMessage.hideKeyboard()
            } else {
                Log.w("ChatFragment", "Cannot send message: text empty or chat not initialized")
                Log.w("ChatFragment", "Debug - messageText.isNotEmpty(): ${messageText.isNotEmpty()}, currentUserId: $currentUserId, chatRoomId: $chatRoomId")
            }
        }
    }

    /**
     * Send message - Always saves to database for reliable background messaging
     * 실제 구현에서는 백그라운드에서도 채팅 메시지를 받을 수 있도록 하기 위해 항상 DB에 저장
     */
    private fun sendMessage(text: String) {
        val userId = currentUserId ?: run {
            Log.e("ChatFragment", "❌ Cannot send message - currentUserId is null")
            return
        }
        
        Log.d("ChatFragment", "📤 ==> sendMessage() called")
        Log.d("ChatFragment", "Text: '$text'")
        Log.d("ChatFragment", "UserId: $userId")
        Log.d("ChatFragment", "ChatRoomId: $chatRoomId")
        Log.d("ChatFragment", "Fragment lifecycle: ${lifecycle.currentState}")
        Log.d("ChatFragment", "View binding exists: ${_binding != null}")
        
        lifecycleScope.launch {
            try {
                Log.d("ChatFragment", "🚀 Starting sendMessage coroutine...")
                
                // 항상 DB에 저장하여 백그라운드에서도 메시지를 받을 수 있도록 함
                Log.d("ChatFragment", "📞 Calling chatRepository.sendMessage()")
                Log.d("ChatFragment", "Parameters - chatRoomId: $chatRoomId, userId: $userId, text: '$text'")
                val sentMessage = chatRepository.sendMessage(chatRoomId, userId, text)
                
                if (sentMessage != null) {
                    Log.d("ChatFragment", "✅ Message successfully sent to DB!")
                    Log.d("ChatFragment", "Sent message ID: ${sentMessage.messageId}")
                    Log.d("ChatFragment", "Sent message chatId: ${sentMessage.chatId}")
                    Log.d("ChatFragment", "Sent message senderId: ${sentMessage.senderId}")
                    Log.d("ChatFragment", "Sent message content: '${sentMessage.message}'")
                    Log.d("ChatFragment", "Sent message timestamp: ${sentMessage.timestamp}")
                    Log.d("ChatFragment", "📡 UI update will be handled by Realtime subscription")
                    Log.d("ChatFragment", "🔄 Waiting for Realtime to receive this message...")
                } else {
                    Log.e("ChatFragment", "❌ Failed to send message to DB - returned null")
                    // TODO: Show error to user
                }
                
                Log.d("ChatFragment", "📤 <== sendMessage() coroutine completed")
                
            } catch (e: Exception) {
                Log.e("ChatFragment", "❌ ERROR in sendMessage() coroutine", e)
                Log.e("ChatFragment", "Error type: ${e::class.simpleName}")
                Log.e("ChatFragment", "Error message: ${e.message}")
                Log.e("ChatFragment", "Error cause: ${e.cause}")
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
        val userId = currentUserId ?: run {
            Log.e("ChatFragment", "❌ Cannot initialize chat room - currentUserId is null")
            return
        }
        val partnerId = otherUserId ?: run {
            Log.e("ChatFragment", "❌ Cannot initialize chat room - otherUserId is null")
            return
        }
        
        Log.d("ChatFragment", "🏠 ==> initializeChatRoom() called")
        Log.d("ChatFragment", "Current user ID: $userId")
        Log.d("ChatFragment", "Partner user ID: $partnerId")
        
        lifecycleScope.launch {
            try {
                Log.d("ChatFragment", "🚀 Starting initializeChatRoom coroutine...")
                Log.d("ChatFragment", "📞 Calling chatRepository.createOrGetChatRoom()")
                
                val chat = chatRepository.createOrGetChatRoom(userId, partnerId)
                
                if (chat != null) {
                    currentChat = chat
                    chatRoomId = chat.chatId
                    
                    Log.d("ChatFragment", "✅ Chat room initialized successfully!")
                    Log.d("ChatFragment", "Chat ID: ${chat.chatId}")
                    Log.d("ChatFragment", "User1: ${chat.user1Id}")
                    Log.d("ChatFragment", "User2: ${chat.user2Id}")
                    Log.d("ChatFragment", "Created at: ${chat.createdAt}")
                    Log.d("ChatFragment", "Updated at: ${chat.updatedAt}")
                    
                    Log.d("ChatFragment", "🔄 Starting post-initialization tasks...")
                    
                    // Now that chat room is initialized, load history, connect Realtime, and setup presence
                    Log.d("ChatFragment", "1/3 Loading chat history...")
                    loadChatHistory()
                    
                    Log.d("ChatFragment", "2/3 Connecting to Realtime...")
                    connectRealtime()
                    
                    Log.d("ChatFragment", "3/3 Setting up presence tracking...")
                    setupPresenceTracking()
                    
                    Log.d("ChatFragment", "✅ All initialization tasks started")
                    
                } else {
                    Log.e("ChatFragment", "❌ Failed to initialize chat room - returned null")
                }
                
                Log.d("ChatFragment", "🏠 <== initializeChatRoom() coroutine completed")
                
            } catch (e: Exception) {
                Log.e("ChatFragment", "❌ ERROR in initializeChatRoom()", e)
                Log.e("ChatFragment", "Error type: ${e::class.simpleName}")
                Log.e("ChatFragment", "Error message: ${e.message}")
            }
        }
    }
    
    /**
     * Load chat history from Supabase
     */
    private fun loadChatHistory() {
        if (chatRoomId == -1) {
            Log.w("ChatFragment", "❌ Chat room not initialized, cannot load history")
            return
        }
        
        Log.d("ChatFragment", "📚 ==> loadChatHistory() called for chatRoomId: $chatRoomId")
        
        lifecycleScope.launch {
            try {
                Log.d("ChatFragment", "🚀 Starting loadChatHistory coroutine...")
                Log.d("ChatFragment", "📞 Calling chatRepository.getChatMessages($chatRoomId)")
                
                val chatMessages = chatRepository.getChatMessages(chatRoomId)
                Log.d("ChatFragment", "✅ Retrieved ${chatMessages.size} messages from repository")
                
                Log.d("ChatFragment", "🔄 Converting ChatMessages to local Messages...")
                val localMessages = chatMessages.map { it.toLocalMessage() }
                Log.d("ChatFragment", "✅ Converted ${localMessages.size} messages")
                
                Log.d("ChatFragment", "📝 Updating messages list...")
                Log.d("ChatFragment", "Messages list before: ${messagesList.size}")
                messagesList.clear()
                messagesList.addAll(localMessages)
                Log.d("ChatFragment", "Messages list after: ${messagesList.size}")
                
                Log.d("ChatFragment", "📱 Updating UI...")
                chatAdapter.notifyDataSetChanged()
                
                if (messagesList.isNotEmpty()) {
                    binding.recyclerViewChat.scrollToPosition(chatAdapter.itemCount - 1)
                    Log.d("ChatFragment", "📜 Scrolled to last message (position ${chatAdapter.itemCount - 1})")
                }
                
                Log.d("ChatFragment", "✅ Chat history loaded successfully: ${messagesList.size} messages")
                Log.d("ChatFragment", "📚 <== loadChatHistory() completed")
                
            } catch (e: Exception) {
                Log.e("ChatFragment", "❌ ERROR in loadChatHistory()", e)
                Log.e("ChatFragment", "Error type: ${e::class.simpleName}")
                Log.e("ChatFragment", "Error message: ${e.message}")
            }
        }
    }
    
    /**
     * Connect to Supabase Realtime for background-safe messaging
     * 백그라운드에서도 안전하게 메시지를 받을 수 있도록 Supabase Realtime 사용
     */
    private fun connectRealtime() {
        Log.d("ChatFragment", "🔄 ==> connectRealtime() CALLED ===")
        Log.d("ChatFragment", "Fragment lifecycle state: ${lifecycle.currentState}")
        Log.d("ChatFragment", "View created: ${_binding != null}")
        
        if (chatRoomId == -1 || currentUserId == null) {
            Log.w("ChatFragment", "❌ Cannot connect Realtime - missing requirements")
            Log.w("ChatFragment", "Debug - chatRoomId: $chatRoomId, currentUserId: $currentUserId")
            return
        }
        
        Log.d("ChatFragment", "✅ Prerequisites OK - chatRoomId: $chatRoomId, userId: $currentUserId")
        Log.d("ChatFragment", "Current messagesList size: ${messagesList.size}")
        Log.d("ChatFragment", "ChatRepository instance: ${chatRepository}")
        
        lifecycleScope.launch {
            try {
                Log.d("ChatFragment", "🚀 Starting Realtime subscription in coroutine...")
                Log.d("ChatFragment", "Coroutine context: ${coroutineContext}")
                
                // Supabase Realtime을 사용하여 새 메시지 구독
                Log.d("ChatFragment", "📞 Calling chatRepository.subscribeToNewMessages($chatRoomId)")
                val (channel, messageFlow) = chatRepository.subscribeToNewMessages(chatRoomId)
                
                // Store channel for cleanup
                messageChannel = channel
                
                Log.d("ChatFragment", "✅ Successfully got channel and flow from repository")
                Log.d("ChatFragment", "Channel: $channel")
                Log.d("ChatFragment", "Flow: $messageFlow")
                Log.d("ChatFragment", "🔄 Starting messageFlow.collect() - waiting for messages...")
                
                var messageCount = 0
                
                // 실시간 메시지 수신
                messageFlow.collect { chatMessage ->
                    messageCount++
                    Log.d("ChatFragment", "🔔 [$messageCount] NEW MESSAGE RECEIVED via Realtime!")
                    Log.d("ChatFragment", "Message ID: ${chatMessage.messageId}")
                    Log.d("ChatFragment", "From: ${chatMessage.senderId} (current user: $currentUserId)")
                    Log.d("ChatFragment", "Chat ID: ${chatMessage.chatId} (current chat: $chatRoomId)")
                    Log.d("ChatFragment", "Content: '${chatMessage.message}'")
                    Log.d("ChatFragment", "Timestamp: ${chatMessage.timestamp}")
                    
                    // Check if we're still in the right fragment
                    if (_binding == null) {
                        Log.w("ChatFragment", "⚠️ Fragment view destroyed, ignoring message")
                        return@collect
                    }
                    
                    // ChatMessage를 local Message로 변환
                    Log.d("ChatFragment", "🔄 Converting to local message...")
                    val localMessage = chatMessage.toLocalMessage()
                    Log.d("ChatFragment", "Local message ID: ${localMessage.messageId}")
                    Log.d("ChatFragment", "Local message senderId: ${localMessage.senderId}")
                    Log.d("ChatFragment", "Local message text: '${localMessage.text}'")
                    
                    // 중복 메시지 방지 (자신이 보낸 메시지 포함)
                    Log.d("ChatFragment", "🔍 Checking for duplicates in ${messagesList.size} existing messages...")
                    val existingMessage = messagesList.find { it.messageId == localMessage.messageId }
                    if (existingMessage == null) {
                        Log.d("ChatFragment", "✅ New message - adding to UI")
                        Log.d("ChatFragment", "Messages list before: ${messagesList.size}")
                        
                        messagesList.add(localMessage)
                        Log.d("ChatFragment", "Messages list after: ${messagesList.size}")
                        
                        // Update UI on main thread
                        binding.recyclerViewChat.post {
                            chatAdapter.notifyItemInserted(messagesList.size - 1)
                            binding.recyclerViewChat.smoothScrollToPosition(chatAdapter.itemCount - 1)
                            Log.d("ChatFragment", "📱 UI updated - adapter item count: ${chatAdapter.itemCount}")
                        }
                        
                        Log.d("ChatFragment", "✅ Added new message to UI: ${chatMessage.messageId}")
                        Log.d("ChatFragment", "Total messages in list: ${messagesList.size}")
                    } else {
                        Log.d("ChatFragment", "⚠️ Duplicate message ignored: ${chatMessage.messageId}")
                        Log.d("ChatFragment", "Existing message details: ${existingMessage}")
                    }
                }
            } catch (e: Exception) {
                Log.e("ChatFragment", "❌ ERROR in connectRealtime() coroutine", e)
                Log.e("ChatFragment", "Error type: ${e::class.simpleName}")
                Log.e("ChatFragment", "Exception details: ${e.message}")
                Log.e("ChatFragment", "Cause: ${e.cause}")
                Log.e("ChatFragment", "Stack trace:")
                e.stackTrace.forEach { element ->
                    Log.e("ChatFragment", "  at $element")
                }
                // TODO: Show error to user or fallback to polling
            }
        }
        
        Log.d("ChatFragment", "🔄 <== connectRealtime() launched coroutine and returned ===")
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

    /**
     * Setup presence tracking for the chat room
     */
    private fun setupPresenceTracking() {
        if (chatRoomId == -1 || currentUserId == null) {
            Log.w("ChatFragment", "❌ Cannot setup presence: chat room or user not initialized")
            Log.w("ChatFragment", "Debug - chatRoomId: $chatRoomId, currentUserId: $currentUserId")
            return
        }
        
        Log.d("ChatFragment", "👥 ==> setupPresenceTracking() called")
        Log.d("ChatFragment", "ChatRoomId: $chatRoomId, UserId: $currentUserId")
        
        lifecycleScope.launch {
            try {
                Log.d("ChatFragment", "🚀 Starting presence tracking coroutine...")
                
                // Use the same pattern as in SupabaseChatRepository - create channel using realtime
                val realtime = chatRepository.supabase.realtime
                Log.d("ChatFragment", "Got realtime instance: $realtime")
                Log.d("ChatFragment", "Realtime status: ${realtime.status}")
                
                val channelName = "chat-presence-$chatRoomId"
                Log.d("ChatFragment", "Creating presence channel: $channelName")
                presenceChannel = realtime.channel(channelName)
                
                Log.d("ChatFragment", "Presence channel created: ${presenceChannel}")
                Log.d("ChatFragment", "Presence channel status: ${presenceChannel?.status}")
                
                // Subscribe to the channel
                Log.d("ChatFragment", "📡 Subscribing to presence channel...")
                presenceChannel?.subscribe()
                
                // **FIX: Wait longer and verify subscription status**
                Log.d("ChatFragment", "⏳ Waiting for subscription to complete...")
                kotlinx.coroutines.delay(2000) // Increased delay
                Log.d("ChatFragment", "Presence channel status after subscribe: ${presenceChannel?.status}")
                
                // **FIX: Only track presence if channel is actually subscribed**
                if (presenceChannel?.status.toString() == "SUBSCRIBED") {
                    Log.d("ChatFragment", "👤 Channel subscribed - tracking user presence...")
                    val presenceData = buildJsonObject {
                        put("user_id", currentUserId!!)
                        put("username", "user-$currentUserId")
                        put("online_at", java.time.Instant.now().toString())
                    }
                    Log.d("ChatFragment", "Presence data: $presenceData")
                    
                    presenceChannel?.track(presenceData)
                    Log.d("ChatFragment", "✅ User presence tracked")
                } else {
                    Log.w("ChatFragment", "⚠️ Channel not subscribed yet, skipping presence tracking")
                    Log.w("ChatFragment", "Current status: ${presenceChannel?.status}")
                }
                
                // 간단한 presence 추적 - Supabase Realtime을 통해 관리됨
                // 실제 구현에서는 백그라운드에서도 채팅 메시지를 받을 수 있도록 하기 위해 항상 DB에 저장
                isPartnerOnline = true // Realtime 연결 시 온라인으로 가정
                Log.d("ChatFragment", "Partner assumed online (using Supabase Realtime)")
                
                Log.d("ChatFragment", "✅ Presence tracking setup completed successfully")
                Log.d("ChatFragment", "👥 <== setupPresenceTracking() completed")
                
            } catch (e: Exception) {
                Log.e("ChatFragment", "❌ ERROR in setupPresenceTracking()", e)
                Log.e("ChatFragment", "Error type: ${e::class.simpleName}")
                Log.e("ChatFragment", "Error message: ${e.message}")
            }
        }
    }
    
    override fun onDestroyView() {
        Log.d("ChatFragment", "🧹 ==> onDestroyView() called")
        Log.d("ChatFragment", "Fragment lifecycle: ${lifecycle.currentState}")
        Log.d("ChatFragment", "Current chatRoomId: $chatRoomId")
        Log.d("ChatFragment", "Current userId: $currentUserId")
        
        super.onDestroyView()
        
        // Cleanup all channels
        Log.d("ChatFragment", "🧹 Cleaning up all channels...")
        lifecycleScope.launch {
            try {
                // Cleanup message channel
                if (messageChannel != null) {
                    Log.d("ChatFragment", "📡 Unsubscribing from message channel...")
                    Log.d("ChatFragment", "Message channel status before cleanup: ${messageChannel?.status}")
                    messageChannel?.unsubscribe()
                    messageChannel = null
                    Log.d("ChatFragment", "✅ Message channel unsubscribed and cleared")
                } else {
                    Log.d("ChatFragment", "ℹ️ No message channel to cleanup")
                }
                
                // Cleanup presence channel
                if (presenceChannel != null) {
                    Log.d("ChatFragment", "📡 Unsubscribing from presence channel...")
                    Log.d("ChatFragment", "Presence channel status before cleanup: ${presenceChannel?.status}")
                    presenceChannel?.unsubscribe()
                    presenceChannel = null
                    Log.d("ChatFragment", "✅ Presence channel unsubscribed and cleared")
                } else {
                    Log.d("ChatFragment", "ℹ️ No presence channel to cleanup")
                }
            } catch (e: Exception) {
                Log.e("ChatFragment", "❌ Error cleaning up channels", e)
            }
        }
        
        Log.d("ChatFragment", "ℹ️ Realtime message connections will be cleaned up automatically by lifecycleScope")
        Log.d("ChatFragment", "Messages list size at cleanup: ${messagesList.size}")
        
        _binding = null
        Log.d("ChatFragment", "✅ View binding cleared")
        Log.d("ChatFragment", "🧹 <== onDestroyView() completed")
    }
}
