package com.example.lovebug_project.chatlist

import android.app.Dialog
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.lovebug_project.R
import com.example.lovebug_project.chatlist.adapter.UserSearchAdapter
import com.example.lovebug_project.data.repository.SupabaseChatRepository
import com.example.lovebug_project.data.repository.SupabaseAuthRepository
import com.example.lovebug_project.data.supabase.models.ChatUserSearchResult
import com.example.lovebug_project.databinding.DialogStartNewChatBinding
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Dialog fragment for starting new chat by searching users by nickname
 */
class StartNewChatDialogFragment(
    private val onUserSelected: (String) -> Unit
) : DialogFragment() {

    private var _binding: DialogStartNewChatBinding? = null
    private val binding get() = _binding!!

    private lateinit var userSearchAdapter: UserSearchAdapter
    private val chatRepository = SupabaseChatRepository()
    private val authRepository = SupabaseAuthRepository()
    
    private var searchJob: Job? = null
    private val searchResults = mutableListOf<ChatUserSearchResult>()

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState)
        dialog.window?.apply {
            setBackgroundDrawableResource(R.drawable.rounded_dialog_background)
            // 모달창 밖 터치시 닫기
            setFlags(
                android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND,
                android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND
            )
            // dimming 정도 조절 (0.0 ~ 1.0, 기본값은 0.6)
            attributes = attributes.apply {
                dimAmount = 0.5f
            }
        }
        return dialog
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogStartNewChatBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        setupTextWatcher()
        setupButtons()
    }

    private fun setupRecyclerView() {
        userSearchAdapter = UserSearchAdapter(searchResults) { user ->
            // 사용자 선택 시
            onUserSelected(user.nickname)
            dismiss()
        }

        binding.recyclerViewSearchResults.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = userSearchAdapter
        }
    }

    private fun setupTextWatcher() {
        binding.editTextNickname.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val nickname = s.toString().trim()
                binding.buttonSearch.isEnabled = nickname.isNotEmpty()
                
                // 실시간 검색 (디바운싱 적용)
                searchJob?.cancel()
                if (nickname.length >= 2) { // 최소 2글자부터 검색
                    searchJob = lifecycleScope.launch {
                        delay(300) // 300ms 디바운싱
                        searchUsers(nickname)
                    }
                } else {
                    clearSearchResults()
                }
            }
            
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun setupButtons() {
        binding.buttonCancel.setOnClickListener {
            dismiss()
        }

        binding.buttonSearch.setOnClickListener {
            val nickname = binding.editTextNickname.text.toString().trim()
            if (nickname.isNotEmpty()) {
                searchUsers(nickname)
            }
        }
    }

    private fun searchUsers(nickname: String) {
        lifecycleScope.launch {
            showLoading(true)
            
            try {
                // 현재 로그인된 사용자 ID 가져오기
                val currentUser = authRepository.getCurrentUser()
                val currentUserId = currentUser?.id
                
                if (currentUserId == null) {
                    showLoading(false)
                    showError("로그인 상태를 확인할 수 없습니다. 다시 로그인해주세요.")
                    return@launch
                }
                
                val results = chatRepository.searchUsersForChat(nickname, currentUserId, 10)
                
                showLoading(false)
                displaySearchResults(results)
                
            } catch (e: Exception) {
                showLoading(false)
                showError("사용자 검색 중 오류가 발생했습니다: ${e.message}")
            }
        }
    }

    private fun displaySearchResults(results: List<ChatUserSearchResult>) {
        searchResults.clear()
        searchResults.addAll(results)
        userSearchAdapter.updateUsers(searchResults)

        if (results.isEmpty()) {
            binding.recyclerViewSearchResults.visibility = View.GONE
            binding.textViewNoResults.visibility = View.VISIBLE
        } else {
            binding.recyclerViewSearchResults.visibility = View.VISIBLE
            binding.textViewNoResults.visibility = View.GONE
        }
    }

    private fun clearSearchResults() {
        searchResults.clear()
        userSearchAdapter.updateUsers(searchResults)
        binding.recyclerViewSearchResults.visibility = View.GONE
        binding.textViewNoResults.visibility = View.GONE
    }

    private fun showLoading(show: Boolean) {
        if (show) {
            binding.progressBarLoading.visibility = View.VISIBLE
            binding.recyclerViewSearchResults.visibility = View.GONE
            binding.textViewNoResults.visibility = View.GONE
        } else {
            binding.progressBarLoading.visibility = View.GONE
        }
    }

    private fun showError(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        searchJob?.cancel()
        _binding = null
    }
}