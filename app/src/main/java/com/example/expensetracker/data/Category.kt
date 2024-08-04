package com.example.expensetracker.data

data class Category(val id: String = "",    // Make sure there's a default value
                    val name: String = "",
                    val icon: String? = null)