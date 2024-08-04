package com.example.expensetracker

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Toast
import androidx.core.content.ContentProviderCompat.requireContext
import com.example.expensetracker.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var userID: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        userID = intent.getStringExtra("userID").toString()
        Toast.makeText(this, userID, Toast.LENGTH_SHORT).show()
        binding.navBtm.setOnItemSelectedListener {menuItem ->

            when (menuItem.itemId) {

                R.id.transaction -> {
                    supportFragmentManager.beginTransaction().replace(binding.fragmentContainerView2.id, TransactionFragment()).commit()
                    true
                }
                R.id.budget -> {
                    supportFragmentManager.beginTransaction().replace(binding.fragmentContainerView2.id, BudgetFragment()).commit()
                    true
                }
                R.id.saveGoal -> {
                    supportFragmentManager.beginTransaction().replace(binding.fragmentContainerView2.id, SavingGoalFragment()).commit()
                    true
                }
                R.id.statistic -> {
                    supportFragmentManager.beginTransaction().replace(binding.fragmentContainerView2.id, StatisticFragment()).commit()
                    true
                }
                R.id.setting -> {
                    supportFragmentManager.beginTransaction().replace(binding.fragmentContainerView2.id, SettingFragment()).commit()
                    true
                }

                else -> {false}
            }
        }
    }
    fun getUserID(): String{
        return userID
    }
}