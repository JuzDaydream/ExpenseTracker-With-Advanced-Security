package com.example.expensetracker

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth

class AuthActivity : AppCompatActivity() {

    private lateinit var biometricPrompt: BiometricPrompt
    private lateinit var promptInfo: BiometricPrompt.PromptInfo
    private var isLoadingComplete = false
    private var isAuthenticating = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_auth)

        // Start Firebase initialization and loading
        initializeFirebaseAndLoadData()
    }

    // Simulate Firebase initialization and data loading
    private fun initializeFirebaseAndLoadData() {
        FirebaseApp.initializeApp(this)

        // Simulate loading (e.g., fetching user data from Firebase)
        FirebaseAuth.getInstance().currentUser?.let { user ->
            Log.d("DEBUGTEST", "Loading user data...")

            // Simulating data loading delay
            // For demonstration, we're using a fake delay. Replace this with actual loading logic.
            Handler(Looper.getMainLooper()).postDelayed({
                onLoadingComplete()
            }, 2000)  // Simulated 2-second loading delay

        } ?: run {
            Log.d("DEBUGTEST", "User not authenticated.")
            onLoadingComplete()
        }
    }

    private fun onLoadingComplete() {
        isLoadingComplete = true
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
            // User is logged in, trigger fingerprint authentication after loading is complete
            authenticateWithFingerprint(sharedPreferences)
        } else {
            // No user logged in, proceed with the auth flow (e.g., LoginFragment)
            navigateToLoginFragment()
        }
    }

    private fun authenticateWithFingerprint(sharedPreferences: SharedPreferences) {
        if (!isLoadingComplete || isAuthenticating) return  // Ensure loading is complete and prevent duplicate calls
        isAuthenticating = true

        val executor = ContextCompat.getMainExecutor(this)

        biometricPrompt = BiometricPrompt(this, executor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                isAuthenticating = false
                Log.d("DEBUGTEST", "Fingerprint authentication succeeded")

                val userID = sharedPreferences.getString("userID", null)
                if (userID != null) {
                    navigateToMainActivity(userID)
                } else {
                    navigateToLoginFragment()
                }
            }

            override fun onAuthenticationFailed() {
                super.onAuthenticationFailed()
                isAuthenticating = false
                Log.d("DEBUGTEST", "Fingerprint authentication failed")
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                isAuthenticating = false
                Log.d("DEBUGTEST", "Fingerprint authentication error: $errString")
                handleAuthenticationError(errorCode)
            }
        })

        promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Fingerprint Authentication")
            .setSubtitle("Authenticate to continue")
            .setDescription("Please authenticate using your fingerprint to proceed to the main activity.")
            .setNegativeButtonText("Cancel")
            .build()

        biometricPrompt.authenticate(promptInfo)
    }

    private fun handleAuthenticationError(errorCode: Int) {
        when (errorCode) {
            BiometricPrompt.ERROR_NEGATIVE_BUTTON -> {
                Log.d("DEBUGTEST", "User canceled authentication.")
                navigateToLoginFragment()
            }
            else -> {
                Log.d("DEBUGTEST", "Authentication error occurred, no retry.")
            }
        }
    }

    private fun navigateToMainActivity(userID: String?) {
        val intent = Intent(this, MainActivity::class.java)
        intent.putExtra("userID", userID)
        startActivity(intent)
        finish()
    }

    private fun navigateToLoginFragment() {
        Log.d("DEBUGTEST", "Navigating to LoginFragment.")
        // Implement your logic to navigate to the LoginFragment
    }
}
