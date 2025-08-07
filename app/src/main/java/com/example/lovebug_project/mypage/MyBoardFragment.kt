package com.example.lovebug_project.mypage

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.example.lovebug_project.R

// TODO: 매개변수 인수 이름을 변경하고, 일치하는 이름을 선택하세요
// 프래그먼트 초기화 매개변수, 예: ARG_ITEM_NUMBER
private const val ARG_PARAM1 = "param1"
private const val ARG_PARAM2 = "param2"

/**
 * 간단한 [Fragment] 서브클래스입니다.
 * 이 프래그먼트의 인스턴스를 생성하려면
 * [MyBoardFragment.newInstance] 팩토리 메서드를 사용하세요.
 */
class MyBoardFragment : Fragment() {
    // TODO: 매개변수 이름을 변경하고 타입을 수정하세요
    private var param1: String? = null
    private var param2: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            param1 = it.getString(ARG_PARAM1)
            param2 = it.getString(ARG_PARAM2)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // 이 프래그먼트의 레이아웃을 inflate 합니다
        return inflater.inflate(R.layout.fragment_my_board, container, false)
    }

    companion object {
        /**
         * 제공된 매개변수를 사용하여 이 프래그먼트의
         * 새 인스턴스를 생성하는 팩토리 메서드입니다.
         *
         * @param param1 매개변수 1
         * @param param2 매개변수 2
         * @return MyBoardFragment의 새 인스턴스
         */
        // TODO: 매개변수의 이름, 타입 및 개수를 변경하세요
        @JvmStatic
        fun newInstance(param1: String, param2: String) =
            MyBoardFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_PARAM1, param1)
                    putString(ARG_PARAM2, param2)
                }
            }
    }
}