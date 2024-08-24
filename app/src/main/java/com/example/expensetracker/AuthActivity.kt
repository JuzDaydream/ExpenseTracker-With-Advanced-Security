package com.example.expensetracker

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity

class AuthActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_auth)

        val sharedPreferences = getSharedPreferences("UserPrefs", Context.MODE_PRIVATE)

        // Retrieve the onboarding completion status and user ID
        val isOnboardingComplete = sharedPreferences.getBoolean("isOnboardingComplete", false)
        val userID = sharedPreferences.getString("userID", null)

        Log.d("DEBUGTEST", "Is Onboarding Complete: $isOnboardingComplete")
        Log.d("DEBUGTEST", "User ID: $userID")

        if (!isOnboardingComplete) {
            // First time, go to OnboardingActivity
            val intent = Intent(this, OnboardActivity::class.java)
            startActivity(intent)
            finish() // Close this activity so the user can't return to it
        } else if (userID != null) {
            // User is logged in, navigate to the main activity
            val intent = Intent(this, MainActivity::class.java)
            intent.putExtra("userID", userID)
            startActivity(intent)
            finish()
        } else {
            // No user logged in, proceed with the auth flow
            // You might want to start a login or registration activity here
            Log.d("DEBUGTEST", "No user logged in, proceed with auth flow")
        }
    }
}
