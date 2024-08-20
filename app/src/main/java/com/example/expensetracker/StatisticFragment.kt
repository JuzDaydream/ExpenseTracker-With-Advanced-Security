package com.example.expensetracker

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.AppCompatButton
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentContainerView
import com.example.expensetracker.ui.MonthlyStatFragment
import com.example.expensetracker.ui.YearlyStatFragment

class StatisticFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        val view = inflater.inflate(R.layout.fragment_statistic, container, false)

        val btnMonthly: AppCompatButton = view.findViewById(R.id.btn_month)
        val btnYearly: AppCompatButton = view.findViewById(R.id.btn_year)
        val fragment:FragmentContainerView =view.findViewById(R.id.fragmentContainerView3)

        btnMonthly.setOnClickListener{
            parentFragmentManager.beginTransaction().replace(R.id.fragmentContainerView3, MonthlyStatFragment()).commit()
        }
        btnYearly.setOnClickListener{
            parentFragmentManager.beginTransaction().replace(R.id.fragmentContainerView3, YearlyStatFragment()).commit()
        }

        return view
    }
}