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
import com.example.lovebug_project.databinding.FragmentSavingPerformBinding // 이미 import 되어 있음
import com.example.lovebug_project.data.db.MyApplication
import com.example.lovebug_project.utils.AuthHelper
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.github.mikephil.charting.utils.ColorTemplate // 사용되지 않는 import는 유지
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*
// ImageView 또는 Button 등 btnBack4의 실제 타입에 따라 import가 필요할 수 있습니다.
// 예: import android.widget.ImageView

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

        // *** btnBack4 클릭 리스너 설정 ***
        // fragment_saving_perform.xml에 <... android:id="@+id/btnBack4" /> 가 정의되어 있어야 합니다.
        // binding 객체를 통해 ID가 btnBack4인 뷰에 접근합니다.
        binding.btnBack4.setOnClickListener {
            // Activity의 OnBackPressedDispatcher를 통해 뒤로가기 이벤트를 전달합니다.
            activity?.onBackPressedDispatcher?.onBackPressed()
        }
        // **********************************

        // RecyclerView 초기 설정
        binding.rvCategory.layoutManager = LinearLayoutManager(requireContext())

        // 초기 데이터 로드 (전달받은 데이터가 있어도 현재 월 데이터를 로드)
        // 원본 코드에서는 categoryList를 인자로 받지 않고, 내부의 this.categoryList를 사용하거나,
        // 혹은 loadSavingDataForCurrentMonth()가 arguments에서 받은 categoryList를 무시하고 새로 로드하는 구조일 수 있습니다.
        // 제공해주신 코드의 loadSavingDataForCurrentMonth는 arguments의 categoryList를 사용하지 않고 항상 새로 데이터를 불러옵니다.
        loadSavingDataForCurrentMonth()

        // 날짜 관련 설정
        updateDateText()
        binding.imageView2.setOnClickListener {
            currentCalendar.add(Calendar.MONTH, 1)
            updateDateText()
            loadSavingDataForCurrentMonth()
        }
        binding.ivSp.setOnClickListener {
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
        val userId = currentUserId // onViewCreated에서 설정된 currentUserId 사용
        if (userId == null) {
            Toast.makeText(requireContext(), "사용자 정보를 찾을 수 없습니다.", Toast.LENGTH_SHORT).show()
            // 사용자 정보가 없는 경우, arguments에서 받은 categoryList가 있다면 그것을 사용하거나,
            // 아니면 기본 UI를 보여주는 로직이 필요할 수 있습니다.
            // 현재 코드는 여기서 return하여 아무것도 표시하지 않게 됩니다.
            // arguments에서 받은 categoryList를 활용하는 방안:
            // if (categoryList != null && categoryList!!.isNotEmpty()) {
            //     updateUIWithData(categoryList!!)
            // } else {
            //     // 기본 "데이터 없음" UI 표시
            //     val defaultList = listOf(CategoryData("데이터 없음", 100, "#E0E0E0"))
            //     updateUIWithData(defaultList)
            // }
            return
        }

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // 현재 선택된 월의 YYYY-MM 형식 문자열 생성
                val dateFormat = SimpleDateFormat("yyyy-MM", Locale.getDefault())
                val yearMonth = dateFormat.format(currentCalendar.time)

                // 디버깅용 로그
                android.util.Log.d("SavingPerform", "Loading data for: $yearMonth, UserID: $userId")

                // 절약성과 데이터 조회
                val result = MyApplication.savingRepository.getSavingPerformanceAsCategoryData(userId, yearMonth)

                withContext(Dispatchers.Main) {
                    result.fold(
                        onSuccess = { fetchedCategoryDataList -> // 변수명 변경 (this.categoryList와 구분)
                            android.util.Log.d("SavingPerform", "Received data size: ${fetchedCategoryDataList.size}")
                            fetchedCategoryDataList.forEach { category ->
                                android.util.Log.d("SavingPerform", "Category: ${category.cg_name}, Percentage: ${category.percentage}%")
                            }

                            if (fetchedCategoryDataList.isEmpty()) {
                                android.util.Log.d("SavingPerform", "Empty data, showing default")
                                val defaultList = listOf(
                                    CategoryData("데이터 없음", 100, "#E0E0E0")
                                )
                                updateUIWithData(defaultList)
                            } else {
                                updateUIWithData(fetchedCategoryDataList)
                            }
                        },
                        onFailure = { exception ->
                            android.util.Log.e("SavingPerform", "Data loading failed: ${exception.message}")
                            val defaultList = listOf(
                                CategoryData("데이터 없음", 100, "#E0E0E0")
                            )
                            updateUIWithData(defaultList)
                            Toast.makeText(requireContext(), "데이터 로딩 실패: ${exception.message}", Toast.LENGTH_LONG).show()
                        }
                    )
                }
            } catch (e: Exception) {
                android.util.Log.e("SavingPerform", "Exception in loadSavingDataForCurrentMonth: ${e.message}")
                withContext(Dispatchers.Main) {
                    val defaultList = listOf(CategoryData("데이터 없음", 100, "#E0E0E0"))
                    updateUIWithData(defaultList)
                    Toast.makeText(requireContext(), "데이터 로딩 중 오류: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun updateUIWithData(categories: List<CategoryData>) {
        // RecyclerView 업데이트 (항상 새 어댑터 생성)
        categoryAdapter = CategoryAdapter(categories) // 원본 코드 유지
        binding.rvCategory.adapter = categoryAdapter

        // PieChart 업데이트
        setupPieChart(categories)
    }

    private fun setupPieChart(categories: List<CategoryData>) {
        val entries = ArrayList<PieEntry>()
        val pieColors = ArrayList<Int>() // 변수명 변경 (Color와 구분)

        if (categories.isEmpty() || categories.all { it.cg_name == "데이터 없음" && it.percentage == 100 }) {
            // 데이터가 없거나 "데이터 없음"만 있는 경우
            entries.add(PieEntry(100f, "데이터 없음"))
            try {
                pieColors.add(Color.parseColor("#E0E0E0")) // 기본 회색
            } catch (e: IllegalArgumentException) {
                pieColors.add(Color.GRAY)
            }
        } else {
            for (category in categories) {
                entries.add(PieEntry(category.percentage.toFloat(), category.cg_name))
                try {
                    pieColors.add(Color.parseColor(category.color)) // category.color 사용
                } catch (e: IllegalArgumentException) {
                    android.util.Log.w("SavingPerform", "Invalid color string: ${category.color}")
                    pieColors.add(Color.LTGRAY) // 잘못된 색상 코드일 경우 기본 색상
                }
            }
        }

        val dataSet = PieDataSet(entries, "Categories") // 원본 코드 유지
        dataSet.colors = pieColors // 새로 만든 colors 리스트

        val data = PieData(dataSet)
        data.setValueTextSize(14f) // 원본 코드 유지
        data.setValueTextColor(Color.BLACK) // 원본 코드 유지

        binding.pieChart.data = data
        binding.pieChart.description.isEnabled = false // 원본 코드 유지
        binding.pieChart.isDrawHoleEnabled = true // 원본 코드 유지
        binding.pieChart.setEntryLabelColor(Color.BLACK) // 원본 코드 유지
        binding.pieChart.animateY(1000) // 원본 코드 유지
        binding.pieChart.invalidate() // 원본 코드 유지
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // companion object는 원본 그대로 유지합니다.
    companion object {
        @JvmStatic
        fun newInstance(list: List<CategoryData>, userId: String? = null) =
            SavingPerformFragment().apply {
                arguments = Bundle().apply {
                    putSerializable("categoryList", ArrayList(list)) // 직렬화 가능한 리스트로 변환
                    userId?.let { putString("userId", it) }
                }
            }
    }
}
