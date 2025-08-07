package com.example.lovebug_project.auth

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.lovebug_project.databinding.ActivityPayBinding
import java.text.DecimalFormat

class PayActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPayBinding
    private val decimalFormat = DecimalFormat("#,###")
    private var result: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPayBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // EditText에 TextWatcher를 추가하여 1,000 단위로 콤마(,)를 찍어줍니다.
        binding.goalAmountEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (!TextUtils.isEmpty(s.toString()) && s.toString() != result) {
                    val plainText = s.toString().replace(",", "")
                    result = decimalFormat.format(plainText.toLong())
                    binding.goalAmountEditText.setText(result)
                    binding.goalAmountEditText.setSelection(result.length)
                }
            }

            override fun afterTextChanged(s: Editable?) {}
        })

        // '등록' 버튼 클릭 이벤트 리스너를 한 번만 설정합니다.
        binding.submitBtn.setOnClickListener {
            val goalAmountStr = binding.goalAmountEditText.text.toString()

            if (goalAmountStr.isBlank()) {
                Toast.makeText(this, "목표 금액을 입력해주세요.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val goalAmount = goalAmountStr.replace(",", "").toLongOrNull()

            if (goalAmount == null) {
                Toast.makeText(this, "유효한 숫자를 입력해주세요.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // 1. SharedPreferences를 사용하여 목표 금액 저장
            val sharedPref = getSharedPreferences("app_settings", Context.MODE_PRIVATE)
            with (sharedPref.edit()) {
                putLong("expense_goal", goalAmount)
                apply()
            }

            // 2. 저장 완료 토스트 메시지 표시
            Toast.makeText(this, "목표 금액 ${decimalFormat.format(goalAmount)}원이 저장되었습니다.", Toast.LENGTH_SHORT).show()

            // 3. WelcomeActivity로 이동할 Intent를 생성하고 실행
            val intent = Intent(this, WelcomeActivity::class.java)
            startActivity(intent)

            // 4. 현재 PayActivity를 종료하여 뒤로 가기 버튼으로 돌아오지 않도록 함
            finish()
        }
    }
}