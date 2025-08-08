package com.example.lovebug_project.data.repository

import com.example.lovebug_project.data.supabase.SupabaseClient
import com.example.lovebug_project.data.supabase.models.Chat
import io.github.jan.supabase.auth.auth
import com.example.lovebug_project.data.supabase.models.ChatMessage
import com.example.lovebug_project.data.supabase.models.ChatRoomInfo
import com.example.lovebug_project.data.supabase.models.ChatUserSearchResult
import com.example.lovebug_project.data.supabase.models.PartnerProfile
import com.example.lovebug_project.data.supabase.models.UserProfile
import com.example.lovebug_project.utils.ErrorReporter
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import io.github.jan.supabase.realtime.RealtimeChannel
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.Json

/**
 * Repository for real-time chat operations using Supabase
 * Handles chat rooms, messages, and real-time subscriptions
 */
class SupabaseChatRepository {
    
    val supabase = SupabaseClient.client
    private val realtime = supabase.realtime
    
    /**
     * 개선된 Realtime 연결 관리
     * 실제 연결 상태를 확인하여 연결 성공 여부를 판단
     * @return true if connection successful, false otherwise
     */
    suspend fun ensureRealtimeConnection(): Boolean {
        return try {
            android.util.Log.d("SupabaseChatRepo", "🔌 Starting improved Realtime connection...")
            
            val currentStatus = realtime.status.toString()
            android.util.Log.d("SupabaseChatRepo", "Current status: $currentStatus")
            
            // 이미 연결되어 있으면 바로 반환
            if (currentStatus == "OPEN" || currentStatus == "CONNECTED") {
                android.util.Log.d("SupabaseChatRepo", "✅ Already connected")
                return true
            }
            
            // 연결 시도
            realtime.connect()
            
            // 실제 연결 상태를 확인하며 대기
            var waitTime = 0L
            val maxWaitTime = 15000L // 15초 최대 대기
            val checkInterval = 500L // 0.5초마다 체크
            
            while (waitTime < maxWaitTime) {
                val status = realtime.status.toString()
                android.util.Log.d("SupabaseChatRepo", "Checking status: $status (waited: ${waitTime}ms)")
                
                when (status) {
                    "OPEN", "CONNECTED" -> {
                        android.util.Log.d("SupabaseChatRepo", "✅ Connection established successfully!")
                        ErrorReporter.logSuccess("RealtimeConnection", "Connection established after ${waitTime}ms")
                        return true
                    }
                    "CLOSED", "FAILED" -> {
                        android.util.Log.e("SupabaseChatRepo", "❌ Connection failed with status: $status")
                        ErrorReporter.logSupabaseError("RealtimeConnection", 
                            Exception("Connection failed with status: $status"))
                        return false
                    }
                    "CONNECTING" -> {
                        // 연결 중이므로 계속 대기
                    }
                    else -> {
                        android.util.Log.w("SupabaseChatRepo", "Unknown status: $status")
                    }
                }
                
                kotlinx.coroutines.delay(checkInterval)
                waitTime += checkInterval
            }
            
            // 타임아웃
            val finalStatus = realtime.status.toString()
            android.util.Log.e("SupabaseChatRepo", "❌ Connection timeout after ${maxWaitTime}ms. Final status: $finalStatus")
            ErrorReporter.logSupabaseError("RealtimeConnection", 
                Exception("Connection timeout after ${maxWaitTime}ms. Status: $finalStatus"))
            false
            
        } catch (e: Exception) {
            android.util.Log.e("SupabaseChatRepo", "❌ Realtime connection failed", e)
            android.util.Log.e("SupabaseChatRepo", "Error details: ${e.message}")
            ErrorReporter.logSupabaseError("RealtimeConnection", e)
            false
        }
    }
    
    /**
     * Get current authenticated user ID
     * @return Current user ID or null if not authenticated
     */
    fun getCurrentUserId(): String? {
        return try {
            supabase.auth.currentUserOrNull()?.id
        } catch (e: Exception) {
            ErrorReporter.logSupabaseError("GetCurrentUserId", e)
            null
        }
    }
    
    /**
     * Create or get existing chat room between two users
     * @param user1Id First user ID 
     * @param user2Id Second user ID
     * @return Chat room or null if creation fails
     */
    suspend fun createOrGetChatRoom(user1Id: String, user2Id: String): Chat? {
        return try {
            // First check if chat room already exists (both directions)
            // Use explicit column selection to avoid RLS infinite recursion
            val existingChat = supabase.from("chats")
                .select(Columns.list("chat_id", "user1_id", "user2_id", "created_at", "updated_at")) {
                    filter {
                        or {
                            and {
                                eq("user1_id", user1Id)
                                eq("user2_id", user2Id)
                            }
                            and {
                                eq("user1_id", user2Id)
                                eq("user2_id", user1Id)
                            }
                        }
                    }
                    limit(1L)
                }
                .decodeSingleOrNull<Chat>()
            
            if (existingChat != null) {
                ErrorReporter.logSuccess("ChatRepository", "Found existing chat: ${existingChat.chatId}")
                return existingChat
            }
            
            // Create new chat room
            val newChat = supabase.from("chats")
                .insert(Chat(user1Id = user1Id, user2Id = user2Id)) {
                    select()
                }
                .decodeSingle<Chat>()
            
            ErrorReporter.logSuccess("ChatRepository", "Created new chat: ${newChat.chatId}")
            newChat
            
        } catch (e: Exception) {
            ErrorReporter.logSupabaseError("ChatRoomCreation", e,
                ErrorReporter.createContext("user1Id" to user1Id, "user2Id" to user2Id))
            null
        }
    }
    
    /**
     * Send a message to a chat room
     * @param chatId Chat room ID
     * @param senderId Sender user ID
     * @param message Message content
     * @return Sent message or null if failed
     */
    suspend fun sendMessage(chatId: Int, senderId: String, message: String): ChatMessage? {
        return try {
            android.util.Log.d("SupabaseChatRepo", "💬 ==> sendMessage() called")
            android.util.Log.d("SupabaseChatRepo", "ChatId: $chatId, SenderId: $senderId")
            android.util.Log.d("SupabaseChatRepo", "Message: '$message'")
            
            val timestamp = java.time.Instant.now().toString()
            android.util.Log.d("SupabaseChatRepo", "Generated timestamp: $timestamp")
            
            val chatMessage = ChatMessage(
                chatId = chatId,
                senderId = senderId,
                message = message,
                timestamp = timestamp
            )
            
            android.util.Log.d("SupabaseChatRepo", "Created ChatMessage object: $chatMessage")
            android.util.Log.d("SupabaseChatRepo", "Inserting message to Supabase...")
            
            val sentMessage = supabase.from("chat_messages")
                .insert(chatMessage) {
                    select()
                }
                .decodeSingle<ChatMessage>()
            
            android.util.Log.d("SupabaseChatRepo", "✅ Message inserted successfully!")
            android.util.Log.d("SupabaseChatRepo", "Returned message ID: ${sentMessage.messageId}")
            android.util.Log.d("SupabaseChatRepo", "Returned message: $sentMessage")
            android.util.Log.d("SupabaseChatRepo", "💬 <== sendMessage() completed successfully")
            
            ErrorReporter.logSuccess("ChatRepository", "Message sent: ${sentMessage.messageId}")
            sentMessage
            
        } catch (e: Exception) {
            android.util.Log.e("SupabaseChatRepo", "❌ sendMessage() failed", e)
            android.util.Log.e("SupabaseChatRepo", "Error type: ${e::class.simpleName}")
            android.util.Log.e("SupabaseChatRepo", "Error message: ${e.message}")
            ErrorReporter.logSupabaseError("SendMessage", e,
                ErrorReporter.createContext("chatId" to chatId, "senderId" to senderId))
            null
        }
    }
    
    /**
     * Get chat messages for a specific chat room
     * @param chatId Chat room ID
     * @param limit Number of messages to retrieve (default 50)
     * @return List of chat messages ordered by timestamp (oldest first)
     */
    suspend fun getChatMessages(chatId: Int, limit: Int = 50): List<ChatMessage> {
        return try {
            // Use explicit column selection to avoid potential RLS issues
            val messages = supabase.from("chat_messages")
                .select(Columns.list("message_id", "chat_id", "sender_id", "message", "timestamp")) {
                    filter { 
                        eq("chat_id", chatId)
                    }
                    limit(limit.toLong())
                }
                .decodeList<ChatMessage>()
            
            // Sort messages by timestamp ascending (oldest first) for natural chat order
            messages.sortedBy { message ->
                try {
                    java.time.Instant.parse(message.timestamp).toEpochMilli()
                } catch (e: Exception) {
                    0L // Fallback for invalid timestamps
                }
            }
                
        } catch (e: Exception) {
            ErrorReporter.logSupabaseError("GetChatMessages", e,
                ErrorReporter.createContext("chatId" to chatId))
            emptyList()
        }
    }
    
    /**
     * 개선된 버전: Subscribe to new messages in a specific chat room
     * 최신 Supabase Kotlin API 사용 및 더 안정적인 연결 관리
     * @param chatId Chat room ID to subscribe to
     * @return Pair of RealtimeChannel and Flow of new chat messages
     */
    suspend fun subscribeToNewMessages(chatId: Int): Pair<RealtimeChannel, Flow<ChatMessage>> {
        android.util.Log.d("SupabaseChatRepo", "📡 ==> subscribeToNewMessages() IMPROVED VERSION for chatId: $chatId")
        
        // Get current user info for debugging
        val currentUser = supabase.auth.currentUserOrNull()
        android.util.Log.d("SupabaseChatRepo", "Current user: ${currentUser?.id}")
        
        // Ensure realtime connection is established
        android.util.Log.d("SupabaseChatRepo", "Ensuring Realtime connection...")
        if (!ensureRealtimeConnection()) {
            android.util.Log.e("SupabaseChatRepo", "❌ Failed to establish realtime connection")
            throw Exception("Failed to establish realtime connection")
        }
        android.util.Log.d("SupabaseChatRepo", "✅ Realtime connection established")
        
        // Create unique channel using latest API
        val channelName = "chat_messages_$chatId"
        android.util.Log.d("SupabaseChatRepo", "Creating channel: $channelName")
        
        val channel = realtime.channel(channelName)
        android.util.Log.d("SupabaseChatRepo", "Channel created: $channel")
        
        // Create postgresChangeFlow with improved error handling
        val flow = channel.postgresChangeFlow<PostgresAction.Insert>(schema = "public") {
            table = "chat_messages"
            // 최신 버전에서는 클라이언트 사이드 필터링을 사용
            // 서버 사이드 필터링이 사용 가능한 경우 추용
        }
        
        android.util.Log.d("SupabaseChatRepo", "PostgresChangeFlow created")
        
        // Subscribe using latest API (no join needed)
        android.util.Log.d("SupabaseChatRepo", "Subscribing to channel...")
        channel.subscribe()
        
        // 개선된 대기 로직: 실제 상태 확인
        var waitTime = 0L
        val maxWaitTime = 10000L // 10초
        val checkInterval = 500L
        
        while (waitTime < maxWaitTime) {
            val status = channel.status.toString()
            android.util.Log.d("SupabaseChatRepo", "Channel status: $status (waited: ${waitTime}ms)")
            
            if (status == "SUBSCRIBED" || status == "JOINED") {
                android.util.Log.d("SupabaseChatRepo", "✅ Channel subscribed successfully!")
                break
            }
            
            kotlinx.coroutines.delay(checkInterval)
            waitTime += checkInterval
        }
        
        val finalStatus = channel.status.toString()
        android.util.Log.d("SupabaseChatRepo", "Final channel status: $finalStatus")
        
        ErrorReporter.logSuccess("RealtimeChannel", "Channel subscribed for chat: $chatId, status: $finalStatus")
        
        val messageFlow = createMessageFlow(channel, chatId, flow)
        
        android.util.Log.d("SupabaseChatRepo", "📡 <== subscribeToNewMessages() returning channel and flow")
        return Pair(channel, messageFlow)
    }
    
    
    // 중복 메시지 방지를 위한 Set
    private val processedMessageIds = mutableSetOf<String>()
    
    /**
     * 개선된 버전: Message flow 생성
     * 중복 방지, 에러 처리, 성능 최적화 포함
     */
    private fun createMessageFlow(channel: RealtimeChannel, chatId: Int, flow: Flow<PostgresAction.Insert>): Flow<ChatMessage> {
        return flow
            .onStart {
                android.util.Log.d("SupabaseChatRepo", "🔄 IMPROVED Message flow started for chat: $chatId")
                android.util.Log.d("SupabaseChatRepo", "Channel status: ${channel.status}")
            }
            .onEach { action ->
                android.util.Log.d("SupabaseChatRepo", "🔔 RAW ACTION: ${action::class.simpleName}")
            }
            .mapNotNull { action ->
                // 결합된 필터링과 매핑: null을 반환하면 자동으로 필터링됨
                try {
                    val message = Json.decodeFromJsonElement(ChatMessage.serializer(), action.record)
                    val messageId = message.messageId.toString()
                    
                    android.util.Log.d("SupabaseChatRepo", "Processing message: $messageId for chat: ${message.chatId}")
                    
                    // 1. Chat ID 체크
                    if (message.chatId != chatId) {
                        android.util.Log.d("SupabaseChatRepo", "⚠️ Wrong chat: ${message.chatId} != $chatId")
                        return@mapNotNull null
                    }
                    
                    // 2. 중복 체크
                    synchronized(processedMessageIds) {
                        if (processedMessageIds.contains(messageId)) {
                            android.util.Log.d("SupabaseChatRepo", "⚠️ Duplicate message: $messageId")
                            return@mapNotNull null
                        }
                        processedMessageIds.add(messageId)
                        
                        // 메모리 관리: 1000개 이상 저장되면 오래된 것 제거
                        if (processedMessageIds.size > 1000) {
                            val toRemove = processedMessageIds.take(200)
                            processedMessageIds.removeAll(toRemove.toSet())
                            android.util.Log.d("SupabaseChatRepo", "Cleaned up ${toRemove.size} old message IDs")
                        }
                    }
                    
                    android.util.Log.d("SupabaseChatRepo", "✅ Valid new message: $messageId")
                    ErrorReporter.logSuccess("RealtimeChatMessage", "New message: $messageId for chat: $chatId")
                    
                    message
                    
                } catch (e: Exception) {
                    android.util.Log.e("SupabaseChatRepo", "❌ Error processing message", e)
                    ErrorReporter.logSupabaseError("RealtimeMessageProcessing", e,
                        ErrorReporter.createContext("chatId" to chatId, "action" to "INSERT"))
                    null // 오류 발생 시 null 반환하여 해당 메시지 무시
                }
            }
            .catch { error ->
                android.util.Log.e("SupabaseChatRepo", "❌ Critical error in message flow", error as Throwable)
                ErrorReporter.logSupabaseError("RealtimeFlowError", error as Throwable,
                    ErrorReporter.createContext("chatId" to chatId))
                
                // 에러 시 빈 배열 전송하여 전체 플로우가 중단되지 않도록 함
                emitAll(emptyFlow<ChatMessage>())
            }
            .distinctUntilChanged { old, new ->
                // 추가 중복 방지: 같은 메시지 ID를 가진 동일한 메시지
                old.messageId == new.messageId
            }
    }
    
    /**
     * 중복 방지 상태 초기화 (테스트용)
     */
    fun clearProcessedMessageIds() {
        synchronized(processedMessageIds) {
            processedMessageIds.clear()
            android.util.Log.d("SupabaseChatRepo", "Processed message IDs cleared")
        }
    }

    
    /**
     * Subscribe to all message changes (insert, update, delete) in a chat room
     * @param chatId Chat room ID
     * @return Flow of chat message change events
     */
    suspend fun subscribeToAllMessageChanges(chatId: Int): Flow<ChatMessageChangeEvent> {
        // Ensure realtime connection is established
        if (!ensureRealtimeConnection()) {
            throw Exception("Failed to establish realtime connection")
        }
        
        val channel = realtime.channel("chat-all-changes-$chatId")
        val flow = channel.postgresChangeFlow<PostgresAction>(schema = "public") {
            table = "chat_messages"
        }
        
        // Channel will be automatically joined when collecting the flow
        
        return flow
            .filter { action ->
                // Filter for messages in this specific chat
                try {
                    when (action) {
                        is PostgresAction.Insert -> {
                            val message = Json.decodeFromJsonElement(ChatMessage.serializer(), action.record)
                            message.chatId == chatId
                        }
                        is PostgresAction.Update -> {
                            val message = Json.decodeFromJsonElement(ChatMessage.serializer(), action.record)
                            message.chatId == chatId
                        }
                        is PostgresAction.Delete -> {
                            val deletedMessage = Json.decodeFromJsonElement(ChatMessage.serializer(), action.oldRecord)
                            deletedMessage.chatId == chatId
                        }
                        else -> false
                    }
                } catch (e: Exception) {
                    false
                }
            }
            .map { action ->
                try {
                    when (action) {
                        is PostgresAction.Insert -> {
                            val message = Json.decodeFromJsonElement(ChatMessage.serializer(), action.record)
                            ChatMessageChangeEvent.Created(message)
                        }
                        is PostgresAction.Update -> {
                            val message = Json.decodeFromJsonElement(ChatMessage.serializer(), action.record)
                            ChatMessageChangeEvent.Updated(message)
                        }
                        is PostgresAction.Delete -> {
                            val deletedMessage = Json.decodeFromJsonElement(ChatMessage.serializer(), action.oldRecord)
                            ChatMessageChangeEvent.Deleted(deletedMessage.messageId)
                        }
                        else -> {
                            ChatMessageChangeEvent.Unknown
                        }
                    }
                } catch (e: Exception) {
                    ErrorReporter.logSupabaseError("RealtimeDecodeError", e,
                        ErrorReporter.createContext("action" to action::class.simpleName, "chatId" to chatId))
                    ChatMessageChangeEvent.Error(e)
                }
            }
            .catch { error ->
                ErrorReporter.logSupabaseError("RealtimeAllMessageChanges", error as Throwable)
                emit(ChatMessageChangeEvent.Error(error as Throwable))
            }
    }
    
    /**
     * Get user's chat rooms
     * @param userId User ID
     * @return List of chat rooms the user is part of
     */
    suspend fun getUserChats(userId: String): List<Chat> {
        return try {
            // Use explicit column selection to avoid RLS infinite recursion
            supabase.from("chats")
                .select(Columns.list("chat_id", "user1_id", "user2_id", "created_at", "updated_at")) {
                    filter {
                        or {
                            eq("user1_id", userId)
                            eq("user2_id", userId)
                        }
                    }
                }
                .decodeList<Chat>()
                
        } catch (e: Exception) {
            ErrorReporter.logSupabaseError("GetUserChats", e,
                ErrorReporter.createContext("userId" to userId))
            emptyList()
        }
    }
    
    /**
     * Update chat room's updated_at timestamp when a new message is sent
     * @param chatId Chat room ID
     */
    suspend fun updateChatTimestamp(chatId: Int) {
        try {
            supabase.from("chats")
                .update(mapOf("updated_at" to java.time.Instant.now().toString())) {
                    filter { 
                        eq("chat_id", chatId)
                    }
                }
            ErrorReporter.logSuccess("ChatRepository", "Updated chat timestamp: $chatId")
            
        } catch (e: Exception) {
            ErrorReporter.logSupabaseError("UpdateChatTimestamp", e,
                ErrorReporter.createContext("chatId" to chatId))
        }
    }
    
    /**
     * Start a chat with user by nickname
     * @param currentUserId Current user ID
     * @param targetNickname Target user's nickname
     * @return Chat room or null if failed
     */
    suspend fun startChatWithNickname(currentUserId: String, targetNickname: String): Chat? {
        return try {
            // First, find the user by nickname
            val targetUser = supabase.from("profiles")
                .select {
                    filter {
                        ilike("nickname", targetNickname.trim())
                    }
                    limit(1L)
                }
                .decodeSingleOrNull<UserProfile>()
            
            if (targetUser == null) {
                ErrorReporter.logSupabaseError("ChatRepository", 
                    Exception("User not found with nickname: $targetNickname"))
                return null
            }
            
            if (targetUser.id == currentUserId) {
                ErrorReporter.logSupabaseError("ChatRepository", 
                    Exception("Cannot start chat with yourself"))
                return null
            }
            
            // Create or get existing chat room
            createOrGetChatRoom(currentUserId, targetUser.id)
            
        } catch (e: Exception) {
            ErrorReporter.logSupabaseError("StartChatWithNickname", e,
                ErrorReporter.createContext("currentUserId" to currentUserId, "targetNickname" to targetNickname))
            null
        }
    }
    
    /**
     * Get chat rooms with participant information (including nicknames)
     * @param userId User ID
     * @return List of chat rooms with participant details
     */
    suspend fun getUserChatsWithParticipants(userId: String): List<ChatRoomInfo> {
        return try {
            // Use explicit column selection to avoid RLS infinite recursion
            val chats = supabase.from("chats")
                .select(Columns.list("chat_id", "user1_id", "user2_id", "created_at", "updated_at")) {
                    filter {
                        or {
                            eq("user1_id", userId)
                            eq("user2_id", userId)
                        }
                    }
                }
                .decodeList<Chat>()
            
            // Get participant information for each chat
            chats.mapNotNull { chat ->
                val partnerId = if (chat.user1Id == userId) chat.user2Id else chat.user1Id
                
                val partnerProfile = supabase.from("profiles")
                    .select(Columns.list("id", "nickname", "avatar_url")) {
                        filter {
                            eq("id", partnerId)
                        }
                    }
                    .decodeSingleOrNull<PartnerProfile>()
                
                if (partnerProfile != null) {
                    ChatRoomInfo(
                        chatId = chat.chatId,
                        partnerId = partnerId,
                        partnerNickname = partnerProfile.nickname,
                        partnerAvatarUrl = partnerProfile.avatarUrl,
                        updatedAt = chat.updatedAt ?: "",
                        createdAt = chat.createdAt ?: ""
                    )
                } else {
                    null
                }
            }
            
        } catch (e: Exception) {
            ErrorReporter.logSupabaseError("GetUserChatsWithParticipants", e,
                ErrorReporter.createContext("userId" to userId))
            emptyList()
        }
    }
    
    /**
     * Search for users by nickname for starting new chats
     * @param nickname Nickname to search for
     * @param currentUserId Current user ID (to exclude from results)
     * @param limit Maximum number of results
     * @return List of users matching the search
     */
    suspend fun searchUsersForChat(nickname: String, currentUserId: String, limit: Int = 10): List<ChatUserSearchResult> {
        return try {
            if (nickname.isBlank()) {
                return emptyList()
            }
            
            supabase.from("profiles")
                .select(Columns.list("id", "nickname", "avatar_url")) {
                    filter {
                        ilike("nickname", "%${nickname.trim()}%")
                        neq("id", currentUserId) // Exclude current user
                    }
                    limit(limit.toLong())
                }
                .decodeList<ChatUserSearchResult>()
                
        } catch (e: Exception) {
            ErrorReporter.logSupabaseError("SearchUsersForChat", e,
                ErrorReporter.createContext("nickname" to nickname, "currentUserId" to currentUserId))
            emptyList()
        }
    }
    
    /**
     * Get user's chat rooms with participant information and last message
     * @param userId User ID
     * @return List of chat rooms with complete information including last message
     */
    suspend fun getUserChatsWithLastMessage(userId: String): List<ChatRoomInfo> {
        return try {
            // Use explicit column selection to avoid RLS infinite recursion
            val chats = supabase.from("chats")
                .select(Columns.list("chat_id", "user1_id", "user2_id", "created_at", "updated_at")) {
                    filter {
                        or {
                            eq("user1_id", userId)
                            eq("user2_id", userId)
                        }
                    }
                }
                .decodeList<Chat>()
            
            // Sort chats by updated_at in descending order (most recent first)
            val sortedChats = chats.sortedByDescending { chat ->
                try {
                    java.time.Instant.parse(chat.updatedAt ?: chat.createdAt ?: "1970-01-01T00:00:00Z").toEpochMilli()
                } catch (e: Exception) {
                    0L // Fallback for invalid timestamps
                }
            }
            
            // Get participant information and last message for each chat
            sortedChats.mapNotNull { chat ->
                val partnerId = if (chat.user1Id == userId) chat.user2Id else chat.user1Id
                
                // Get partner profile
                val partnerProfile = supabase.from("profiles")
                    .select(Columns.list("id", "nickname", "avatar_url")) {
                        filter {
                            eq("id", partnerId)
                        }
                    }
                    .decodeSingleOrNull<PartnerProfile>()
                
                // Get last message for this chat (explicit column selection)
                val messages = supabase.from("chat_messages")
                    .select(Columns.list("message_id", "chat_id", "sender_id", "message", "timestamp")) {
                        filter {
                            eq("chat_id", chat.chatId)
                        }
                        limit(50L) // Limit to avoid loading too many messages
                    }
                    .decodeList<ChatMessage>()
                
                // Sort messages by timestamp and get the most recent one
                val lastMessage = messages
                    .sortedByDescending { message ->
                        try {
                            java.time.Instant.parse(message.timestamp).toEpochMilli()
                        } catch (e: Exception) {
                            0L // Fallback for invalid timestamps
                        }
                    }
                    .firstOrNull()
                
                if (partnerProfile != null) {
                    ChatRoomInfo(
                        chatId = chat.chatId,
                        partnerId = partnerId,
                        partnerNickname = partnerProfile.nickname,
                        partnerAvatarUrl = partnerProfile.avatarUrl,
                        updatedAt = chat.updatedAt ?: "",
                        createdAt = chat.createdAt ?: "",
                        lastMessage = lastMessage?.message ?: "채팅을 시작하세요",
                        lastMessageTimestamp = lastMessage?.timestamp
                    )
                } else {
                    null
                }
            }
            
        } catch (e: Exception) {
            // Special handling for RLS infinite recursion error
            if (e.message?.contains("infinite recursion detected") == true) {
                ErrorReporter.logSupabaseError("GetUserChatsWithLastMessage", 
                    Exception("RLS policy infinite recursion detected - using fallback approach", e),
                    ErrorReporter.createContext("userId" to userId, "fallback" to "simple_query"))
                
                // Fallback: Try a simpler approach without complex RLS queries
                try {
                    return getUserChatsWithParticipants(userId)
                } catch (fallbackException: Exception) {
                    ErrorReporter.logSupabaseError("GetUserChatsWithLastMessage_Fallback", fallbackException,
                        ErrorReporter.createContext("userId" to userId))
                    return emptyList()
                }
            }
            
            ErrorReporter.logSupabaseError("GetUserChatsWithLastMessage", e,
                ErrorReporter.createContext("userId" to userId))
            emptyList()
        }
    }
}

/**
 * Sealed class representing different types of chat message change events
 */
sealed class ChatMessageChangeEvent {
    data class Created(val message: ChatMessage) : ChatMessageChangeEvent()
    data class Updated(val message: ChatMessage) : ChatMessageChangeEvent()
    data class Deleted(val messageId: Int) : ChatMessageChangeEvent()
    data class Error(val exception: Throwable) : ChatMessageChangeEvent()
    object Unknown : ChatMessageChangeEvent()
}