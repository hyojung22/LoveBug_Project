package com.example.lovebug_project.websocket

import android.util.Log
import com.example.lovebug_project.BuildConfig
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.serialization.*
import kotlinx.serialization.json.*
import okhttp3.*
import java.util.concurrent.TimeUnit

/**
 * WebSocket 매니저 - 실시간 채팅을 위한 WebSocket 연결 관리
 */
class WebSocketManager private constructor() {
    
    companion object {
        private const val TAG = "WebSocketManager"
        // TODO: 실제 서버 URL로 변경 필요
        private const val WS_URL = "ws://10.0.2.2:8080" // Android 에뮬레이터에서 localhost 접근
        
        @Volatile
        private var INSTANCE: WebSocketManager? = null
        
        fun getInstance(): WebSocketManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: WebSocketManager().also { INSTANCE = it }
            }
        }
    }
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .pingInterval(30, TimeUnit.SECONDS) // 30초마다 ping
        .build()
    
    private var webSocket: WebSocket? = null
    private var currentUserId: String? = null
    private var currentChatRoomId: Int? = null
    
    // 메시지 이벤트를 위한 Flow
    private val _messageFlow = MutableSharedFlow<WebSocketMessage>()
    val messageFlow: SharedFlow<WebSocketMessage> = _messageFlow.asSharedFlow()
    
    // 연결 상태를 위한 Flow
    private val _connectionState = MutableSharedFlow<ConnectionState>()
    val connectionState: SharedFlow<ConnectionState> = _connectionState.asSharedFlow()
    
    private val webSocketListener = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            Log.d(TAG, "WebSocket connection opened")
            _connectionState.tryEmit(ConnectionState.Connected)
            
            // 연결 성공 후 인증
            currentUserId?.let { userId ->
                sendMessage(WebSocketMessage.Auth(userId))
            }
        }
        
        override fun onMessage(webSocket: WebSocket, text: String) {
            Log.d(TAG, "WebSocket message received: $text")
            
            try {
                val jsonObject = Json.parseToJsonElement(text).jsonObject
                val type = jsonObject["type"]?.jsonPrimitive?.content ?: return
                
                val message = when (type) {
                    "auth_success" -> Json.decodeFromJsonElement<WebSocketMessage.AuthSuccess>(jsonObject)
                    "joined_room" -> Json.decodeFromJsonElement<WebSocketMessage.JoinedRoom>(jsonObject)
                    "new_message" -> Json.decodeFromJsonElement<WebSocketMessage.NewMessage>(jsonObject)
                    "left_room" -> WebSocketMessage.LeftRoom
                    "error" -> Json.decodeFromJsonElement<WebSocketMessage.Error>(jsonObject)
                    "ping" -> WebSocketMessage.Ping
                    "pong" -> WebSocketMessage.Pong
                    else -> {
                        Log.w(TAG, "Unknown message type: $type")
                        return
                    }
                }
                
                _messageFlow.tryEmit(message)
                
                // 특정 메시지 타입에 대한 처리
                when (message) {
                    is WebSocketMessage.AuthSuccess -> {
                        Log.d(TAG, "Authentication successful")
                        // 채팅방에 자동 입장
                        currentChatRoomId?.let { roomId ->
                            sendMessage(WebSocketMessage.JoinRoom(roomId))
                        }
                    }
                    is WebSocketMessage.JoinedRoom -> {
                        Log.d(TAG, "Joined room: ${message.chatRoomId}")
                    }
                    is WebSocketMessage.Error -> {
                        Log.e(TAG, "WebSocket error: ${message.message}")
                    }
                    else -> {}
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing WebSocket message", e)
            }
        }
        
        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            Log.d(TAG, "WebSocket closing: $code $reason")
            webSocket.close(1000, null)
        }
        
        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            Log.d(TAG, "WebSocket closed: $code $reason")
            _connectionState.tryEmit(ConnectionState.Disconnected)
            this@WebSocketManager.webSocket = null
        }
        
        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            Log.e(TAG, "WebSocket failure", t)
            _connectionState.tryEmit(ConnectionState.Error(t.message ?: "Unknown error"))
            this@WebSocketManager.webSocket = null
        }
    }
    
    /**
     * WebSocket 연결 시작
     */
    fun connect(userId: String, chatRoomId: Int) {
        if (webSocket != null) {
            Log.w(TAG, "WebSocket already connected")
            return
        }
        
        currentUserId = userId
        currentChatRoomId = chatRoomId
        
        _connectionState.tryEmit(ConnectionState.Connecting)
        
        val request = Request.Builder()
            .url(WS_URL)
            .build()
        
        webSocket = client.newWebSocket(request, webSocketListener)
    }
    
    /**
     * WebSocket 연결 종료
     */
    fun disconnect() {
        currentChatRoomId?.let { roomId ->
            sendMessage(WebSocketMessage.LeaveRoom(roomId))
        }
        
        webSocket?.close(1000, "Client closing connection")
        webSocket = null
        currentUserId = null
        currentChatRoomId = null
    }
    
    /**
     * 메시지 전송
     */
    fun sendChatMessage(message: String) {
        Log.d(TAG, "sendChatMessage called with: '$message'")
        
        if (webSocket == null) {
            Log.e(TAG, "WebSocket not connected - webSocket is null")
            return
        }
        
        Log.d(TAG, "WebSocket exists, creating SendMessage and calling sendMessage")
        sendMessage(WebSocketMessage.SendMessage(message))
    }
    
    /**
     * WebSocket 메시지 전송
     */
    private fun sendMessage(message: WebSocketMessage) {
        try {
            val jsonObject = buildJsonObject {
                put("type", message.type)
                when (message) {
                    is WebSocketMessage.Auth -> put("userId", message.userId)
                    is WebSocketMessage.JoinRoom -> put("chatRoomId", message.chatRoomId)
                    is WebSocketMessage.SendMessage -> put("message", message.message)
                    is WebSocketMessage.LeaveRoom -> put("chatRoomId", message.chatRoomId)
                    else -> {}
                }
            }
            val jsonString = jsonObject.toString()
            webSocket?.send(jsonString)
            Log.d(TAG, "WebSocket message sent: $jsonString")
        } catch (e: Exception) {
            Log.e(TAG, "Error sending WebSocket message", e)
        }
    }
    
    /**
     * 연결 상태 확인
     */
    fun isConnected(): Boolean {
        val isWebSocketNotNull = webSocket != null
        val lastConnectionState = connectionState.replayCache.lastOrNull()
        val result = isWebSocketNotNull && lastConnectionState == ConnectionState.Connected
        
        Log.d(TAG, "isConnected check - webSocket != null: $isWebSocketNotNull, lastState: $lastConnectionState, result: $result")
        return result
    }
}

/**
 * WebSocket 메시지 타입들
 */
sealed class WebSocketMessage {
    abstract val type: String
    
    @Serializable
    data class Auth(val userId: String) : WebSocketMessage() {
        override val type: String = "auth"
    }
    
    @Serializable
    data class AuthSuccess(val userId: String) : WebSocketMessage() {
        override val type: String = "auth_success"
    }
    
    @Serializable
    data class JoinRoom(val chatRoomId: Int) : WebSocketMessage() {
        override val type: String = "join_room"
    }
    
    @Serializable
    data class JoinedRoom(val chatRoomId: Int) : WebSocketMessage() {
        override val type: String = "joined_room"
    }
    
    @Serializable
    data class SendMessage(val message: String) : WebSocketMessage() {
        override val type: String = "send_message"
    }
    
    @Serializable
    data class NewMessage(
        val messageId: Int,
        val chatId: Int,
        val senderId: String,
        val message: String,
        val timestamp: String
    ) : WebSocketMessage() {
        override val type: String = "new_message"
    }
    
    @Serializable
    data class LeaveRoom(val chatRoomId: Int) : WebSocketMessage() {
        override val type: String = "leave_room"
    }
    
    object LeftRoom : WebSocketMessage() {
        override val type: String = "left_room"
    }
    
    @Serializable
    data class Error(val message: String) : WebSocketMessage() {
        override val type: String = "error"
    }
    
    object Ping : WebSocketMessage() {
        override val type: String = "ping"
    }
    
    object Pong : WebSocketMessage() {
        override val type: String = "pong"
    }
}

/**
 * WebSocket 연결 상태
 */
sealed class ConnectionState {
    object Connecting : ConnectionState()
    object Connected : ConnectionState()
    object Disconnected : ConnectionState()
    data class Error(val message: String) : ConnectionState()
}