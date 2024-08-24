package com.example.expensetracker

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.example.expensetracker.databinding.FragmentOnboard3Binding

class Onboard3 : Fragment() {
    private lateinit var binding: FragmentOnboard3Binding

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding= FragmentOnboard3Binding.inflate(layoutInflater)
        binding.btnBack.setOnClickListener{
            parentFragmentManager.popBackStack() // This will navigate to the previous fragment
        }

        binding.btnNext.setOnClickListener{
            parentFragmentManager.beginTransaction()
                .replace(R.id.onboardFragment, Onboard4())
                .addToBackStack(null) // Adds the transaction to the back stack
                .commit()
        }
        return binding.root
    }
}