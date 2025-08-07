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
import com.example.lovebug_project.databinding.FragmentChatListBinding
import kotlinx.coroutines.launch

class ChatListFragment : Fragment() {

    private var _binding: FragmentChatListBinding? = null
    private val binding get() = _binding!!

    private lateinit var chatListAdapter: ChatListAdapter
    private val chatRoomList = mutableListOf<ChatRoom>() // 실제로는 ViewModel이나 Repository에서 가져옴
    private val chatRepository = SupabaseChatRepository()

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
            // 아이템 클릭 시 ChatFragment로 이동
            navigateToChatFragment(chatRoom)
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

    private fun navigateToChatFragment(chatRoom: ChatRoom) {
        val chatFragment = ChatFragment().apply {
            arguments = Bundle().apply {
                putString("chatRoomId", chatRoom.roomId) // 채팅방 ID 전달
                putString("partnerName", chatRoom.partnerName) // 상대방 이름 전달 (선택적)
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
                // TODO: 현재 로그인된 사용자 ID 가져오기 (실제 구현 시 AuthRepository 사용)
                val currentUserId = "current_user_id" // 임시
                
                // 닉네임으로 채팅방 생성 또는 기존 채팅방 가져오기
                val chat = chatRepository.startChatWithNickname(currentUserId, nickname)
                
                if (chat != null) {
                    // 성공적으로 채팅방을 생성/가져온 경우
                    val chatRoom = ChatRoom(
                        roomId = chat.chatId.toString(),
                        partnerName = nickname,
                        lastMessage = "채팅을 시작하세요",
                        timestamp = System.currentTimeMillis(),
                        partnerProfileImageUrl = null
                    )
                    navigateToChatFragment(chatRoom)
                } else {
                    // 실패한 경우
                    Toast.makeText(context, "채팅방 생성에 실패했습니다. 닉네임을 확인해주세요.", Toast.LENGTH_SHORT).show()
                }
                
            } catch (e: Exception) {
                Toast.makeText(context, "오류가 발생했습니다: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // 임시로 대화방 목록 데이터 생성 (실제로는 서버나 로컬 DB에서 가져와야 함)
    private fun loadChatRooms() {
        // TODO: 실제 데이터 로딩 로직 구현
        val dummyChatRooms = listOf(
            ChatRoom("room1", "개발자 A", "안녕하세요! 오늘 날씨 좋네요.", System.currentTimeMillis() - 100000, null),
            ChatRoom("room2", "디자이너 B", "네, 좋아요. 커피 한잔 어때요?", System.currentTimeMillis() - 500000, null),
            ChatRoom("room3", "기획자 C", "프로젝트 진행 상황 공유해주세요.", System.currentTimeMillis() - 800000, null)
        )
        chatRoomList.clear()
        chatRoomList.addAll(dummyChatRooms)
        chatListAdapter.updateData(chatRoomList)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
