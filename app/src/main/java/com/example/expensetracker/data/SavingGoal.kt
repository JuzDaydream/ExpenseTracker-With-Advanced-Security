package com.example.expensetracker.data

data class SavingGoal(var id :String="",
                       var title :String="",
                       val amount: Double=0.00,
                       val startDate: String="",
                       val endDate: String="",
                      val icon: String? = null,
                      var notes :String?=null)