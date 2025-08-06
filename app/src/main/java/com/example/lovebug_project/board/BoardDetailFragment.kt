package com.example.lovebug_project.board

import android.content.Intent
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.example.lovebug_project.R
import com.example.lovebug_project.databinding.FragmentBoardDetailBinding
import com.example.lovebug_project.databinding.FragmentBoardMainBinding

class BoardDetailFragment : Fragment() {
    // binding 인스턴스를 nullable로 선언
    private var _binding: FragmentBoardDetailBinding? = null
    // 안전하게 binding을 꺼내 쓰는 프로퍼티
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // inflate 대신 binding.inflate 사용
        _binding = FragmentBoardDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // 예: binding.spinnerSort, binding.rvBoard 등 바로 접근 가능


    }

    override fun onDestroyView() {
        super.onDestroyView()
        // 메모리 누수 방지를 위해 반드시 null 처리
        _binding = null
    }
}