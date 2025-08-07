package com.example.lovebug_project.home

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import android.widget.NumberPicker
import android.widget.LinearLayout
import android.view.Gravity
import com.example.lovebug_project.R
import com.example.lovebug_project.databinding.FragmentHomeBinding
import com.example.lovebug_project.expense.ExpenseDetailActivity
import com.example.lovebug_project.data.supabase.models.Expense
import com.example.lovebug_project.data.repository.SupabaseRepositoryManager
import com.example.lovebug_project.data.db.MyApplication
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.kizitonwose.calendar.core.CalendarDay
import com.kizitonwose.calendar.core.DayPosition
import com.kizitonwose.calendar.core.firstDayOfWeekFromLocale
import com.kizitonwose.calendar.view.MonthDayBinder
import com.kizitonwose.calendar.view.ViewContainer
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

class HomeFragment : Fragment() {
    
    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    
    private var selectedDate: LocalDate? = null
    private var currentMonth = YearMonth.now()
    
    // Supabase Repository 인스턴스
    private val expenseRepository = SupabaseRepositoryManager.expenseRepository
    
    // 데이터베이스에서 로드된 지출 데이터 - 날짜별 총 지출 금액 맵
    private val expenseData = mutableMapOf<LocalDate, Int>()
    
    private var monthlyBudget = 100000 // 목표 금액 (기본값)
    private val currentUserId = "sample-user-id" // 샘플 유저 ID - 실제 앱에서는 Supabase Auth에서 가져와야 함
    
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // Repository 초기화 (이미 companion object로 초기화됨)
        
        setupCalendar()
        setupClickListeners()
        
        // 월별 목표 지출 금액 로드
        loadMonthlyBudget()
        
        // 현재 월의 지출 데이터 로드
        loadExpenseDataForCurrentMonth()
    }
    
    private fun setupCalendar() {
        val startMonth = currentMonth.minusMonths(100)
        val endMonth = currentMonth.plusMonths(100)
        val firstDayOfWeek = DayOfWeek.MONDAY
        
        binding.calendarView.setup(startMonth, endMonth, firstDayOfWeek)
        binding.calendarView.scrollToMonth(currentMonth)
        
        binding.calendarView.dayBinder = object : MonthDayBinder<DayViewContainer> {
            override fun create(view: View) = DayViewContainer(view)
            
            override fun bind(container: DayViewContainer, data: CalendarDay) {
                container.day = data
                val dayTextView = container.dayText
                val amountTextView = container.amountText
                
                dayTextView.text = data.date.dayOfMonth.toString()
                
                if (data.position == DayPosition.MonthDate) {
                    dayTextView.visibility = View.VISIBLE
                    
                    // 지출 금액이 있으면 표시
                    val expense = expenseData[data.date]
                    if (expense != null) {
                        amountTextView.visibility = View.VISIBLE
                        amountTextView.text = "${String.format("%,d", expense)}"
                        
                        // 지출 금액에 따라 배경색 설정
                        when {
                            expense > 15000 -> {
                                container.view.setBackgroundResource(R.drawable.calendar_day_background)
                                container.view.backgroundTintList = ContextCompat.getColorStateList(requireContext(), android.R.color.holo_red_light)
                                amountTextView.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.holo_red_dark))
                            }
                            expense > 5000 -> {
                                container.view.setBackgroundResource(R.drawable.calendar_day_background)
                                container.view.backgroundTintList = ContextCompat.getColorStateList(requireContext(), android.R.color.holo_orange_light)
                                amountTextView.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.holo_orange_dark))
                            }
                            else -> {
                                container.view.setBackgroundResource(R.drawable.calendar_day_background)
                                container.view.backgroundTintList = ContextCompat.getColorStateList(requireContext(), android.R.color.holo_blue_light)
                                amountTextView.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.holo_blue_dark))
                            }
                        }
                    } else {
                        amountTextView.visibility = View.GONE
                        container.view.setBackgroundResource(R.drawable.calendar_day_background)
                        container.view.backgroundTintList = null
                    }
                    
                    // 선택 상태 및 오늘 날짜 강조 표시 처리
                    val isToday = data.date == LocalDate.now()
                    
                    if (selectedDate == data.date) {
                        // 선택된 날짜 (최우선)
                        dayTextView.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.white))
                        container.view.setBackgroundResource(R.drawable.calendar_day_background)
                        container.view.backgroundTintList = ContextCompat.getColorStateList(requireContext(), android.R.color.holo_green_dark)
                    } else if (isToday && expense == null) {
                        // 현재 날짜이지만 지출이 없는 경우
                        dayTextView.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.black))
                        container.view.setBackgroundResource(R.drawable.calendar_today_background)
                        container.view.backgroundTintList = null
                    } else if (isToday && expense != null) {
                        // 현재 날짜이면서 지출이 있는 경우 - 기존 지출 색상 + 특별 텍스트 색상
                        dayTextView.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.holo_blue_dark))
                        dayTextView.setTypeface(null, android.graphics.Typeface.BOLD)
                        // 지출 색상은 위에서 이미 설정됨
                    } else {
                        dayTextView.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.black))
                        dayTextView.setTypeface(null, android.graphics.Typeface.NORMAL)
                    }
                } else {
                    dayTextView.visibility = View.VISIBLE
                    dayTextView.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.darker_gray))
                    amountTextView.visibility = View.GONE
                    container.view.background = null
                }
            }
        }
        
        // 스와이프 시 월 표시를 업데이트하기 위한 스크롤 리스너 추가
        binding.calendarView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                super.onScrollStateChanged(recyclerView, newState)
                
                // 스크롤이 멈췄을 때 업데이트 (사용자가 스크롤/스와이프를 완료함)
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    val visibleMonth = binding.calendarView.findFirstVisibleMonth()
                    visibleMonth?.let { month ->
                        if (currentMonth != month.yearMonth) {
                            currentMonth = month.yearMonth
                            updateMonthDisplay()
                        }
                    }
                }
            }
        })
    }
    
    private fun setupClickListeners() {
        binding.btnPrevMonth.setOnClickListener {
            currentMonth = currentMonth.minusMonths(1)
            binding.calendarView.smoothScrollToMonth(currentMonth)
            updateMonthDisplay()
        }
        
        binding.btnNextMonth.setOnClickListener {
            currentMonth = currentMonth.plusMonths(1)
            binding.calendarView.smoothScrollToMonth(currentMonth)
            updateMonthDisplay()
        }
        
        binding.tvCurrentMonth.setOnClickListener {
            showMonthYearPickerDialog()
        }
        
        binding.llTargetAmount.setOnClickListener {
            showTargetAmountEditDialog()
        }
    }
    
    private fun updateMonthDisplay() {
        val formatter = DateTimeFormatter.ofPattern("yyyy년 M월", Locale.KOREAN)
        binding.tvCurrentMonth.text = currentMonth.format(formatter)
        
        // 월이 변경될 때마다 해당 월의 목표 금액 로드
        loadMonthlyBudget()
        
        // 해당 월의 지출 데이터 로드
        loadExpenseDataForCurrentMonth()
    }
    
    private fun updateExpenseInfo() {
        val monthExpenses = expenseData.filter { 
            YearMonth.from(it.key) == currentMonth 
        }.values.sum()
        
        binding.tvTargetAmount.text = String.format("%,d", monthlyBudget)
        binding.tvTotalExpense.text = String.format("%,d", monthExpenses)
        binding.tvRemainingAmount.text = String.format("%,d", monthlyBudget - monthExpenses)
        
        // 월 표시 업데이트
        val formatter = DateTimeFormatter.ofPattern("yyyy년 M월", Locale.KOREAN)
        binding.tvCurrentMonth.text = currentMonth.format(formatter)
    }
    
    private fun loadExpenseDataForCurrentMonth() {
        lifecycleScope.launch {
            try {
                // Supabase 쿼리를 위한 날짜 범위 계산
                val startDate = currentMonth.atDay(1).toString() // "2025-08-01"
                val endDate = currentMonth.atEndOfMonth().toString() // "2025-08-31"
                
                // Supabase에서 현재 월의 지출 내역 로드 (IO 스레드에서 실행)
                val expensesResult = withContext(Dispatchers.IO) {
                    expenseRepository.getExpensesByDateRange(currentUserId, startDate, endDate)
                }
                
                if (expensesResult.isFailure) {
                    // 에러 처리
                    expensesResult.exceptionOrNull()?.printStackTrace()
                    return@launch
                }
                
                val expenses = expensesResult.getOrNull() ?: emptyList()
                
                // 현재 데이터를 초기화하고 일별 총계 계산
                expenseData.clear()
                
                // 날짜별로 지출을 그룹화하고 금액 합계 계산
                expenses.groupBy { expense ->
                    LocalDate.parse(expense.date)
                }.forEach { (date, expensesForDate) ->
                    val dailyTotal = expensesForDate.sumOf { it.amount }
                    expenseData[date] = dailyTotal
                }
                
                // UI 업데이트
                updateExpenseInfo()
                
                // 새로운 데이터를 보여주기 위해 캘린더 새로고침
                binding.calendarView.notifyCalendarChanged()
                
            } catch (e: Exception) {
                // 에러를 조용히 처리하거나 토스트 표시
                e.printStackTrace()
                // 선택사항: 에러 토스트 표시
                // Toast.makeText(requireContext(), "지출 데이터 로드 실패", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun onDateClick(day: CalendarDay) {
        when (day.position) {
            DayPosition.MonthDate -> {
                val currentSelection = selectedDate
                if (currentSelection == day.date) {
                    selectedDate = null
                    binding.calendarView.notifyDateChanged(currentSelection)
                } else {
                    selectedDate = day.date
                    binding.calendarView.notifyDateChanged(day.date)
                    if (currentSelection != null) {
                        binding.calendarView.notifyDateChanged(currentSelection)
                    }
                    
                    // 상세 화면으로 이동
                    
                    // ExpenseDetailActivity로 이동
                    val intent = ExpenseDetailActivity.newIntent(
                        requireContext(), 
                        day.date
                    )
                    startActivity(intent)
                }
            }
            DayPosition.InDate -> {
                // 이전 월로 이동
                currentMonth = currentMonth.minusMonths(1)
                binding.calendarView.smoothScrollToMonth(currentMonth)
                updateMonthDisplay()
                
                // 해당 일자 선택 및 터치 이벤트 처리
                val currentSelection = selectedDate
                selectedDate = day.date
                if (currentSelection != null) {
                    binding.calendarView.notifyDateChanged(currentSelection)
                }
                binding.calendarView.notifyDateChanged(day.date)
                
                // 상세 화면으로 이동
                
                // ExpenseDetailActivity로 이동
                val intent = ExpenseDetailActivity.newIntent(
                    requireContext(), 
                    day.date
                )
                startActivity(intent)
            }
            DayPosition.OutDate -> {
                // 다음 월로 이동
                currentMonth = currentMonth.plusMonths(1)
                binding.calendarView.smoothScrollToMonth(currentMonth)
                updateMonthDisplay()
                
                // 해당 일자 선택 및 터치 이벤트 처리
                val currentSelection = selectedDate
                selectedDate = day.date
                if (currentSelection != null) {
                    binding.calendarView.notifyDateChanged(currentSelection)
                }
                binding.calendarView.notifyDateChanged(day.date)
                
                // 상세 화면으로 이동
                
                // ExpenseDetailActivity로 이동
                val intent = ExpenseDetailActivity.newIntent(
                    requireContext(), 
                    day.date
                )
                startActivity(intent)
            }
        }
    }
    
    inner class DayViewContainer(view: View) : ViewContainer(view) {
        val dayText: TextView = view.findViewById(R.id.tv_day)
        val amountText: TextView = view.findViewById(R.id.tv_amount)
        lateinit var day: CalendarDay
        
        init {
            view.setOnClickListener {
                onDateClick(day)
            }
        }
    }
    
    private fun showMonthYearPickerDialog() {
        // 메인 레이아웃 생성
        val dialogLayout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 32, 48, 24) // Material Design 스페이싱
            gravity = Gravity.CENTER
        }
        
        // 연도와 월을 나란히 배치할 가로 레이아웃
        val pickerLayout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        
        // 연도 섹션 컨테이너
        val yearContainer = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(16, 0, 16, 0)
        }
        
        // 연도 NumberPicker 설정
        val yearPicker = NumberPicker(requireContext()).apply {
            val currentYear = currentMonth.year
            minValue = currentYear - 10
            maxValue = currentYear + 10
            value = currentYear
            wrapSelectorWheel = false
        }
        
        val yearLabel = TextView(requireContext()).apply {
            text = "년"
            textSize = 16f
            setTextColor(ContextCompat.getColor(requireContext(), android.R.color.black))
            gravity = Gravity.CENTER
            setPadding(0, 8, 0, 0)
        }
        
        yearContainer.addView(yearPicker)
        yearContainer.addView(yearLabel)
        
        // 월 섹션 컨테이너
        val monthContainer = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(16, 0, 16, 0)
        }
        
        // 월 NumberPicker 설정
        val monthPicker = NumberPicker(requireContext()).apply {
            minValue = 1
            maxValue = 12
            value = currentMonth.monthValue
            wrapSelectorWheel = false
        }
        
        val monthLabel = TextView(requireContext()).apply {
            text = "월"
            textSize = 16f
            setTextColor(ContextCompat.getColor(requireContext(), android.R.color.black))
            gravity = Gravity.CENTER
            setPadding(0, 8, 0, 0)
        }
        
        monthContainer.addView(monthPicker)
        monthContainer.addView(monthLabel)
        
        // 컨테이너들을 가로 레이아웃에 추가
        pickerLayout.addView(yearContainer)
        pickerLayout.addView(monthContainer)
        
        // 메인 레이아웃에 가로 레이아웃 추가
        dialogLayout.addView(pickerLayout)
        
        // Material AlertDialog 생성
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("연월 선택")
            .setView(dialogLayout)
            .setNegativeButton("취소") { dialog, _ ->
                dialog.dismiss()
            }
            .setPositiveButton("확인") { dialog, _ ->
                val selectedYear = yearPicker.value
                val selectedMonth = monthPicker.value
                
                currentMonth = YearMonth.of(selectedYear, selectedMonth)
                
                // 애니메이션 비활성화를 위해 scrollToMonth 사용
                binding.calendarView.scrollToMonth(currentMonth)
                updateMonthDisplay()
                
                dialog.dismiss()
            }
            .show()
    }
    
    private fun showTargetAmountEditDialog() {
        // TextInputLayout과 TextInputEditText를 포함한 레이아웃 생성
        val dialogLayout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 32, 48, 24) // Material Design 스페이싱
        }
        
        // TextInputLayout 생성
        val textInputLayout = com.google.android.material.textfield.TextInputLayout(requireContext()).apply {
            hint = "목표 금액을 입력하세요"
            boxBackgroundMode = com.google.android.material.textfield.TextInputLayout.BOX_BACKGROUND_OUTLINE
        }
        
        // TextInputEditText 생성
        val editText = com.google.android.material.textfield.TextInputEditText(requireContext()).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setText(monthlyBudget.toString())
            selectAll() // 전체 텍스트 선택
        }
        
        // EditText를 TextInputLayout에 추가
        textInputLayout.addView(editText)
        dialogLayout.addView(textInputLayout)
        
        // MaterialAlertDialog 생성
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("지출 목표 금액 설정")
            .setView(dialogLayout)
            .setNegativeButton("취소") { dialog, _ ->
                dialog.dismiss()
            }
            .setPositiveButton("확인") { dialog, _ ->
                val inputText = editText.text.toString()
                if (inputText.isNotEmpty()) {
                    try {
                        val newBudget = inputText.toInt()
                        if (newBudget > 0) {
                            // Supabase에 현재 월의 목표 지출 금액 저장
                            lifecycleScope.launch {
                                try {
                                    // 현재 월을 YYYY-MM 형식으로 변환
                                    val currentYearMonth = currentMonth.toString() // "2025-01" 형식
                                    
                                    val result = MyApplication.authRepository.setMonthlyBudgetForMonth(
                                        currentYearMonth, newBudget
                                    )
                                    
                                    result.fold(
                                        onSuccess = { userInfo ->
                                            // 성공 시 로컬 값도 업데이트 (현재 월에만 적용)
                                            monthlyBudget = newBudget
                                            
                                            // ❌ SharedPreferences 업데이트 제거
                                            // 특정 월의 목표는 기본값(expense_goal)과 별개로 관리
                                            
                                            updateExpenseInfo() // UI 업데이트
                                            val formatter = DateTimeFormatter.ofPattern("yyyy년 M월", Locale.KOREAN)
                                            val monthDisplay = currentMonth.format(formatter)
                                            Toast.makeText(requireContext(), 
                                                "${monthDisplay} 목표 금액이 ${String.format("%,d", newBudget)}원으로 설정되었습니다.", 
                                                Toast.LENGTH_SHORT).show()
                                        },
                                        onFailure = { exception ->
                                            // 실패 시 오류 메시지만 표시 (로컬 저장 안함)
                                            Toast.makeText(requireContext(), 
                                                "목표 금액 저장에 실패했습니다: ${exception.message}", 
                                                Toast.LENGTH_LONG).show()
                                        }
                                    )
                                } catch (e: Exception) {
                                    // 예외 발생 시 오류 메시지만 표시
                                    Toast.makeText(requireContext(), 
                                        "목표 금액 저장 중 오류가 발생했습니다: ${e.message}", 
                                        Toast.LENGTH_LONG).show()
                                }
                            }
                        } else {
                            Toast.makeText(requireContext(), "0보다 큰 금액을 입력해주세요.", Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: NumberFormatException) {
                        Toast.makeText(requireContext(), "올바른 금액을 입력해주세요.", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(requireContext(), "금액을 입력해주세요.", Toast.LENGTH_SHORT).show()
                }
                dialog.dismiss()
            }
            .show()
    }
    
    /**
     * Supabase에서 현재 월의 목표 지출 금액 로드
     */
    private fun loadMonthlyBudget() {
        lifecycleScope.launch {
            try {
                // 현재 월을 YYYY-MM 형식으로 변환
                val currentYearMonth = currentMonth.toString() // "2025-01" 형식
                
                // Supabase에서 현재 월의 목표 지출 금액 가져오기
                // 이 메소드는 다음 우선순위로 값을 반환:
                // 1. 특정 월 설정값 2. default 값 3. legacy monthly_budget 4. null
                val supabaseBudget = MyApplication.authRepository.getMonthlyBudgetForMonth(currentYearMonth)
                
                if (supabaseBudget != null && supabaseBudget > 0) {
                    monthlyBudget = supabaseBudget
                } else {
                    // Supabase에 아무 설정도 없는 경우에만 SharedPreferences fallback
                    val sharedPref = requireContext().getSharedPreferences("app_settings", Context.MODE_PRIVATE)
                    monthlyBudget = sharedPref.getLong("expense_goal", 100000L).toInt()
                }
                
                // UI 업데이트
                updateExpenseInfo()
                
            } catch (e: Exception) {
                // 에러 발생 시 SharedPreferences fallback
                val sharedPref = requireContext().getSharedPreferences("app_settings", Context.MODE_PRIVATE)
                monthlyBudget = sharedPref.getLong("expense_goal", 100000L).toInt()
                updateExpenseInfo()
            }
        }
    }
    
    override fun onResume() {
        super.onResume()
        // ExpenseDetailActivity에서 돌아올 때 지출 데이터 새로고침
        loadExpenseDataForCurrentMonth()
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}