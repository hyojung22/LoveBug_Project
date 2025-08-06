package com.example.lovebug_project.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.example.lovebug_project.R
import com.example.lovebug_project.databinding.FragmentHomeBinding
import com.kizitonwose.calendar.core.CalendarDay
import com.kizitonwose.calendar.core.DayPosition
import com.kizitonwose.calendar.core.firstDayOfWeekFromLocale
import com.kizitonwose.calendar.view.MonthDayBinder
import com.kizitonwose.calendar.view.ViewContainer
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

class HomeFragment : Fragment() {
    
    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    
    private var selectedDate: LocalDate? = null
    private var currentMonth = YearMonth.now()
    
    // Sample expense data - in real app, this would come from database/repository
    private val expenseData = mutableMapOf<LocalDate, Int>().apply {
        put(LocalDate.of(2025, 8, 1), 8900)
        put(LocalDate.of(2025, 8, 2), 20000)
        put(LocalDate.of(2025, 8, 5), 15000)
        put(LocalDate.of(2025, 8, 10), 5000)
        put(LocalDate.of(2025, 8, 15), 12000)
    }
    
    private val monthlyBudget = 100000 // 목표 금액
    
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupCalendar()
        setupClickListeners()
        updateExpenseInfo()
    }
    
    private fun setupCalendar() {
        val startMonth = currentMonth.minusMonths(100)
        val endMonth = currentMonth.plusMonths(100)
        val firstDayOfWeek = firstDayOfWeekFromLocale()
        
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
                    
                    // Show expense amount if exists
                    val expense = expenseData[data.date]
                    if (expense != null) {
                        amountTextView.visibility = View.VISIBLE
                        amountTextView.text = "${String.format("%,d", expense)}"
                        
                        // Set background color based on expense amount
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
                    
                    // Handle selection
                    if (selectedDate == data.date) {
                        dayTextView.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.white))
                        container.view.setBackgroundResource(R.drawable.calendar_day_background)
                        container.view.backgroundTintList = ContextCompat.getColorStateList(requireContext(), android.R.color.holo_green_dark)
                    } else {
                        dayTextView.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.black))
                    }
                } else {
                    dayTextView.visibility = View.VISIBLE
                    dayTextView.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.darker_gray))
                    amountTextView.visibility = View.GONE
                    container.view.background = null
                }
            }
        }
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
    }
    
    private fun updateMonthDisplay() {
        val formatter = DateTimeFormatter.ofPattern("yyyy년 M월", Locale.KOREAN)
        binding.tvCurrentMonth.text = currentMonth.format(formatter)
        updateExpenseInfo()
    }
    
    private fun updateExpenseInfo() {
        val monthExpenses = expenseData.filter { 
            YearMonth.from(it.key) == currentMonth 
        }.values.sum()
        
        binding.tvTargetAmount.text = String.format("%,d", monthlyBudget)
        binding.tvTotalExpense.text = String.format("%,d", monthExpenses)
        binding.tvRemainingAmount.text = String.format("%,d", monthlyBudget - monthExpenses)
        
        // Update month display
        val formatter = DateTimeFormatter.ofPattern("yyyy년 M월", Locale.KOREAN)
        binding.tvCurrentMonth.text = currentMonth.format(formatter)
    }
    
    private fun onDateClick(day: CalendarDay) {
        if (day.position == DayPosition.MonthDate) {
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
                
                // Show expense registration option
                val existingExpense = expenseData[day.date]
                val message = if (existingExpense != null) {
                    "${day.date.monthValue}월 ${day.date.dayOfMonth}일 지출: ${String.format("%,d", existingExpense)}원"
                } else {
                    "${day.date.monthValue}월 ${day.date.dayOfMonth}일에 지출을 등록하세요!"
                }
                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
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
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}