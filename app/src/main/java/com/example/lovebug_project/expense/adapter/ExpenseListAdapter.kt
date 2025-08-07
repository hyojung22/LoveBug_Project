package com.example.lovebug_project.expense.adapter

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.lovebug_project.R
import com.example.lovebug_project.data.supabase.models.Expense
import com.example.lovebug_project.databinding.ItemExpenseBinding
import kotlin.random.Random

class ExpenseListAdapter(
    private val expenses: MutableList<Expense>,
    private val onItemClick: (Expense) -> Unit,
    private val onDeleteClick: (Expense) -> Unit
) : RecyclerView.Adapter<ExpenseListAdapter.ExpenseViewHolder>() {

    // 카테고리별 미리 정의된 색상들
    private val categoryColors = arrayOf(
        "#FFEB3B", // 노란색
        "#FF9800", // 주황색
        "#F44336", // 빨간색
        "#E91E63", // 분홍색
        "#9C27B0", // 보라색
        "#673AB7", // 진한 보라색
        "#3F51B5", // 인디고
        "#2196F3", // 파란색
        "#03DAC5", // 청록색
        "#4CAF50", // 초록색
        "#8BC34A", // 연한 초록색
        "#CDDC39", // 라임색
        "#FFC107", // 호박색
        "#FF5722"  // 진한 주황색
    )

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ExpenseViewHolder {
        val binding = ItemExpenseBinding.inflate(
            LayoutInflater.from(parent.context), 
            parent, 
            false
        )
        return ExpenseViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ExpenseViewHolder, position: Int) {
        holder.bind(expenses[position])
    }

    override fun getItemCount(): Int = expenses.size

    inner class ExpenseViewHolder(
        private val binding: ItemExpenseBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(expense: Expense) {
            // 카테고리 아이콘과 색상 설정
            binding.tvCategoryIcon.text = expense.category
            binding.tvCategoryIcon.setBackgroundColor(
                Color.parseColor(getCategoryColor(expense.category))
            )
            
            // 적절한 포맷으로 금액 설정
            binding.tvAmount.text = "${String.format("%,.0f", expense.amount)}원"
            
            // 메모 설정 (비어있으면 플레이스홀더 표시)
            binding.tvMemo.text = if (expense.memo.isNullOrEmpty()) {
                "한줄 메모"
            } else {
                expense.memo
            }
            
            // 아이템 클릭 처리
            binding.root.setOnClickListener {
                onItemClick(expense)
            }

            // 삭제/편집 클릭 처리
            binding.ivDelete.setOnClickListener {
                onDeleteClick(expense)
            }
        }

        private fun getCategoryColor(category: String): String {
            // 동일한 카테고리에 일관된 색상을 얻기 위해 카테고리 이름의 해시값 사용
            val hash = Math.abs(category.hashCode())
            return categoryColors[hash % categoryColors.size]
        }
    }
}