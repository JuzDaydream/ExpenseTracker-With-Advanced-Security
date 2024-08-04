package com.example.expensetracker.data

data class Transaction(var id :String= "",
                       var title :String= "",
                       val amount: Double= 0.0,
                       val date: String= "",
                       var category: String = "",  // Adjusted to match your Firebase data
                       var note :String?=null)