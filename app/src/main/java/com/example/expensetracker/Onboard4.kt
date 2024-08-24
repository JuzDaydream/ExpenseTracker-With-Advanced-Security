package com.example.expensetracker

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.example.expensetracker.databinding.FragmentOnboard4Binding

class Onboard4 : Fragment() {
    private lateinit var binding: FragmentOnboard4Binding

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentOnboard4Binding.inflate(inflater, container, false)

        binding.btnBack.setOnClickListener {
            parentFragmentManager.popBackStack() // Navigate to the previous fragment
        }

        binding.btnNext.setOnClickListener {
            onOnboardingFinished()
        }

        return binding.root
    }

    private fun onOnboardingFinished() {
        // Set the flag in SharedPreferences
        val sharedPref = requireContext().getSharedPreferences("UserPrefs", Context.MODE_PRIVATE)
        with(sharedPref.edit()) {
            putBoolean("isOnboardingComplete", true)
            apply()
        }

        // Go to AuthActivity and finish the onboarding process
        val intent = Intent(requireContext(), AuthActivity::class.java)
        startActivity(intent)
        requireActivity().finish() // Finish the current activity so the user can't return to it
    }
}
