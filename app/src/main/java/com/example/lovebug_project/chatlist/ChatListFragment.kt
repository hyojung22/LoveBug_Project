package com.example.lovebug_project.chatlist

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.lovebug_project.R
import com.example.lovebug_project.chat.ChatFragment // 채팅 프래그먼트 import
import com.example.lovebug_project.chatlist.adapter.ChatListAdapter
import com.example.lovebug_project.chatlist.model.ChatRoom
import com.example.lovebug_project.data.repository.SupabaseChatRepository
import com.example.lovebug_project.data.supabase.models.ChatRoomInfo
import com.example.lovebug_project.databinding.FragmentChatListBinding
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.format.DateTimeFormatter

class ChatListFragment : Fragment() {

    private var _binding: FragmentChatListBinding? = null
    private val binding get() = _binding!!

    private lateinit var chatListAdapter: ChatListAdapter
    private val chatRoomList = mutableListOf<ChatRoom>() // 실제로는 ViewModel이나 Repository에서 가져옴
    private val chatRepository = SupabaseChatRepository()
    
    // ChatRoom ID와 Partner ID 매핑을 위한 맵
    private val chatRoomPartnerMap = mutableMapOf<String, String>()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentChatListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        setupFloatingActionButton()
        loadChatRooms() // 임시 데이터 로드
    }

    private fun setupRecyclerView() {
        chatListAdapter = ChatListAdapter(chatRoomList) { chatRoom ->
            // 아이템 클릭 시 ChatFragment로 이동 (Partner ID 포함)
            val partnerId = chatRoomPartnerMap[chatRoom.roomId]
            navigateToChatFragment(chatRoom, partnerId)
        }
        binding.recyclerViewChatList.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = chatListAdapter
        }
    }

    private fun setupFloatingActionButton() {
        binding.fabStartNewChat.setOnClickListener {
            showStartNewChatDialog()
        }
    }

    private fun navigateToChatFragment(chatRoom: ChatRoom, partnerUserId: String? = null) {
        val chatFragment = ChatFragment().apply {
            arguments = Bundle().apply {
                putString("chatRoomId", chatRoom.roomId) // 채팅방 ID 전달
                putString("partnerName", chatRoom.partnerName) // 상대방 이름 전달 (선택적)
                partnerUserId?.let { 
                    putString("partnerUserId", it) // 상대방 User ID 전달
                }
            }
        }

        parentFragmentManager.beginTransaction()
            .replace(R.id.frame, chatFragment) // MainActivity의 FrameLayout ID
            .addToBackStack(null) // 뒤로가기 버튼으로 ChatListFragment로 돌아올 수 있도록 함
            .commit()
    }

    // 새 채팅 시작 다이얼로그 표시
    private fun showStartNewChatDialog() {
        val dialog = StartNewChatDialogFragment { nickname ->
            // 닉네임으로 채팅 시작
            startChatWithNickname(nickname)
        }
        dialog.show(childFragmentManager, "StartNewChatDialog")
    }

    // 닉네임으로 채팅 시작
    private fun startChatWithNickname(nickname: String) {
        lifecycleScope.launch {
            try {
                // 현재 로그인된 사용자 ID 가져오기
                val currentUserId = chatRepository.getCurrentUserId()
                
                if (currentUserId == null) {
                    Toast.makeText(context, "로그인이 필요합니다.", Toast.LENGTH_SHORT).show()
                    return@launch
                }
                
                // 닉네임으로 채팅방 생성 또는 기존 채팅방 가져오기
                val chat = chatRepository.startChatWithNickname(currentUserId, nickname)
                
                if (chat != null) {
                    // 상대방 user ID 가져오기
                    val partnerUserId = if (chat.user1Id == currentUserId) chat.user2Id else chat.user1Id
                    
                    // 성공적으로 채팅방을 생성/가져온 경우
                    val chatRoom = ChatRoom(
                        roomId = chat.chatId.toString(),
                        partnerName = nickname,
                        lastMessage = "채팅을 시작하세요",
                        timestamp = System.currentTimeMillis(),
                        partnerProfileImageUrl = null
                    )
                    navigateToChatFragment(chatRoom, partnerUserId)
                } else {
                    // 실패한 경우
                    Toast.makeText(context, "채팅방 생성에 실패했습니다. 닉네임을 확인해주세요.", Toast.LENGTH_SHORT).show()
                }
                
            } catch (e: Exception) {
                Toast.makeText(context, "오류가 발생했습니다: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * Load chat rooms from Supabase database
     */
    private fun loadChatRooms() {
        lifecycleScope.launch {
            try {
                // Get current authenticated user ID
                val currentUserId = chatRepository.getCurrentUserId()
                
                if (currentUserId == null) {
                    Toast.makeText(context, "로그인이 필요합니다.", Toast.LENGTH_SHORT).show()
                    return@launch
                }
                
                // Fetch chat rooms from database
                val chatRoomInfoList = chatRepository.getUserChatsWithLastMessage(currentUserId)
                
                // Clear existing mappings
                chatRoomPartnerMap.clear()
                
                // Convert ChatRoomInfo to ChatRoom for UI and populate partner mapping
                val chatRooms = chatRoomInfoList.map { chatRoomInfo ->
                    // Store partnerId mapping before conversion
                    chatRoomPartnerMap[chatRoomInfo.chatId.toString()] = chatRoomInfo.partnerId
                    // Convert to ChatRoom
                    chatRoomInfo.toChatRoom()
                }
                
                // Update UI
                chatRoomList.clear()
                chatRoomList.addAll(chatRooms)
                chatListAdapter.updateData(chatRoomList)
                
                if (chatRooms.isEmpty()) {
                    // Show empty state message
                    Toast.makeText(context, "채팅방이 없습니다. 새 채팅을 시작해보세요!", Toast.LENGTH_SHORT).show()
                }
                
            } catch (e: Exception) {
                Toast.makeText(context, "채팅방 목록을 불러오는데 실패했습니다: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    /**
     * Convert ChatRoomInfo to ChatRoom for UI display
     */
    private fun ChatRoomInfo.toChatRoom(): ChatRoom {
        // Convert timestamp from ISO string to Long (if available)
        val timestamp = try {
            if (lastMessageTimestamp != null) {
                Instant.parse(lastMessageTimestamp).toEpochMilli()
            } else {
                // Use updated_at as fallback
                Instant.parse(updatedAt).toEpochMilli()
            }
        } catch (e: Exception) {
            // Fallback to current time if parsing fails
            System.currentTimeMillis()
        }
        
        return ChatRoom(
            roomId = chatId.toString(),
            partnerName = partnerNickname,
            lastMessage = lastMessage ?: "채팅을 시작하세요",
            timestamp = timestamp,
            partnerProfileImageUrl = partnerAvatarUrl
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        // Clear partner mapping to prevent memory leaks
        chatRoomPartnerMap.clear()
    }
}
