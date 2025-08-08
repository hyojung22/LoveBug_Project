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
            loadSavingPerformanceData()
        }
        
        // 북마크 클릭 이벤트
        binding.constraintLayout3.setOnClickListener {
            Toast.makeText(requireContext(), "북마크 기능 준비 중입니다.", Toast.LENGTH_SHORT).show()
        }
        
        // 나의 게시물 클릭 이벤트  
        binding.constraintLayout4.setOnClickListener {
            // MainActivity의 frame을 사용해서 MyBoardFragment로 이동
            val mainActivity = requireActivity() as com.example.lovebug_project.MainActivity
            val transaction = mainActivity.supportFragmentManager.beginTransaction()
            transaction.replace(R.id.frame, MyBoardFragment.newInstance())
            transaction.addToBackStack(null)
            transaction.commit()
        }
        
        // 정보수정 클릭 이벤트
        binding.textView.setOnClickListener {
            showEditProfileDialog()
        }
        
        // 로그아웃 클릭 이벤트
        binding.btnLogout.setOnClickListener {
            performLogout()
        }
        
        // 예산 설정 클릭 이벤트 (기존 로그아웃 버튼을 길게 눌러 예산 설정)
        binding.btnLogout.setOnLongClickListener {
            showBudgetSettingDialog()
            true
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
    
    private fun loadSavingPerformanceData() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // 현재 로그인한 사용자 ID 가져오기
                val currentUserUuid = AuthHelper.getSupabaseUserId(requireContext())
                
                if (currentUserUuid != null) {
                    // 절약성과 데이터 조회
                    val result = MyApplication.savingRepository.getSavingPerformanceAsCategoryData(currentUserUuid)
                    
                    withContext(Dispatchers.Main) {
                        result.fold(
                            onSuccess = { categoryDataList ->
                                // SavingPerformFragment로 이동 (사용자 ID도 전달)
                                parentFragmentManager.commit {
                                    setReorderingAllowed(true)
                                    replace(R.id.fragment_container, SavingPerformFragment.newInstance(categoryDataList, currentUserUuid))
                                    addToBackStack(null)
                                }
                            },
                            onFailure = { exception ->
                                // 에러 발생 시 기본 데이터로 표시
                                val defaultCategoryList = listOf(
                                    CategoryData("데이터 없음", 100, "#E0E0E0")
                                )
                                parentFragmentManager.commit {
                                    setReorderingAllowed(true)
                                    replace(R.id.fragment_container, SavingPerformFragment.newInstance(defaultCategoryList, currentUserUuid))
                                    addToBackStack(null)
                                }
                                Toast.makeText(requireContext(), "절약성과 데이터를 불러오는데 실패했습니다: ${exception.message}", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(requireContext(), "로그인이 필요합니다.", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "절약성과 로딩 중 오류 발생: ${e.message}", Toast.LENGTH_SHORT).show()
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
    
    private fun showBudgetSettingDialog() {
        // 현재 예산 조회
        val currentBudget = MyApplication.authRepository.getUserMonthlyBudget()
        
        val editText = EditText(requireContext()).apply {
            setText(currentBudget?.toString() ?: "")
            hint = "월별 예산을 입력하세요 (원)"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setPadding(50, 30, 50, 30)
        }
        
        AlertDialog.Builder(requireContext())
            .setTitle("월별 예산 설정")
            .setMessage("이번 달 목표 지출 금액을 설정하세요")
            .setView(editText)
            .setPositiveButton("확인") { _, _ ->
                val budgetText = editText.text.toString().trim()
                if (budgetText.isNotEmpty()) {
                    val budget = budgetText.toIntOrNull()
                    if (budget != null && budget > 0) {
                        updateMonthlyBudget(budget)
                    } else {
                        Toast.makeText(requireContext(), "올바른 금액을 입력해주세요.", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(requireContext(), "예산을 입력해주세요.", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("취소", null)
            .show()
    }
    
    private fun updateMonthlyBudget(budget: Int) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // 현재 월의 예산 설정
                val calendar = java.util.Calendar.getInstance()
                val dateFormat = java.text.SimpleDateFormat("yyyy-MM", java.util.Locale.getDefault())
                val currentYearMonth = dateFormat.format(calendar.time)
                
                val result = MyApplication.authRepository.setMonthlyBudgetForMonth(currentYearMonth, budget)
                
                withContext(Dispatchers.Main) {
                    result.fold(
                        onSuccess = {
                            Toast.makeText(requireContext(), "월별 예산이 설정되었습니다: ${String.format("%,d", budget)}원", Toast.LENGTH_SHORT).show()
                        },
                        onFailure = { exception ->
                            Toast.makeText(requireContext(), "예산 설정 실패: ${exception.message}", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "예산 설정 중 오류 발생: ${e.message}", Toast.LENGTH_SHORT).show()
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