package com.example.lovebug_project.utils // 패키지 이름은 실제 위치에 맞게

import android.app.Activity
import android.content.Context
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.ImageView
import com.bumptech.glide.Glide
import com.example.lovebug_project.R

fun View.hideKeyboard() {
    val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    imm.hideSoftInputFromWindow(windowToken, 0)
}

// Activity에서 사용할 경우 (선택적)
fun Activity.hideKeyboardActivity() { // 함수 이름 충돌 방지를 위해 변경 (hideKeyboard()가 View 확장함수와 겹칠 수 있음)
    currentFocus?.let {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(it.windowToken, 0)
    }
}

/**
 * ImageView에 프로필 이미지를 로드하는 확장 함수
 * profileImageUrl이 null이거나 빈 문자열인 경우 기본 프로필 이미지를 사용
 * 로딩 실패 시에도 기본 프로필 이미지로 fallback
 */
fun ImageView.loadProfileImage(profileImageUrl: String?) {
    Glide.with(context)
        .load(profileImageUrl.takeIf { !it.isNullOrBlank() } ?: R.drawable.circle_button)
        .placeholder(R.drawable.circle_button)
        .error(R.drawable.circle_button)
        .centerCrop()
        .into(this)
}