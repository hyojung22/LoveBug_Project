package com.example.lovebug_project.mypage

import android.content.Intent
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.commit
import com.example.lovebug_project.R
import com.example.lovebug_project.databinding.FragmentMypageBinding


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
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}