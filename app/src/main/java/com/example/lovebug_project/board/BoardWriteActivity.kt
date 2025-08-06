package com.example.lovebug_project.board

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.lovebug_project.R
import com.example.lovebug_project.databinding.ActivityBoardWriteBinding

class BoardWriteActivity : AppCompatActivity() {

    private lateinit var binding : ActivityBoardWriteBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityBoardWriteBinding.inflate(layoutInflater)
        setContentView(binding.root)


        // 뒤로 가기 버튼
        binding.btnBack.setOnClickListener {
            finish()
        }
    }
}