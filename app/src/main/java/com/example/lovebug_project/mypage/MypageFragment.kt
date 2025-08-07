package com.example.lovebug_project.mypage

import android.content.Intent
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.commit
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.widget.Toast
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import com.bumptech.glide.Glide
import com.example.lovebug_project.R
import com.example.lovebug_project.databinding.FragmentMypageBinding
import com.example.lovebug_project.data.db.MyApplication
import com.example.lovebug_project.utils.AuthHelper
import com.example.lovebug_project.utils.loadProfileImage
import com.example.lovebug_project.auth.LoginActivity


class MypageFragment : Fragment() {

    private var _binding: FragmentMypageBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMypageBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 현재 로그인한 유저 정보 로드
        loadUserProfile()
        
        // 절약성과 클릭 이벤트
        binding.constraintLayout2.setOnClickListener {
            val categoryListToPass = listOf(
                CategoryData("식비", 45, "#FFC3A0"),   // 주황색
                CategoryData("교통", 25, "#A3D6E3"),   // 파란색
                CategoryData("쇼핑", 15, "#C9E4A6"),   // 녹색
                CategoryData("기타", 15, "#E4B4D1")    // 분홍색
            )
            parentFragmentManager.commit {
                setReorderingAllowed(true)
                replace(R.id.fragment_container, SavingPerformFragment.newInstance(categoryListToPass))
                addToBackStack(null)
            }
        }
        
        // 북마크 클릭 이벤트
        binding.constraintLayout3.setOnClickListener {
            Toast.makeText(requireContext(), "북마크 기능 준비 중입니다.", Toast.LENGTH_SHORT).show()
        }
        
        // 나의 게시물 클릭 이벤트  
        binding.constraintLayout4.setOnClickListener {
            Toast.makeText(requireContext(), "나의 게시물 기능 준비 중입니다.", Toast.LENGTH_SHORT).show()
        }
        
        // 정보수정 클릭 이벤트
        binding.textView.setOnClickListener {
            showEditProfileDialog()
        }
        
        // 로그아웃 클릭 이벤트
        binding.btnLogout.setOnClickListener {
            performLogout()
        }
        
        // 탈퇴하기 클릭 이벤트
        binding.textView9.setOnClickListener {
            Toast.makeText(requireContext(), "탈퇴하기 기능 준비 중입니다.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadUserProfile() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // 현재 로그인한 유저 정보 가져오기
                val currentUser = MyApplication.authRepository.getCurrentUser()
                val currentUserUuid = AuthHelper.getSupabaseUserId(requireContext())
                
                withContext(Dispatchers.Main) {
                    if (currentUser != null) {
                        // 사용자 정보 표시
                        val userMetadata = currentUser.userMetadata
                        
                        // 닉네임 설정 (메타데이터에서 가져오거나 이메일에서 추출)
                        val nickname = userMetadata?.get("nickname")?.toString()
                            ?: userMetadata?.get("display_name")?.toString()
                            ?: userMetadata?.get("name")?.toString() 
                            ?: userMetadata?.get("full_name")?.toString()
                            ?: currentUser.email?.substringBefore("@")
                            ?: "사용자"
                            
                        binding.textView3.text = nickname
                        
                        // 이메일 설정
                        binding.tvUserEmail.text = currentUser.email ?: "이메일 정보 없음"
                        
                        // 프로필 이미지 로드
                        val profileImageUrl = userMetadata?.get("avatar_url")?.toString()
                            ?: userMetadata?.get("picture")?.toString()
                            ?: userMetadata?.get("profile_image")?.toString()
                            
                        if (profileImageUrl != null) {
                            binding.imageView7.loadProfileImage(profileImageUrl)
                        } else {
                            // 기본 프로필 이미지 설정
                            binding.imageView7.setImageResource(R.drawable.default_profile_image)
                        }
                    } else {
                        // 로그인되지 않은 상태
                        binding.textView3.text = "로그인 필요"
                        binding.tvUserEmail.text = "로그인이 필요합니다"
                        binding.imageView7.setImageResource(R.drawable.default_profile_image)
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.textView3.text = "정보 로딩 실패"
                    binding.tvUserEmail.text = "정보를 불러올 수 없습니다"
                    binding.imageView7.setImageResource(R.drawable.default_profile_image)
                    Toast.makeText(requireContext(), "사용자 정보를 불러올 수 없습니다: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    private fun showEditProfileDialog() {
        val currentNickname = binding.textView3.text.toString()
        
        val editText = EditText(requireContext()).apply {
            setText(currentNickname)
            hint = "새 닉네임을 입력하세요"
            setPadding(50, 30, 50, 30)
        }
        
        AlertDialog.Builder(requireContext())
            .setTitle("닉네임 변경")
            .setMessage("닉네임을 변경하시겠습니까?")
            .setView(editText)
            .setPositiveButton("확인") { _, _ ->
                val newNickname = editText.text.toString().trim()
                if (newNickname.isNotEmpty() && newNickname != currentNickname) {
                    updateUserNickname(newNickname)
                } else if (newNickname.isEmpty()) {
                    Toast.makeText(requireContext(), "닉네임을 입력해주세요.", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("취소", null)
            .show()
    }
    
    private fun updateUserNickname(newNickname: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // Supabase에서 사용자 메타데이터 업데이트
                val result = MyApplication.authRepository.updateUserMetadata(
                    nickname = newNickname
                )
                
                withContext(Dispatchers.Main) {
                    result.fold(
                        onSuccess = {
                            binding.textView3.text = newNickname
                            Toast.makeText(requireContext(), "닉네임이 변경되었습니다.", Toast.LENGTH_SHORT).show()
                        },
                        onFailure = { exception ->
                            Toast.makeText(requireContext(), "닉네임 변경 실패: ${exception.message}", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "닉네임 변경 중 오류 발생: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    private fun performLogout() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // Supabase에서 로그아웃
                AuthHelper.logout(requireContext())
                
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "로그아웃되었습니다.", Toast.LENGTH_SHORT).show()
                    
                    // LoginActivity로 이동
                    val intent = Intent(requireContext(), LoginActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                    requireActivity().finish()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "로그아웃 중 오류가 발생했습니다: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}