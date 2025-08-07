package com.example.lovebug_project.mypage

import java.io.Serializable

data class CategoryData(
    val cg_name : String,
    val percentage : Int,
    var color : String
) : Serializable
