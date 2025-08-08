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
import com.example.lovebug_project.data.repository.SupabaseUserRepository
import com.example.lovebug_project.data.supabase.models.Chat
import com.example.lovebug_project.data.supabase.models.ChatMessage
import com.example.lovebug_project.databinding.FragmentChatBinding
import com.example.lovebug_project.utils.AuthHelper
import com.example.lovebug_project.utils.hideKeyboard
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.format.DateTimeParseException
import java.util.UUID
import kotlin.math.pow
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

/**
 * Retry information for failed messages
 */
data class RetryInfo(
    val messageId: String,
    val originalText: String,
    val retryCount: Int = 0,
    val lastRetryTime: Long = System.currentTimeMillis()
) {
    companion object {
        const val MAX_RETRIES = 3
        const val BASE_BACKOFF_DELAY = 1000L // 1 second
    }
    
    fun shouldRetry(): Boolean = retryCount < MAX_RETRIES
    
    fun getNextBackoffDelay(): Long {
        return BASE_BACKOFF_DELAY * 2.0.pow(retryCount.toDouble()).toLong()
            .coerceAtMost(30000L) // Max 30 seconds
    }
}

class ChatFragment : Fragment() {

    private var _binding: FragmentChatBinding? = null
    private val binding get() = _binding!!

    private lateinit var chatAdapter: ChatAdapter
    private val messagesList = mutableListOf<Message>()
    private var currentUserId: String? = null
    private var chatRoomId: Int = -1
    private var otherUserId: String? = null
    
    private val chatRepository = SupabaseChatRepository()
    private val userRepository = SupabaseUserRepository()
    private var currentChat: Chat? = null
    
    // User nickname cache to avoid repeated API calls
    private val nicknameCache = mutableMapOf<String, String>()
    
    // Optimistic UI를 위한 임시 메시지 관리
    private val tempMessages = mutableMapOf<String, Message>()
    
    // 메시지 중복 방지를 위한 Set
    private val processedMessageIds = mutableSetOf<String>()
    
    // 재시도 관련 변수들
    private val retryQueue = mutableMapOf<String, RetryInfo>()
    private var isRetryingMessages = false
    
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
            },
            onRetryMessage = { messageId -> // 재시도 콜백 추가
                Log.d("ChatFragment", "Retry requested for message: $messageId")
                handleRetryMessage(messageId)
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
     * Send message with Optimistic UI
     * 1. 즉시 UI에 메시지 표시 (pending 상태)
     * 2. 서버에 전송
     * 3. 성공/실패에 따라 UI 업데이트
     */
    private fun sendMessage(text: String) {
        val userId = currentUserId ?: run {
            Log.e("ChatFragment", "❌ Cannot send message - currentUserId is null")
            return
        }
        
        if (text.isBlank()) {
            Log.w("ChatFragment", "Cannot send empty message")
            return
        }
        
        Log.d("ChatFragment", "📤 ==> sendMessage() called with Optimistic UI")
        
        // 1. 임시 메시지 생성 및 즉시 UI 업데이트
        val tempId = "temp_${System.currentTimeMillis()}_${UUID.randomUUID()}"
        val currentTime = System.currentTimeMillis()
        
        val tempMessage = Message(
            messageId = tempId,
            senderId = userId,
            senderName = "나", // 나중에 실제 사용자 닉네임으로 대체
            senderProfileImageUrl = "",
            text = text,
            timestamp = currentTime,
            chatRoomId = chatRoomId.toString(),
            isPending = true,
            isTemporary = true,
            localId = tempId
        )
        
        // 즉시 UI에 추가
        addMessageToUIImmediately(tempMessage)
        
        // 2. 서버에 전송
        lifecycleScope.launch {
            try {
                Log.d("ChatFragment", "🚀 Sending message to server...")
                
                val sentMessage = chatRepository.sendMessage(chatRoomId, userId, text)
                
                if (sentMessage != null) {
                    Log.d("ChatFragment", "✅ Message sent successfully!")
                    // 성공: 재시도 큐에서 제거 (있는 경우)
                    removeFromRetryQueue(tempId)
                    // Realtime에서 오는 메시지를 위해 여기서는 직접 대체하지 않음
                    
                } else {
                    Log.e("ChatFragment", "❌ Failed to send message")
                    // 실패: 임시 메시지를 실패 상태로 변경하고 재시도 플랬그 설정
                    markMessageAsFailedWithRetry(tempId, text, Exception("Send message returned null"))
                }
                
            } catch (e: Exception) {
                Log.e("ChatFragment", "❌ Error sending message", e)
                // 실패: 임시 메시지를 실패 상태로 변경하고 재시도 플랬그 설정
                markMessageAsFailedWithRetry(tempId, text, e)
            }
        }
    }
    
    /**
     * 강화된 Optimistic UI 메시지 추가
     * Thread-safe 및 중복 방지 강화
     */
    private fun addMessageToUIImmediately(message: Message) {
        Log.d("ChatFragment", "📱 Adding message to UI immediately: ${message.text}")
        
        synchronized(this) {
            // 중복 체크: 이미 존재하는지 확인
            if (messagesList.any { it.messageId == message.messageId }) {
                Log.w("ChatFragment", "⚠️ Message already exists, skipping: ${message.messageId}")
                return
            }
            
            // 임시 메시지 기록
            if (message.isTemporary) {
                tempMessages[message.messageId] = message
                Log.d("ChatFragment", "📝 Temporary message registered: ${message.messageId}")
            }
            
            // UI 업데이트
            messagesList.add(message)
            
            // processedMessageIds에 추가 (중복 방지)
            if (!message.isTemporary) {
                processedMessageIds.add(message.messageId)
            }
        }
        
        // 메인 스레드에서 UI 업데이트
        binding.recyclerViewChat.post {
            chatAdapter.notifyItemInserted(messagesList.size - 1)
            binding.recyclerViewChat.smoothScrollToPosition(chatAdapter.itemCount - 1)
        }
        
        Log.d("ChatFragment", "✅ Message added to UI. Total messages: ${messagesList.size}")
    }
    
    /**
     * 임시 메시지를 실패 상태로 마크 (기존 메소드 유지)
     */
    private fun markMessageAsFailed(tempId: String) {
        Log.d("ChatFragment", "❌ Marking message as failed: $tempId")
        
        val tempMessage = tempMessages[tempId] ?: return
        val index = messagesList.indexOfFirst { it.messageId == tempId }
        
        if (index != -1) {
            val failedMessage = tempMessage.toFailedMessage()
            messagesList[index] = failedMessage
            tempMessages[tempId] = failedMessage
            
            binding.recyclerViewChat.post {
                chatAdapter.notifyItemChanged(index)
            }
        }
    }
    
    /**
     * 에러 처리와 재시도 로직을 포함한 실패 메시지 처리
     */
    private fun markMessageAsFailedWithRetry(tempId: String, originalText: String, error: Exception) {
        Log.e("ChatFragment", "❌ Message failed with retry setup: $tempId", error)
        
        // 기존 실패 처리 수행
        markMessageAsFailed(tempId)
        
        // 에러 타입 분석 및 재시도 전략 결정
        val retryStrategy = analyzeErrorAndGetRetryStrategy(error)
        
        if (retryStrategy.shouldRetry) {
            Log.d("ChatFragment", "🔄 Adding message to retry queue: $tempId")
            
            val existingRetry = retryQueue[tempId]
            val newRetryInfo = if (existingRetry != null) {
                existingRetry.copy(
                    retryCount = existingRetry.retryCount + 1,
                    lastRetryTime = System.currentTimeMillis()
                )
            } else {
                RetryInfo(
                    messageId = tempId,
                    originalText = originalText,
                    retryCount = 0,
                    lastRetryTime = System.currentTimeMillis()
                )
            }
            
            retryQueue[tempId] = newRetryInfo
            
            // 자동 재시도 스케줄링
            if (retryStrategy.autoRetry) {
                scheduleAutoRetry(newRetryInfo)
            }
        } else {
            Log.w("ChatFragment", "⚠️ Message not eligible for retry: $tempId (${retryStrategy.reason})")
        }
    }
    
    /**
     * 에러 분석 및 재시도 전략 결정
     */
    private fun analyzeErrorAndGetRetryStrategy(error: Exception): RetryStrategy {
        return when {
            // 네트워크 오류: 자동 재시도
            error is java.net.SocketTimeoutException || 
            error is java.net.UnknownHostException ||
            error.message?.contains("network", true) == true -> {
                RetryStrategy(true, true, "Network error - will auto retry")
            }
            
            // 서버 오류 (5xx): 자동 재시도
            error.message?.contains("5") == true -> {
                RetryStrategy(true, true, "Server error - will auto retry")
            }
            
            // 인증 오류 (401, 403): 수동 재시도만 허용
            error.message?.contains("401") == true || 
            error.message?.contains("403") == true -> {
                RetryStrategy(true, false, "Authentication error - manual retry only")
            }
            
            // 기타 오류: 수동 재시도 가능
            else -> {
                RetryStrategy(true, false, "General error - manual retry available")
            }
        }
    }
    
    /**
     * 재시도 전략 데이터 클래스
     */
    private data class RetryStrategy(
        val shouldRetry: Boolean,
        val autoRetry: Boolean,
        val reason: String
    )
    
    /**
     * 자동 재시도 스케줄링 (Exponential Backoff)
     */
    private fun scheduleAutoRetry(retryInfo: RetryInfo) {
        if (!retryInfo.shouldRetry()) {
            Log.w("ChatFragment", "⚠️ Auto retry not allowed: ${retryInfo.messageId}")
            return
        }
        
        val delay = retryInfo.getNextBackoffDelay()
        Log.d("ChatFragment", "⏰ Scheduling auto retry for ${retryInfo.messageId} in ${delay}ms")
        
        lifecycleScope.launch {
            kotlinx.coroutines.delay(delay)
            
            if (lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)) {
                executeRetry(retryInfo.messageId)
            } else {
                Log.d("ChatFragment", "⚠️ Fragment not active, skipping auto retry: ${retryInfo.messageId}")
            }
        }
    }
    
    /**
     * 사용자 요청 재시도 처리
     */
    private fun handleRetryMessage(messageId: String) {
        Log.d("ChatFragment", "🔄 Manual retry requested: $messageId")
        executeRetry(messageId)
    }
    
    /**
     * 실제 재시도 실행
     */
    private fun executeRetry(messageId: String) {
        val retryInfo = retryQueue[messageId]
        if (retryInfo == null) {
            Log.w("ChatFragment", "⚠️ No retry info found for message: $messageId")
            return
        }
        
        if (!retryInfo.shouldRetry()) {
            Log.w("ChatFragment", "⚠️ Retry not allowed (max attempts reached): $messageId")
            retryQueue.remove(messageId)
            return
        }
        
        Log.d("ChatFragment", "🔄 Executing retry ${retryInfo.retryCount + 1}/3 for: $messageId")
        
        // UI에서 실패 상태를 전송 중으로 변경
        updateMessageToPending(messageId)
        
        lifecycleScope.launch {
            try {
                val sentMessage = chatRepository.sendMessage(chatRoomId, currentUserId!!, retryInfo.originalText)
                
                if (sentMessage != null) {
                    Log.d("ChatFragment", "✅ Retry successful for: $messageId")
                    removeFromRetryQueue(messageId)
                    // Realtime으로 메시지 업데이트가 올 예정
                } else {
                    Log.e("ChatFragment", "❌ Retry failed for: $messageId")
                    markMessageAsFailedWithRetry(messageId, retryInfo.originalText, Exception("Retry attempt failed"))
                }
                
            } catch (e: Exception) {
                Log.e("ChatFragment", "❌ Retry exception for: $messageId", e)
                markMessageAsFailedWithRetry(messageId, retryInfo.originalText, e)
            }
        }
    }
    
    /**
     * 메시지를 전송 중 상태로 업데이트
     */
    private fun updateMessageToPending(messageId: String) {
        val index = messagesList.indexOfFirst { it.messageId == messageId }
        if (index != -1) {
            val currentMessage = messagesList[index]
            val pendingMessage = currentMessage.copy(
                isPending = true,
                isFailed = false,
                retryCount = currentMessage.retryCount + 1
            )
            
            messagesList[index] = pendingMessage
            tempMessages[messageId] = pendingMessage
            
            binding.recyclerViewChat.post {
                chatAdapter.notifyItemChanged(index)
            }
            
            Log.d("ChatFragment", "🔄 Message updated to pending: $messageId")
        }
    }
    
    /**
     * 재시도 큐에서 제거
     */
    private fun removeFromRetryQueue(messageId: String) {
        retryQueue.remove(messageId)?.let {
            Log.d("ChatFragment", "✅ Removed from retry queue: $messageId")
        }
    }
    
    /**
     * 모든 재시도 대기 중인 메시지 일괄 재시도
     */
    private fun retryAllFailedMessages() {
        if (isRetryingMessages) {
            Log.d("ChatFragment", "⚠️ Batch retry already in progress")
            return
        }
        
        val failedMessageIds = retryQueue.keys.toList()
        if (failedMessageIds.isEmpty()) {
            Log.d("ChatFragment", "ℹ️ No messages to retry")
            return
        }
        
        Log.d("ChatFragment", "🔄 Starting batch retry for ${failedMessageIds.size} messages")
        isRetryingMessages = true
        
        lifecycleScope.launch {
            for (messageId in failedMessageIds) {
                executeRetry(messageId)
                kotlinx.coroutines.delay(500) // 0.5초 간격으로 재시도
            }
            isRetryingMessages = false
            Log.d("ChatFragment", "✅ Batch retry completed")
        }
    }
    
    // ===========================================
    // 연결 오류 처리 및 복구 로직
    // ===========================================
    
    private var connectionRetryCount = 0
    private var isReconnecting = false
    
    /**
     * Realtime 연결 오류 처리
     */
    private fun handleConnectionError(error: Exception) {
        Log.e("ChatFragment", "🔌 Connection error occurred", error)
        
        // 연결 상태 초기화
        messageChannel = null
        presenceChannel = null
        
        // 에러 타입에 따른 복구 전략 결정
        val shouldAttemptReconnection = when {
            error is java.net.SocketTimeoutException -> true
            error is java.net.UnknownHostException -> true
            error.message?.contains("network", true) == true -> true
            error.message?.contains("timeout", true) == true -> true
            connectionRetryCount < 3 -> true
            else -> false
        }
        
        if (shouldAttemptReconnection && !isReconnecting) {
            Log.d("ChatFragment", "🔄 Attempting connection recovery...")
            attemptConnectionRecovery()
        } else {
            Log.w("ChatFragment", "⚠️ Connection recovery not attempted (retryCount: $connectionRetryCount, isReconnecting: $isReconnecting)")
            
            // 사용자에게 오프라인 상태 표시 (필요시 구현)
            showConnectionErrorState()
        }
    }
    
    /**
     * 연결 복구 시도
     */
    private fun attemptConnectionRecovery() {
        if (isReconnecting) {
            Log.d("ChatFragment", "⚠️ Reconnection already in progress")
            return
        }
        
        isReconnecting = true
        connectionRetryCount++
        
        val backoffDelay = (1000L * 2.0.pow(connectionRetryCount.toDouble())).toLong()
            .coerceAtMost(30000L) // 최대 30초
        
        Log.d("ChatFragment", "🔄 Connection recovery attempt $connectionRetryCount in ${backoffDelay}ms")
        
        lifecycleScope.launch {
            try {
                kotlinx.coroutines.delay(backoffDelay)
                
                // Fragment 상태 확인
                if (!lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)) {
                    Log.d("ChatFragment", "⚠️ Fragment not active, skipping reconnection")
                    isReconnecting = false
                    return@launch
                }
                
                // 재연결 시도
                Log.d("ChatFragment", "🔄 Executing reconnection...")
                connectRealtime()
                
                // 성공시 상태 초기화
                connectionRetryCount = 0
                isReconnecting = false
                
                // 실패한 메시지 일괄 재시도
                if (retryQueue.isNotEmpty()) {
                    Log.d("ChatFragment", "🔄 Connection recovered, retrying failed messages")
                    retryAllFailedMessages()
                }
                
                Log.d("ChatFragment", "✅ Connection recovery successful")
                
            } catch (e: Exception) {
                Log.e("ChatFragment", "❌ Connection recovery failed", e)
                isReconnecting = false
                
                // 재귀적 재시도 (최대 3회)
                if (connectionRetryCount < 3) {
                    handleConnectionError(e)
                } else {
                    Log.e("ChatFragment", "❌ Max connection retry attempts reached")
                    showConnectionErrorState()
                }
            }
        }
    }
    
    /**
     * 연결 오류 상태 표시
     */
    private fun showConnectionErrorState() {
        Log.w("ChatFragment", "⚠️ Showing connection error state to user")
        // TODO: UI에서 연결 오류 표시 및 수동 재연결 버튼 제공
        // 예: Toast 메시지, Snackbar, 또는 전용 오류 UI 요소
    }
    
    /**
     * 수동 연결 복구 요청 (사용자 버튼 클릭 시)
     */
    private fun manualReconnect() {
        Log.d("ChatFragment", "🔄 Manual reconnection requested by user")
        connectionRetryCount = 0 // 수동 재연결시 카운터 리셋
        isReconnecting = false
        attemptConnectionRecovery()
    }
    
    /**
     * 강화된 임시 메시지 대체 로직
     * Thread-safe 및 에러 처리 강화
     */
    private suspend fun replaceTemporaryMessage(tempId: String, realMessage: ChatMessage) {
        Log.d("ChatFragment", "🔄 Replacing temporary message: $tempId -> ${realMessage.messageId}")
        
        synchronized(tempMessages) {
            val tempMessage = tempMessages[tempId]
            if (tempMessage == null) {
                Log.w("ChatFragment", "⚠️ Temporary message not found: $tempId")
                return
            }
            
            val index = messagesList.indexOfFirst { it.messageId == tempId }
            if (index == -1) {
                Log.w("ChatFragment", "⚠️ Temporary message not found in messagesList: $tempId")
                tempMessages.remove(tempId) // 정리
                return
            }
            
            // 이미 대체된 메시지인지 체크
            if (tempMessage.isCompleted()) {
                Log.d("ChatFragment", "Message already completed: $tempId")
                return
            }
            
            val completedMessage = tempMessage.toCompletedMessage(
                realMessage.messageId.toString(),
                parseTimestamp(realMessage.timestamp)
            )
            
            messagesList[index] = completedMessage
            tempMessages.remove(tempId)
            
            // UI 업데이트
            binding.recyclerViewChat.post {
                chatAdapter.notifyItemChanged(index)
            }
            
            Log.d("ChatFragment", "✅ Message replaced successfully: $tempId -> ${realMessage.messageId}")
        }
    }
    
    /**
     * Timestamp 파싱 유틸리티
     */
    private fun parseTimestamp(timestamp: String): Long {
        return try {
            java.time.Instant.parse(timestamp).toEpochMilli()
        } catch (e: Exception) {
            System.currentTimeMillis()
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
                val localMessages = mutableListOf<Message>()
                for (chatMessage in chatMessages) {
                    localMessages.add(chatMessage.toLocalMessage())
                }
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
                
                // 실시간 메시지 수신 (개선된 버전)
                messageFlow.collect { chatMessage ->
                    messageCount++
                    Log.d("ChatFragment", "🔔 [$messageCount] NEW MESSAGE RECEIVED via Realtime!")
                    Log.d("ChatFragment", "Message ID: ${chatMessage.messageId}")
                    Log.d("ChatFragment", "From: ${chatMessage.senderId} (current user: $currentUserId)")
                    Log.d("ChatFragment", "Content: '${chatMessage.message}'")
                    
                    // Fragment view 상태 확인
                    if (_binding == null) {
                        Log.w("ChatFragment", "⚠️ Fragment view destroyed, ignoring message")
                        return@collect
                    }
                    
                    // 개선된 메시지 처리
                    processRealtimeMessage(chatMessage)
                }
            } catch (e: Exception) {
                Log.e("ChatFragment", "❌ ERROR in connectRealtime() coroutine", e)
                Log.e("ChatFragment", "Error type: ${e::class.simpleName}")
                Log.e("ChatFragment", "Exception details: ${e.message}")
                Log.e("ChatFragment", "Cause: ${e.cause}")
                
                // 연결 오류 처리 및 복구 시도
                handleConnectionError(e)
            }
        }
        
        Log.d("ChatFragment", "🔄 <== connectRealtime() launched coroutine and returned ===")
    }
    
    /**
     * Realtime 메시지 처리 (강화된 중복 방지 버전)
     * 1. 다중 레벨 중복 체크
     * 2. 임시 메시지와 충돌 처리 강화
     * 3. 새 메시지 추가 및 동기화
     */
    private suspend fun processRealtimeMessage(chatMessage: ChatMessage) {
        val messageId = chatMessage.messageId.toString()
        
        Log.d("ChatFragment", "🔄 Processing Realtime message: $messageId")
        
        // 1. 다중 레벨 중복 체크 (synchronized 블록 안에서)
        val isDuplicate = synchronized(this) {
            isMessageAlreadyProcessed(chatMessage)
        }
        
        if (isDuplicate) {
            Log.d("ChatFragment", "⚠️ Message already processed: $messageId")
            return
        }
        
        // 2. 강화된 임시 메시지 처리 (synchronized 블록 안에서 매칭 확인)
        val matchingTempMessage = synchronized(this) {
            findMatchingTempMessage(chatMessage)
        }
        
        if (matchingTempMessage != null) {
            Log.d("ChatFragment", "🔄 Found matching temp message, replacing...")
            // suspend 함수는 동기화 블록 밖에서 실행
            replaceTemporaryMessage(matchingTempMessage.messageId, chatMessage)
            synchronized(this) {
                processedMessageIds.add(messageId)
            }
            return
        }
        
        // 3. 새 메시지 추가 (suspend 함수는 블록 밖에서 실행)
        val localMessage = chatMessage.toLocalMessage()
        
        synchronized(this) {
            Log.d("ChatFragment", "✅ Adding new Realtime message to UI")
            messagesList.add(localMessage)
            processedMessageIds.add(messageId)
            
            // 메모리 관리: processedMessageIds가 너무 커지면 정리
            if (processedMessageIds.size > 500) {
                cleanupProcessedMessageIds()
            }
        }
        
        // UI 업데이트 (동기화 블록 밖에서 실행)
        binding.recyclerViewChat.post {
            chatAdapter.notifyItemInserted(messagesList.size - 1)
            binding.recyclerViewChat.smoothScrollToPosition(chatAdapter.itemCount - 1)
        }
        
        Log.d("ChatFragment", "✅ Realtime message processed. Total: ${messagesList.size}")
    }
    
    /**
     * 다중 레벨 메시지 중복 체크
     * 1. processedMessageIds 체크
     * 2. 기존 messagesList 체크
     * 3. 내용과 시간 기반 중복 체크
     */
    private fun isMessageAlreadyProcessed(chatMessage: ChatMessage): Boolean {
        val messageId = chatMessage.messageId.toString()
        
        // Level 1: ID 기반 체크
        if (processedMessageIds.contains(messageId)) {
            Log.d("ChatFragment", "Level 1 duplicate: ID already processed")
            return true
        }
        
        // Level 2: messagesList에서 동일한 ID 체크
        if (messagesList.any { it.messageId == messageId }) {
            Log.d("ChatFragment", "Level 2 duplicate: ID exists in messagesList")
            processedMessageIds.add(messageId) // 누락된 ID 추가
            return true
        }
        
        // Level 3: 내용과 시간 기반 중복 체크 (네트워크 지연으로 인한 중복 방지)
        val messageTimestamp = parseTimestamp(chatMessage.timestamp)
        val duplicateByContent = messagesList.any { existingMessage ->
            existingMessage.senderId == chatMessage.senderId &&
            existingMessage.text == chatMessage.message &&
            Math.abs(existingMessage.timestamp - messageTimestamp) < 5000 // 5초 이내
        }
        
        if (duplicateByContent) {
            Log.d("ChatFragment", "Level 3 duplicate: Content and time match found")
            processedMessageIds.add(messageId) // ID 기록
            return true
        }
        
        return false
    }
    
    /**
     * processedMessageIds 메모리 정리
     */
    private fun cleanupProcessedMessageIds() {
        Log.d("ChatFragment", "🧹 Cleaning up processedMessageIds (${processedMessageIds.size} entries)")
        
        // 현재 messagesList에 있는 ID들만 유지
        val currentMessageIds = messagesList.map { it.messageId }.toSet()
        processedMessageIds.retainAll(currentMessageIds)
        
        Log.d("ChatFragment", "✅ Cleaned up processedMessageIds (${processedMessageIds.size} entries remaining)")
    }
    
    /**
     * 강화된 임시 메시지 매칭 로직
     * 다중 조건으로 정확한 매칭 보장
     */
    private fun findMatchingTempMessage(chatMessage: ChatMessage): Message? {
        val messageTimestamp = parseTimestamp(chatMessage.timestamp)
        
        return tempMessages.values.find { tempMessage ->
            // 기본 조건: 내용, 전송자, 임시 상태 체크
            tempMessage.text.trim() == chatMessage.message.trim() &&
            tempMessage.senderId == chatMessage.senderId &&
            tempMessage.isTemporary &&
            // 시간 매칭: 더 엄격한 시간 기준 (5초 이내)
            Math.abs(tempMessage.timestamp - messageTimestamp) < 5000 &&
            // 추가 안전 장치: 이미 완료된 메시지가 아닌지 체크
            !tempMessage.isCompleted() &&
            // 채팅방 ID 매칭 (추가 보안)
            tempMessage.chatRoomId == chatMessage.chatId.toString()
        }
    }
    
    /**
     * Convert ChatMessage (Supabase) to Message (local)
     * Now loads real user nicknames from profiles table
     */
    private suspend fun ChatMessage.toLocalMessage(): Message {
        val timestampMs = parseTimestamp(this.timestamp)
        
        // 실제 사용자 닉네임 가져오기 (캐싱 사용)
        val senderName = getUserNickname(this.senderId)
        
        return Message(
            messageId = this.messageId.toString(),
            senderId = this.senderId,
            senderName = senderName,
            senderProfileImageUrl = "",
            text = this.message,
            timestamp = timestampMs,
            chatRoomId = this.chatId.toString(),
            serverTimestamp = timestampMs
        )
    }
    
    /**
     * Get user nickname with caching to avoid repeated API calls
     * @param userId The user ID to get nickname for
     * @return The user's nickname or a fallback name if not found
     */
    private suspend fun getUserNickname(userId: String): String {
        // Check cache first
        nicknameCache[userId]?.let { 
            Log.d("ChatFragment", "📦 Using cached nickname for user $userId: $it")
            return it 
        }
        
        // Load from database
        return try {
            Log.d("ChatFragment", "🔍 Loading nickname for user $userId from database...")
            val userProfile = userRepository.getUserProfile(userId)
            val nickname = userProfile?.nickname?.takeIf { it.isNotBlank() }
                ?: when (userId) {
                    currentUserId -> "나"
                    otherUserId -> "상대방"
                    else -> "사용자_${userId.take(6)}"
                }
            
            // Cache the result
            nicknameCache[userId] = nickname
            Log.d("ChatFragment", "✅ Loaded and cached nickname for user $userId: $nickname")
            nickname
            
        } catch (e: Exception) {
            Log.e("ChatFragment", "❌ Failed to load nickname for user $userId", e)
            // Fallback to default names
            val fallbackName = when (userId) {
                currentUserId -> "나"
                otherUserId -> "상대방"
                else -> "사용자_${userId.take(6)}"
            }
            // Cache the fallback to avoid repeated failures
            nicknameCache[userId] = fallbackName
            fallbackName
        }
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
        
        // Cleanup all channels and memory
        Log.d("ChatFragment", "🧹 Cleaning up all channels and memory...")
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
                
                // 메모리 정리: 모든 캐시 데이터 및 연결 상태 정리
                synchronized(this@ChatFragment) {
                    processedMessageIds.clear()
                    tempMessages.clear()
                    nicknameCache.clear()
                    retryQueue.clear()
                    isRetryingMessages = false
                    connectionRetryCount = 0
                    isReconnecting = false
                    Log.d("ChatFragment", "🧹 Cleared all cached data, retry queue, and connection state")
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

/**
 * Extension functions for Message class to support Optimistic UI states
 */
fun Message.toFailedMessage(): Message {
    return this.copy(
        isPending = false,
        isFailed = true,
        retryCount = this.retryCount + 1
    )
}

fun Message.toCompletedMessage(realMessageId: String, serverTimestamp: Long): Message {
    return this.copy(
        messageId = realMessageId,
        isPending = false,
        isFailed = false,
        isTemporary = false,
        serverTimestamp = serverTimestamp,
        timestamp = serverTimestamp
    )
}

fun Message.isCompleted(): Boolean {
    return !isTemporary && !isPending && !isFailed
}
