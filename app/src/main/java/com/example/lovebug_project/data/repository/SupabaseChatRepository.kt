package com.example.lovebug_project.data.repository

import com.example.lovebug_project.data.supabase.SupabaseClient
import com.example.lovebug_project.data.supabase.models.Chat
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
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.Json

/**
 * Repository for real-time chat operations using Supabase
 * Handles chat rooms, messages, and real-time subscriptions
 */
class SupabaseChatRepository {
    
    private val supabase = SupabaseClient.client
    private val realtime = supabase.realtime
    
    /**
     * Create or get existing chat room between two users
     * @param user1Id First user ID 
     * @param user2Id Second user ID
     * @return Chat room or null if creation fails
     */
    suspend fun createOrGetChatRoom(user1Id: String, user2Id: String): Chat? {
        return try {
            // First check if chat room already exists (both directions)
            val existingChat = supabase.from("chats")
                .select {
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
            val chatMessage = ChatMessage(
                chatId = chatId,
                senderId = senderId,
                message = message,
                timestamp = java.time.Instant.now().toString()
            )
            
            val sentMessage = supabase.from("chat_messages")
                .insert(chatMessage) {
                    select()
                }
                .decodeSingle<ChatMessage>()
            
            ErrorReporter.logSuccess("ChatRepository", "Message sent: ${sentMessage.messageId}")
            sentMessage
            
        } catch (e: Exception) {
            ErrorReporter.logSupabaseError("SendMessage", e,
                ErrorReporter.createContext("chatId" to chatId, "senderId" to senderId))
            null
        }
    }
    
    /**
     * Get chat messages for a specific chat room
     * @param chatId Chat room ID
     * @param limit Number of messages to retrieve (default 50)
     * @return List of chat messages
     */
    suspend fun getChatMessages(chatId: Int, limit: Int = 50): List<ChatMessage> {
        return try {
            supabase.from("chat_messages")
                .select {
                    filter { 
                        eq("chat_id", chatId)
                    }
                    limit(limit.toLong())
                }
                .decodeList<ChatMessage>()
                .reversed() // Show oldest first
                
        } catch (e: Exception) {
            ErrorReporter.logSupabaseError("GetChatMessages", e,
                ErrorReporter.createContext("chatId" to chatId))
            emptyList()
        }
    }
    
    /**
     * Subscribe to new messages in a specific chat room
     * @param chatId Chat room ID to subscribe to
     * @return Flow of new chat messages
     */
    fun subscribeToNewMessages(chatId: Int): Flow<ChatMessage> {
        return realtime.channel("chat-messages-$chatId")
            .postgresChangeFlow<PostgresAction.Insert>(schema = "public") {
                table = "chat_messages"
            }
            .filter { action ->
                // Filter for messages in this specific chat
                try {
                    val message = Json.decodeFromJsonElement(ChatMessage.serializer(), action.record)
                    message.chatId == chatId
                } catch (e: Exception) {
                    false
                }
            }
            .map { action ->
                try {
                    val message = Json.decodeFromJsonElement(ChatMessage.serializer(), action.record)
                    ErrorReporter.logSuccess("RealtimeChatMessage", "New message: ${message.messageId}")
                    message
                } catch (e: Exception) {
                    ErrorReporter.logSupabaseError("RealtimeDecodeError", e,
                        ErrorReporter.createContext("action" to "INSERT", "table" to "chat_messages", "chatId" to chatId))
                    throw e
                }
            }
            .catch { error ->
                ErrorReporter.logSupabaseError("RealtimeNewMessages", error as Throwable)
                throw error
            }
    }
    
    /**
     * Subscribe to all message changes (insert, update, delete) in a chat room
     * @param chatId Chat room ID
     * @return Flow of chat message change events
     */
    fun subscribeToAllMessageChanges(chatId: Int): Flow<ChatMessageChangeEvent> {
        return realtime.channel("chat-all-changes-$chatId")
            .postgresChangeFlow<PostgresAction>(schema = "public") {
                table = "chat_messages"
            }
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
            supabase.from("chats")
                .select {
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
            val chats = supabase.from("chats")
                .select {
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