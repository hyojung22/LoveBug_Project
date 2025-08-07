package com.example.lovebug_project.mypage

import CategoryAdapter
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.lovebug_project.databinding.FragmentSavingPerformBinding
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.github.mikephil.charting.utils.ColorTemplate
import java.text.SimpleDateFormat
import java.util.*

class SavingPerformFragment : Fragment() {

    private var _binding : FragmentSavingPerformBinding? = null
    private val binding get() = _binding!!

    private var categoryList : List<CategoryData>? = null
    private var currentCalendar: Calendar = Calendar.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            categoryList = it.getSerializable("categoryList") as? List<CategoryData>
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

        // 전달받은 데이터가 있을 경우에만 차트와 리스트를 설정
        categoryList?.let {
            // RecyclerView 설정
            val categoryAdapter = CategoryAdapter(it)
            binding.rvCategory.adapter = categoryAdapter
            binding.rvCategory.layoutManager = LinearLayoutManager(requireContext())

            // PieChart 설정
            setupPieChart(it)
        }

        // 날짜 관련 설정
        updateDateText()
        binding.imageView2.setOnClickListener {
            currentCalendar.add(Calendar.MONTH, 1)
            updateDateText()
        }
        binding.imageView3.setOnClickListener {
            currentCalendar.add(Calendar.MONTH, -1)
            updateDateText()
        }
    }

    private fun updateDateText() {
        val sdf = SimpleDateFormat("yyyy년 M월", Locale.KOREA)
        binding.date.text = sdf.format(currentCalendar.time)
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
        fun newInstance(list: List<CategoryData>) =
            SavingPerformFragment().apply {
                arguments = Bundle().apply {
                    putSerializable("categoryList", ArrayList(list))
                }
            }
    }
}