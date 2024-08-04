package com.example.expensetracker.data

data class User(var id: String = "",
                var email: String = "",
                var password: String = "",
                var longitude: String = "",
                var latitude: String = "",
                var transactionList: List<String> = arrayListOf(),
                var goalList: List<String> = arrayListOf(),
                var budgetList: List<String> = arrayListOf())