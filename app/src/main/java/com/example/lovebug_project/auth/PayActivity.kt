package com.example.lovebug_project.auth

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.lovebug_project.MainActivity // 메인 액티비티 임포트
import com.example.lovebug_project.databinding.ActivityPayBinding
import java.text.DecimalFormat

class PayActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPayBinding
    private val decimalFormat = DecimalFormat("#,###")
    private var result: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // enableEdgeToEdge() // 이 기능은 전체 화면을 사용할 때 유용하며, 현재 레이아웃에서는 필수가 아닙니다.

        binding = ActivityPayBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val submitBtn = binding.submitBtn

        // EditText에 TextWatcher를 추가하여 1,000 단위로 콤마(,)를 찍어줍니다.
        binding.goalAmountEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (!TextUtils.isEmpty(s.toString()) && s.toString() != result) {
                    // 콤마가 포함되지 않은 문자열로 변환
                    val plainText = s.toString().replace(",", "")
                    // 숫자로 변환 후 포맷 적용
                    result = decimalFormat.format(plainText.toLong())
                    binding.goalAmountEditText.setText(result)
                    binding.goalAmountEditText.setSelection(result.length) // 커서를 마지막으로 이동
                }
            }

            override fun afterTextChanged(s: Editable?) {}
        })


        // '등록' 버튼 클릭 이벤트 리스너 설정
        // XML에서 버튼 ID를 signUpBtn -> submitBtn으로 변경했으므로 바인딩 변수도 변경됩니다.
        binding.submitBtn.setOnClickListener {
            // EditText에서 텍스트를 가져옵니다.
            val goalAmountStr = binding.goalAmountEditText.text.toString()

            // 입력값이 비어있는지 확인합니다.
            if (goalAmountStr.isBlank()) {
                Toast.makeText(this, "목표 금액을 입력해주세요.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // 콤마(,)를 제거하고 Long 타입으로 변환합니다.
            val goalAmount = goalAmountStr.replace(",", "").toLongOrNull()

            if (goalAmount == null) {
                Toast.makeText(this, "유효한 숫자를 입력해주세요.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // SharedPreferences를 사용하여 목표 금액 저장
            val sharedPref = getSharedPreferences("app_settings", Context.MODE_PRIVATE)
            with (sharedPref.edit()) {
                putLong("expense_goal", goalAmount)
                apply() // 비동기적으로 데이터를 저장합니다.
            }

            Toast.makeText(this, "목표 금액 ${decimalFormat.format(goalAmount)}원이 저장되었습니다.", Toast.LENGTH_SHORT).show()

//            signUpButton.setOnClickListener {
//                // Intent를 생성하여 현재 액티비티(this)에서 JoinActivity로 이동하도록 설정합니다.
//                val intent = Intent(this, JoinActivity::class.java)
//
//                // startActivity() 메소드에 intent를 전달하여 새로운 액티비티를 시작합니다.
//                startActivity(intent)
//            }

            Intent(this, WelcomeActivity::class.java).also { startActivity(it) }
            finish()

        }
    }
}