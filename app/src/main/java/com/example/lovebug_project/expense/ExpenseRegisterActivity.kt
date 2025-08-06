package com.example.lovebug_project.expense

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.InputFilter
import android.text.InputType
import android.text.TextWatcher
import android.util.Log
import android.view.MenuItem
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.activity.OnBackPressedCallback
import com.example.lovebug_project.R
import com.example.lovebug_project.data.db.AppDatabase
import com.example.lovebug_project.data.db.MyApplication
import com.example.lovebug_project.data.db.entity.Expense
import com.example.lovebug_project.data.db.entity.User
import com.example.lovebug_project.databinding.ActivityExpenseRegisterBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

class ExpenseRegisterActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityExpenseRegisterBinding
    private lateinit var database: AppDatabase
    
    private var selectedDate: LocalDate? = null
    private var selectedCategory = "식비" // 기본 카테고리
    private val categories = arrayOf("식비", "교통비", "쇼핑", "엔터테인먼트", "의료", "기타")
    private val categoryButtons = mutableListOf<Button>()
    
    // 수정 모드에서 원본 데이터 저장용
    private var originalAmount: Int? = null
    private var originalCategory: String? = null
    private var originalMemo: String? = null
    
    companion object {
        private const val EXTRA_DATE = "extra_date"
        private const val EXTRA_USER_ID = "extra_user_id"
        private const val EXTRA_EXPENSE_ID = "extra_expense_id" // 수정 모드용
        
        fun newIntent(context: Context, date: LocalDate, userId: Int, expenseId: Int? = null): Intent {
            return Intent(context, ExpenseRegisterActivity::class.java).apply {
                putExtra(EXTRA_DATE, date.toString())
                putExtra(EXTRA_USER_ID, userId)
                expenseId?.let { putExtra(EXTRA_EXPENSE_ID, it) }
            }
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityExpenseRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // 데이터베이스 초기화
        database = MyApplication.database
        
        // Intent extras 가져오기
        val dateString = intent.getStringExtra(EXTRA_DATE)
        val userId = intent.getIntExtra(EXTRA_USER_ID, -1)
        val expenseId = intent.getIntExtra(EXTRA_EXPENSE_ID, -1)
        
        if (dateString == null || userId == -1) {
            finish()
            return
        }
        
        selectedDate = LocalDate.parse(dateString)
        
        setupToolbar()
        setupCategoryButtons()
        setupAmountTextWatcher()
        setupClickListeners()
        setupBackPressHandler()
        
        // 테스트: 즉시 포맷팅된 값 설정
//        Log.d("ExpenseRegister", "Testing immediate formatting")
//        binding.etAmount.setText("1234567")
//        Log.d("ExpenseRegister", "Set text to 1234567, current text: '${binding.etAmount.text}'")
        
        // 사용자 존재 확인 및 생성
        ensureUserExists(userId)
        
        // 수정 모드인 경우 기존 데이터 로드
        if (expenseId != -1) {
            loadExpenseData(expenseId)
        } else {
            // 새 등록 모드인 경우 기본 카테고리 선택
            selectCategory(0)
        }
    }
    
    /**
     * 사용자 존재 확인 및 임시 사용자 생성
     * Foreign Key 제약조건 위반을 방지하기 위함
     */
    private fun ensureUserExists(userId: Int) {
        lifecycleScope.launch {
            try {
                val existingUser = database.userDao().getUserById(userId)
                if (existingUser == null) {
                    // 임시 사용자 생성
                    val tempUser = User(
                        userId = userId,
                        username = "temp_user_$userId",
                        nickname = "사용자 $userId",
                        userLoginId = "temp_$userId",
                        password = "temp_password",
                        profileImage = null,
                        sharedSavingStats = false
                    )
                    database.userDao().insert(tempUser)
                }
            } catch (e: Exception) {
                // 사용자 생성 실패 시 로그만 남기고 계속 진행
                // 실제 앱에서는 적절한 에러 처리가 필요
                e.printStackTrace()
            }
        }
    }
    
    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
            title = ""
        }
    }
    
    private fun setupCategoryButtons() {
        categoryButtons.clear()
        categoryButtons.addAll(listOf(
            binding.btnCategory1,
            binding.btnCategory2,
            binding.btnCategory3,
            binding.btnCategory4,
            binding.btnCategory5,
            binding.btnCategory6
        ))
        
        // 카테고리 버튼에 텍스트 설정
        categories.forEachIndexed { index, category ->
            if (index < categoryButtons.size) {
                categoryButtons[index].text = category
                categoryButtons[index].setOnClickListener {
                    selectCategory(index)
                }
            }
        }
    }
    
    private fun setupAmountTextWatcher() {
        // InputType을 프로그래밍적으로 설정
        binding.etAmount.inputType = InputType.TYPE_CLASS_TEXT
        
        // 최대 길이 제한 (10자리 + 콤마들)
        val maxLength = InputFilter.LengthFilter(15)
        binding.etAmount.filters = arrayOf(maxLength)
        
        Log.d("ExpenseRegister", "Setting up TextWatcher")
        
        binding.etAmount.addTextChangedListener(object : TextWatcher {
            private var isFormatting = false
            
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            
            override fun afterTextChanged(s: Editable?) {
                if (isFormatting) return
                
                val input = s.toString()
                Log.d("ExpenseRegister", "TextWatcher triggered with input: '$input'")
                
                isFormatting = true
                
                // 숫자가 아닌 문자 제거
                val digitsOnly = input.filter { it.isDigit() }
                Log.d("ExpenseRegister", "Digits only: '$digitsOnly'")
                
                if (digitsOnly.isEmpty()) {
                    s?.clear()
                } else {
                    try {
                        val number = digitsOnly.toLong()
                        val formatted = addCommas(number.toString())
                        Log.d("ExpenseRegister", "Number: $number, Formatted: '$formatted'")
                        
                        if (input != formatted) {
                            s?.replace(0, s.length, formatted)
                        }
                    } catch (e: Exception) {
                        Log.e("ExpenseRegister", "Error formatting: ${e.message}")
                    }
                }
                
                isFormatting = false
            }
        })
    }
    
    
    private fun addCommas(numberStr: String): String {
        if (numberStr.length <= 3) return numberStr
        
        val reversed = numberStr.reversed()
        val chunks = mutableListOf<String>()
        
        for (i in reversed.indices step 3) {
            val end = minOf(i + 3, reversed.length)
            chunks.add(reversed.substring(i, end))
        }
        
        val result = chunks.joinToString(",").reversed()
        Log.d("ExpenseRegister", "addCommas input: '$numberStr', output: '$result'")
        return result
    }
    
    private fun formatAmountWithComma(amount: Long): String {
        return addCommas(amount.toString())
    }
    
    private fun selectCategory(index: Int) {
        // 모든 버튼 선택 해제
        categoryButtons.forEach { button ->
            button.isSelected = false
        }
        
        // 선택된 버튼 활성화
        if (index < categoryButtons.size) {
            categoryButtons[index].isSelected = true
            selectedCategory = categories[index]
        }
    }
    
    private fun setupClickListeners() {
        binding.ivDelete.setOnClickListener {
            showDeleteConfirmDialog()
        }
        
        binding.btnAddExpense.setOnClickListener {
            saveExpense()
        }
    }
    
    private fun setupBackPressHandler() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                handleBackNavigation()
            }
        })
    }
    
    private fun handleBackNavigation() {
        val expenseId = intent.getIntExtra(EXTRA_EXPENSE_ID, -1)
        val amountText = binding.etAmount.text.toString().replace(",", "").trim()
        val memo = binding.etMemo.text.toString().trim()
        
        if (expenseId != -1) {
            // 수정 모드인 경우 - 원본 데이터와 현재 데이터 비교
            val hasChanges = hasDataChanged(amountText, memo)
            if (hasChanges) {
                showExitConfirmDialog()
            } else {
                // 변경사항이 없으면 바로 나가기
                finish()
            }
        } else {
            // 신규 등록 모드인 경우 - 기존 로직 유지
            if (amountText.isEmpty() || amountText == "0") {
                // 금액이 입력되지 않은 경우 바로 나가기
                finish()
            } else {
                // 금액이 입력된 경우 확인 다이얼로그 표시
                showExitConfirmDialog()
            }
        }
    }
    
    private fun hasDataChanged(amountText: String, memo: String): Boolean {
        // 금액 비교
        val currentAmount = try {
            if (amountText.isEmpty()) 0 else amountText.toInt()
        } catch (e: NumberFormatException) {
            0
        }
        
        // 원본 데이터와 현재 데이터 비교
        return originalAmount != currentAmount ||
               originalCategory != selectedCategory ||
               originalMemo != memo
    }
    
    private fun showExitConfirmDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle("지출 등록")
            .setMessage("입력한 내용이 있습니다. 어떻게 하시겠습니까?")
            .setNegativeButton("저장하지 않고 나가기") { dialog, _ ->
                dialog.dismiss()
                finish()
            }
            .setPositiveButton("저장하고 나가기") { dialog, _ ->
                dialog.dismiss()
                saveExpense()
            }
            .setNeutralButton("취소") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }
    
    private fun loadExpenseData(expenseId: Int) {
        lifecycleScope.launch {
            try {
                val expense = database.expenseDao().getExpenseById(expenseId)
                expense?.let { 
                    // 원본 데이터 저장 (비교용)
                    originalAmount = it.amount
                    originalCategory = it.category
                    originalMemo = it.memo
                    
                    // 금액 설정 (쉼표 포함 형태로)
                    binding.etAmount.setText(formatAmountWithComma(it.amount.toLong()))
                    
                    // 카테고리 선택
                    val categoryIndex = categories.indexOf(it.category)
                    if (categoryIndex != -1) {
                        selectCategory(categoryIndex)
                    }
                    
                    // 메모 설정
                    binding.etMemo.setText(it.memo)
                }
            } catch (e: Exception) {
                Toast.makeText(this@ExpenseRegisterActivity, "데이터를 불러오는데 실패했습니다.", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun saveExpense() {
        val amountText = binding.etAmount.text.toString().replace(",", "").trim()
        val memo = binding.etMemo.text.toString().trim()
        val userId = intent.getIntExtra(EXTRA_USER_ID, -1)
        val expenseId = intent.getIntExtra(EXTRA_EXPENSE_ID, -1)
        
        if (amountText.isEmpty()) {
            Toast.makeText(this, "금액을 입력해주세요.", Toast.LENGTH_SHORT).show()
            return
        }
        
        try {
            val amount = amountText.toInt()
            if (amount <= 0) {
                Toast.makeText(this, "0보다 큰 금액을 입력해주세요.", Toast.LENGTH_SHORT).show()
                return
            }
            
            selectedDate?.let { date ->
                if (expenseId != -1) {
                    // 수정 모드
                    updateExpense(expenseId, selectedCategory, amount, memo)
                } else {
                    // 새 등록 모드
                    addNewExpense(userId, date, selectedCategory, amount, memo)
                }
            }
            
        } catch (e: NumberFormatException) {
            Toast.makeText(this, "올바른 금액을 입력해주세요.", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun addNewExpense(userId: Int, date: LocalDate, category: String, amount: Int, memo: String) {
        val expense = Expense(
            userId = userId,
            date = date.toString(),
            category = category,
            amount = amount,
            memo = memo
        )
        
        lifecycleScope.launch {
            try {
                database.expenseDao().insert(expense)
                Toast.makeText(this@ExpenseRegisterActivity, "지출이 등록되었습니다.", Toast.LENGTH_SHORT).show()
                finish()
            } catch (e: Exception) {
                Toast.makeText(this@ExpenseRegisterActivity, "지출 등록에 실패했습니다.", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun updateExpense(expenseId: Int, category: String, amount: Int, memo: String) {
        lifecycleScope.launch {
            try {
                val existingExpense = database.expenseDao().getExpenseById(expenseId)
                existingExpense?.let { expense ->
                    // 기존 데이터와 새 데이터 비교
                    val hasChanges = expense.category != category || 
                                   expense.amount != amount || 
                                   expense.memo != memo
                    
                    if (hasChanges) {
                        // 변경사항이 있는 경우에만 업데이트
                        val updatedExpense = expense.copy(
                            category = category,
                            amount = amount,
                            memo = memo
                        )
                        database.expenseDao().update(updatedExpense)
                        Toast.makeText(this@ExpenseRegisterActivity, "지출이 수정되었습니다.", Toast.LENGTH_SHORT).show()
                    }
                    // 변경사항이 있든 없든 액티비티 종료
                    finish()
                }
            } catch (e: Exception) {
                Toast.makeText(this@ExpenseRegisterActivity, "지출 수정에 실패했습니다.", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun showDeleteConfirmDialog() {
        val expenseId = intent.getIntExtra(EXTRA_EXPENSE_ID, -1)
        if (expenseId == -1) {
            // 새 등록 모드에서는 그냥 취소
            finish()
            return
        }
        
        MaterialAlertDialogBuilder(this)
            .setTitle("지출 삭제")
            .setMessage("이 지출 내역을 삭제하시겠습니까?")
            .setNegativeButton("취소") { dialog, _ ->
                dialog.dismiss()
            }
            .setPositiveButton("삭제") { dialog, _ ->
                deleteExpense(expenseId)
                dialog.dismiss()
            }
            .show()
    }
    
    private fun deleteExpense(expenseId: Int) {
        lifecycleScope.launch {
            try {
                val expense = database.expenseDao().getExpenseById(expenseId)
                expense?.let {
                    database.expenseDao().delete(it)
                    Toast.makeText(this@ExpenseRegisterActivity, "지출이 삭제되었습니다.", Toast.LENGTH_SHORT).show()
                    finish()
                }
            } catch (e: Exception) {
                Toast.makeText(this@ExpenseRegisterActivity, "지출 삭제에 실패했습니다.", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                handleBackNavigation()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}