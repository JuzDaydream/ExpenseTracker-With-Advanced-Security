package com.example.expensetracker

import android.content.Context
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Toast
import com.example.expensetracker.data.User
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase

class AuthActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_auth)
// Check if the user is already logged in
        val sharedPreferences = getSharedPreferences("UserPrefs", Context.MODE_PRIVATE)
        val userID = sharedPreferences.getString("userID", null)

        if (userID != null) {
            // User is logged in, navigate to the main activity
            val intent = Intent(this, MainActivity::class.java)
            intent.putExtra("userID", userID)
            startActivity(intent)
            finish()
        } else {
            // No user logged in, proceed with the auth flow
        }
    }
}