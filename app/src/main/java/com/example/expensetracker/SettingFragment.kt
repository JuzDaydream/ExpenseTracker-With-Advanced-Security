package com.example.expensetracker

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.example.expensetracker.databinding.FragmentSettingBinding

class SettingFragment : Fragment() {
    private lateinit var binding: FragmentSettingBinding
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentSettingBinding.inflate(inflater, container, false)

        binding.cardLogout.setOnClickListener{
            //LOG OUT CLEAR SHAREDPREFERENCES
            val sharedPreferences = requireContext().getSharedPreferences("UserPrefs", Context.MODE_PRIVATE)
            val editor = sharedPreferences.edit()
            editor.remove("userID")
            editor.apply()

            val intent = Intent(requireContext(), AuthActivity::class.java)
            startActivity(intent)
            requireActivity().finish()
        }
        binding.cardCreateSpending.setOnClickListener {
            val intent = Intent(requireContext(), SpendingAnalysisActivity::class.java)
            val sharedPreferences = requireContext().getSharedPreferences("UserPrefs", Context.MODE_PRIVATE)
            val userId = sharedPreferences.getString("userID", "")
            intent.putExtra("userID", userId)
            startActivity(intent)
        }

        binding.cardCategory.setOnClickListener {
            requireActivity().supportFragmentManager.beginTransaction().replace(R.id.fragmentContainerView2, CategoryFragment()).addToBackStack(null).commit()
            true
        }

        return binding.root
    }
}