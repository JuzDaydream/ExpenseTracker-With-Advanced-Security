package com.example.expensetracker.network

data class ClassificationResponse(
    val category_list: List<Category>
)

data class Category(
    val code: String,
    val label: String,
    val score: Double
)