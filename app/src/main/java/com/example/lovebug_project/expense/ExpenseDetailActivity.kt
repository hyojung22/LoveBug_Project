package com.example.lovebug_project.expense

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.activity.OnBackPressedCallback
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.lovebug_project.data.db.AppDatabase
import com.example.lovebug_project.data.db.MyApplication
import com.example.lovebug_project.data.db.entity.Expense
import com.example.lovebug_project.databinding.ActivityExpenseDetailBinding
import com.example.lovebug_project.expense.adapter.ExpenseListAdapter
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import android.widget.Toast
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

class ExpenseDetailActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityExpenseDetailBinding
    private lateinit var expenseAdapter: ExpenseListAdapter
    private lateinit var database: AppDatabase
    
    private var selectedDate: LocalDate? = null
    private val expenses = mutableListOf<Expense>()
    
    companion object {
        private const val EXTRA_DATE = "extra_date"
        private const val EXTRA_USER_ID = "extra_user_id"
        
        fun newIntent(context: Context, date: LocalDate, userId: Int): Intent {
            return Intent(context, ExpenseDetailActivity::class.java).apply {
                putExtra(EXTRA_DATE, date.toString())
                putExtra(EXTRA_USER_ID, userId)
            }
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityExpenseDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // MyApplication을 사용하여 데이터베이스 초기화
        database = MyApplication.database
        
        // Intent extras 가져오기
        val dateString = intent.getStringExtra(EXTRA_DATE)
        val userId = intent.getIntExtra(EXTRA_USER_ID, -1)
        
        if (dateString == null || userId == -1) {
            finish()
            return
        }
        
        selectedDate = LocalDate.parse(dateString)
        
        setupToolbar()
        setupRecyclerView()
        setupFab()
        setupBackPressHandler()
        loadExpenses(userId)
    }
    
    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
            
            // 제목용 날짜 포맷 (예: "8월 1일 지출 내역")
            selectedDate?.let { date ->
                val formatter = DateTimeFormatter.ofPattern("M월 d일", Locale.KOREAN)
                title = "${date.format(formatter)} 지출 내역"
            }
        }
    }
    
    private fun setupRecyclerView() {
        expenseAdapter = ExpenseListAdapter(
            expenses = expenses,
            onItemClick = { expense -> 
                // 편집을 위해 ExpenseRegisterActivity로 이동
                val userId = intent.getIntExtra(EXTRA_USER_ID, -1)
                selectedDate?.let { date ->
                    val intent = ExpenseRegisterActivity.newIntent(this, date, userId, expense.expenseId)
                    startActivity(intent)
                }
            },
            onDeleteClick = { expense ->
                deleteExpense(expense)
            }
        )
        
        binding.recyclerViewExpenses.apply {
            layoutManager = LinearLayoutManager(this@ExpenseDetailActivity)
            adapter = expenseAdapter
        }
    }
    
    private fun setupFab() {
        binding.fabAddExpense.setOnClickListener {
            val userId = intent.getIntExtra(EXTRA_USER_ID, -1)
            selectedDate?.let { date ->
                val intent = ExpenseRegisterActivity.newIntent(this, date, userId)
                startActivity(intent)
            }
        }
    }
    
    private fun setupBackPressHandler() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                finish()
            }
        })
    }
    
    private fun loadExpenses(userId: Int) {
        selectedDate?.let { date ->
            lifecycleScope.launch {
                try {
                    val expenseList = database.expenseDao().getExpensesByDate(
                        userId = userId,
                        date = date.toString()
                    )
                    expenses.clear()
                    expenses.addAll(expenseList)
                    expenseAdapter.notifyDataSetChanged()
                } catch (e: Exception) {
                    Toast.makeText(
                        this@ExpenseDetailActivity,
                        "지출 내역을 불러오는데 실패했습니다.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }
    
    
    private fun deleteExpense(expense: Expense) {
        MaterialAlertDialogBuilder(this)
            .setTitle("지출 삭제")
            .setMessage("이 지출 내역을 삭제하시겠습니까?")
            .setNegativeButton("취소") { dialog, _ ->
                dialog.dismiss()
            }
            .setPositiveButton("삭제") { dialog, _ ->
                lifecycleScope.launch {
                    try {
                        database.expenseDao().delete(expense)
                        loadExpenses(intent.getIntExtra(EXTRA_USER_ID, -1)) // 리스트 새로고침
                        Toast.makeText(
                            this@ExpenseDetailActivity,
                            "지출이 삭제되었습니다.",
                            Toast.LENGTH_SHORT
                        ).show()
                    } catch (e: Exception) {
                        Toast.makeText(
                            this@ExpenseDetailActivity,
                            "지출 삭제에 실패했습니다.",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
                dialog.dismiss()
            }
            .show()
    }
    
    override fun onResume() {
        super.onResume()
        // ExpenseRegisterActivity에서 돌아왔을 때 리스트 새로고침
        val userId = intent.getIntExtra(EXTRA_USER_ID, -1)
        if (userId != -1) {
            loadExpenses(userId)
        }
    }
    
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}