package com.example.expensetracker

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.example.expensetracker.databinding.FragmentOnboard1Binding

class Onboard1 : Fragment() {
    private lateinit var binding: FragmentOnboard1Binding

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding= FragmentOnboard1Binding.inflate(layoutInflater)
        binding.btnNext.setOnClickListener {
            // Replace the current fragment with Onboard2
            parentFragmentManager.beginTransaction()
                .replace(R.id.onboardFragment, Onboard2())  // Replace with the next fragment
                .addToBackStack(null)  // Add the transaction to the back stack, so the user can navigate back
                .commit()  // Commit the transaction
        }
        return binding.root
    }
}