package com.example.lovebug_project.mypage

import CategoryAdapter
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.lovebug_project.databinding.FragmentSavingPerformBinding
import com.example.lovebug_project.data.db.MyApplication
import com.example.lovebug_project.utils.AuthHelper
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.github.mikephil.charting.utils.ColorTemplate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class SavingPerformFragment : Fragment() {

    private var _binding : FragmentSavingPerformBinding? = null
    private val binding get() = _binding!!

    private var categoryList : List<CategoryData>? = null
    private var currentCalendar: Calendar = Calendar.getInstance()
    private var currentUserId: String? = null
    private var categoryAdapter: CategoryAdapter? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            categoryList = it.getSerializable("categoryList") as? List<CategoryData>
            currentUserId = it.getString("userId")
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSavingPerformBinding.inflate(inflater, container, false)
        return  binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 현재 사용자 ID 가져오기 (arguments가 우선, 없으면 AuthHelper에서)
        if (currentUserId == null) {
            currentUserId = AuthHelper.getSupabaseUserId(requireContext())
        }
        
        // RecyclerView 초기 설정
        binding.rvCategory.layoutManager = LinearLayoutManager(requireContext())

        // 초기 데이터 로드 (전달받은 데이터가 있어도 현재 월 데이터를 로드)
        loadSavingDataForCurrentMonth()

        // 날짜 관련 설정
        updateDateText()
        binding.imageView2.setOnClickListener {
            currentCalendar.add(Calendar.MONTH, 1)
            updateDateText()
            loadSavingDataForCurrentMonth()
        }
        binding.imageView3.setOnClickListener {
            currentCalendar.add(Calendar.MONTH, -1)
            updateDateText()
            loadSavingDataForCurrentMonth()
        }
    }

    private fun updateDateText() {
        val sdf = SimpleDateFormat("yyyy년 M월", Locale.KOREA)
        binding.date.text = sdf.format(currentCalendar.time)
    }
    
    private fun loadSavingDataForCurrentMonth() {
        val userId = currentUserId
        if (userId == null) {
            Toast.makeText(requireContext(), "사용자 정보를 찾을 수 없습니다.", Toast.LENGTH_SHORT).show()
            return
        }
        
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // 현재 선택된 월의 YYYY-MM 형식 문자열 생성
                val dateFormat = SimpleDateFormat("yyyy-MM", Locale.getDefault())
                val yearMonth = dateFormat.format(currentCalendar.time)
                
                // 디버깅용 로그
                android.util.Log.d("SavingPerform", "Loading data for: $yearMonth")
                
                // 절약성과 데이터 조회
                val result = MyApplication.savingRepository.getSavingPerformanceAsCategoryData(userId, yearMonth)
                
                withContext(Dispatchers.Main) {
                    result.fold(
                        onSuccess = { categoryDataList ->
                            // 디버깅용 로그
                            android.util.Log.d("SavingPerform", "Received data size: ${categoryDataList.size}")
                            categoryDataList.forEach { category ->
                                android.util.Log.d("SavingPerform", "Category: ${category.cg_name}, Percentage: ${category.percentage}%")
                            }
                            
                            if (categoryDataList.isEmpty()) {
                                // 데이터가 비어있을 때
                                android.util.Log.d("SavingPerform", "Empty data, showing default")
                                val defaultCategoryList = listOf(
                                    CategoryData("데이터 없음", 100, "#E0E0E0")
                                )
                                updateUIWithData(defaultCategoryList)
                            } else {
                                updateUIWithData(categoryDataList)
                            }
                        },
                        onFailure = { exception ->
                            // 에러 발생 시 기본 데이터로 표시
                            val defaultCategoryList = listOf(
                                CategoryData("데이터 없음", 100, "#E0E0E0")
                            )
                            updateUIWithData(defaultCategoryList)
                            Toast.makeText(requireContext(), "데이터 로딩 실패: ${exception.message}", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "데이터 로딩 중 오류: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    private fun updateUIWithData(categories: List<CategoryData>) {
        // RecyclerView 업데이트 (항상 새 어댑터 생성)
        categoryAdapter = CategoryAdapter(categories)
        binding.rvCategory.adapter = categoryAdapter

        // PieChart 업데이트
        setupPieChart(categories)
    }

    private fun setupPieChart(categories: List<CategoryData>) {
        val entries = ArrayList<PieEntry>()
        for (category in categories) {
            entries.add(PieEntry(category.percentage.toFloat(), category.cg_name))
        }

        val dataSet = PieDataSet(entries, "Categories")


        val colors = ArrayList<Int>()
        for (category in categories) {
            colors.add(Color.parseColor(category.color)) // category.color 사용
        }
        dataSet.colors = colors // 새로 만든 colors 리스트


        val data = PieData(dataSet)
        data.setValueTextSize(14f)
        data.setValueTextColor(Color.BLACK)

        binding.pieChart.data = data
        binding.pieChart.description.isEnabled = false
        binding.pieChart.isDrawHoleEnabled = true
        binding.pieChart.setEntryLabelColor(Color.BLACK)
        binding.pieChart.animateY(1000)
        binding.pieChart.invalidate()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        @JvmStatic
        fun newInstance(list: List<CategoryData>, userId: String? = null) =
            SavingPerformFragment().apply {
                arguments = Bundle().apply {
                    putSerializable("categoryList", ArrayList(list))
                    userId?.let { putString("userId", it) }
                }
            }
    }
}