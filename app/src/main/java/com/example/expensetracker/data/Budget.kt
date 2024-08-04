package com.example.expensetracker.data

data class Budget (var id :String="",
                   val amount: Double=0.00,
                   val startDate: String="",
                   val endDate: String="",
                   val category: String="")