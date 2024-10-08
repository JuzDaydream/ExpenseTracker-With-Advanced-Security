package com.example.expensetracker

import android.Manifest
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import com.example.expensetracker.data.User
import com.example.expensetracker.databinding.FragmentLoginBinding
import com.google.android.gms.location.LocationServices
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

class LoginFragment : Fragment() {
    private lateinit var binding: FragmentLoginBinding
    private lateinit var userDB: DatabaseReference
    private var userList: ArrayList<User> = arrayListOf()
    private lateinit var auth: FirebaseAuth

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentLoginBinding.inflate(layoutInflater)
        val view = binding.root
        userDB =
            FirebaseDatabase.getInstance("https://expensetracker-a260c-default-rtdb.asia-southeast1.firebasedatabase.app")
                .getReference("User")
        auth = FirebaseAuth.getInstance()
        binding.tvForgotPass.setOnClickListener {
            promptForEmail()
        }
        binding.tvErrorMsg.text = ""
        requestLocationPermissions()
        fetchData(object : InvokeFirst {
            override fun invoke() {
                var a = 1
            }
        })

        binding.tvRegisterText2.setOnClickListener {
            parentFragmentManager.popBackStackImmediate(null, FragmentManager.POP_BACK_STACK_INCLUSIVE)
            parentFragmentManager.beginTransaction().replace(R.id.fragmentContainerView, RegisterFragment())
                .commit()
        }

        binding.btnLogin.setOnClickListener {
            handleLogin()
        }

        return view
    }

    private fun fetchData(invoke: InvokeFirst) {
        userDB.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                // Clear the existing list
                userList.clear()

                // Check if data exists
                if (snapshot.exists()) {
                    for (userSnap in snapshot.children) {
                        // Extract user data
                        val id = userSnap.child("id").value as? String ?: ""
                        val email = userSnap.child("email").value as? String ?: ""
                        val password = userSnap.child("password").value as? String ?: ""
                        val longitude = userSnap.child("longitude").value as? String ?: ""
                        val latitude = userSnap.child("latitude").value as? String ?: ""

                        // Extract lists
                        val transactionList = userSnap.child("transactionList").children.map { it.value as? String ?: "" }
                        val goalList = userSnap.child("goalList").children.map { it.value as? String ?: "" }
                        val budgetList = userSnap.child("budgetList").children.map { it.value as? String ?: "" }

                        // Create User object
                        val user = User(
                            id,
                            email,
                            password,
                            longitude,
                            latitude,
                            transactionList,
                            goalList,
                            budgetList
                        )

                        // Add to userList
                        userList.add(user)
                    }
                }
                // Invoke the callback function
                invoke.invoke()
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(requireContext(), "Error: ${error.message}", Toast.LENGTH_LONG).show()
            }
        })
    }

    interface InvokeFirst {
        fun invoke()
    }

    private val LOCATION_REQUEST_CODE = 1000

    private fun checkLocationPermissions(): Boolean {
        return ActivityCompat.checkSelfPermission(
            requireContext(),
            android.Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(
                    requireContext(),
                    android.Manifest.permission.ACCESS_COARSE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestLocationPermissions() {
        ActivityCompat.requestPermissions(
            requireActivity(),
            arrayOf(
                android.Manifest.permission.ACCESS_FINE_LOCATION,
                android.Manifest.permission.ACCESS_COARSE_LOCATION
            ),
            LOCATION_REQUEST_CODE
        )
    }

    private fun getCurrentLocation(callback: (latitude: String, longitude: String) -> Unit) {
        if (checkLocationPermissions()) {
            val fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())
            if (ActivityCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                Toast.makeText(requireContext(), "Unable to retrieve location", Toast.LENGTH_SHORT).show()
                return
            }
            fusedLocationClient.lastLocation
                .addOnSuccessListener { location: Location? ->
                    if (location != null) {
                        val latitude = location.latitude.toString()
                        val longitude = location.longitude.toString()
                        // Call the callback with the latitude and longitude
                        callback(latitude, longitude)
                    } else {
                        Toast.makeText(requireContext(), "Unable to retrieve location", Toast.LENGTH_SHORT).show()
                    }
                }
                .addOnFailureListener {
                    // Handle failure
                    Toast.makeText(requireContext(), "Failed to get location", Toast.LENGTH_SHORT).show()
                }
        } else {
            // Request permissions if not granted
            ActivityCompat.requestPermissions(
                requireActivity(),
                arrayOf(
                    android.Manifest.permission.ACCESS_FINE_LOCATION,
                    android.Manifest.permission.ACCESS_COARSE_LOCATION
                ),
                LOCATION_REQUEST_CODE
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == LOCATION_REQUEST_CODE) {
            val fineLocationGranted = grantResults.isNotEmpty() &&
                    grantResults[0] == PackageManager.PERMISSION_GRANTED
            val coarseLocationGranted = grantResults.size > 1 &&
                    grantResults[1] == PackageManager.PERMISSION_GRANTED

            if (fineLocationGranted || coarseLocationGranted) {
                // Request the current location with a callback to handle latitude and longitude
                getCurrentLocation { latitude, longitude ->
                    // Handle the obtained latitude and longitude here if needed
                    Toast.makeText(requireContext(), "Latitude: $latitude, Longitude: $longitude", Toast.LENGTH_LONG).show()
                }
            } else {
                Toast.makeText(requireContext(), "Location permission is required to access your location.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun fetchOldUserLocation(userId: String, callback: (latitude: Double, longitude: Double) -> Unit) {
        userDB.child(userId).get().addOnSuccessListener { snapshot ->
            if (snapshot.exists()) {
                val oldLatitude = (snapshot.child("latitude").value as? String)?.toDoubleOrNull() ?: 0.0
                val oldLongitude = (snapshot.child("longitude").value as? String)?.toDoubleOrNull() ?: 0.0
                callback(oldLatitude, oldLongitude)
            } else {
                Toast.makeText(requireContext(), "User data not found", Toast.LENGTH_SHORT).show()
            }
        }.addOnFailureListener {
            Toast.makeText(requireContext(), "Failed to retrieve user data", Toast.LENGTH_SHORT).show()
        }
    }

    private fun handleLogin() {
        val email = binding.editEmail.text.toString().trim()
        val password = binding.editPass.text.toString().trim()

        if (email.isEmpty() || password.isEmpty()) {
            binding.tvErrorMsg.text = "Email or Password cannot be Empty!"
            return
        }

        // Use Firebase Authentication to sign in
        auth.signInWithEmailAndPassword(email, password).addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val currentUser = auth.currentUser
                if (currentUser != null && currentUser.isEmailVerified) {
                    var found = false
                    var userID = ""

                    for (user in userList) {
                        if (user.email == email) {
//                        if (user.email == email && user.password == password) {
                            found = true
                            userID = user.id
                            break
                        }
                    }

                    if (found) {
                        // Handle location and other tasks
                        getCurrentLocation { newLatitude, newLongitude ->
                            fetchOldUserLocation(userID) { oldLatitude, oldLongitude ->
                                compareLocations(
                                    oldLatitude, oldLongitude,
                                    newLatitude.toDouble(), newLongitude.toDouble()
                                ) { isSignificantChange ->
                                    if (isSignificantChange) {
                                        // If location change is significant, send verification and sign out
                                        promptEmailVerification()
                                        binding.tvErrorMsg.text = "Significant location change detected. Please verify your email to continue."

                                        // Update the user's latitude and longitude in the database
                                        // Here we use a placeholder function updateLocationInDatabase, which you'll need to implement
                                        updateLocationInDatabase(userID, newLatitude, newLongitude)

                                    } else {
                                        // Proceed to main activity if no significant change
                                        proceedToMainActivity(userID)
                                    }
                                }
                            }
                        }
                    } else {
                        binding.tvErrorMsg.text = "Invalid email or password. Please try again."
                    }
                } else {
                    binding.tvErrorMsg.text = "Please verify your email address before logging in."
                }
            } else {
                binding.tvErrorMsg.text = "Invalid email or password. Please try again."
                Log.d("LoginFragment", "Login failed: ${task.exception?.message}")
            }
        }
    }

    // Placeholder function to update user's latitude and longitude in the database
    private fun updateLocationInDatabase(userID: String, latitude: String, longitude: String) {
        userDB.child(userID).child("latitude").setValue(latitude)
        userDB.child(userID).child("longitude").setValue(longitude)
    }
    private fun compareLocations(oldLatitude: Double, oldLongitude: Double, newLatitude: Double, newLongitude: Double, callback: (isSignificantChange: Boolean) -> Unit) {
        val results = FloatArray(1)
        Location.distanceBetween(oldLatitude, oldLongitude, newLatitude, newLongitude, results)
        val distanceInMeters = results[0]
        val thresholdInMeters = 500_000f // 500 km in meters

        if (distanceInMeters > thresholdInMeters) {
            callback(true)
        } else {
            callback(false)
        }
    }

    private fun promptEmailVerification() {
        val currentUser = auth.currentUser
        if (currentUser != null) {
            currentUser.sendEmailVerification().addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    auth.signOut()
                } else {
                    Toast.makeText(
                        requireContext(),
                        "Failed to send verification email. Please try again.",
                        Toast.LENGTH_SHORT
                    ).show()
                    Log.d("LoginFragment", "Failed to send verification email: ${task.exception?.message}")
                }
            }
        } else {
            Log.d("LoginFragment", "No current user found for email verification.")
        }
    }
    private fun promptForEmail() {
        val dialog = Dialog(requireContext())
        dialog.setContentView(R.layout.popup_forgetpass)
        dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val editEmail = dialog.findViewById<EditText>(R.id.edit_email)
        val btnCancel = dialog.findViewById<View>(R.id.btn_cancel)
        val btnSend = dialog.findViewById<View>(R.id.btn_send)

        btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        btnSend.setOnClickListener {
            val email = editEmail.text.toString().trim()
            if (email.isNotEmpty()) {
                sendForgotPasswordEmail(email)
                dialog.dismiss()
            } else {
                Toast.makeText(context, "Please enter your email address.", Toast.LENGTH_SHORT).show()
            }
        }

        dialog.show()
    }

    // Function to send the password reset email
    private fun sendForgotPasswordEmail(email: String) {
        auth.sendPasswordResetEmail(email)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    Toast.makeText(context, "Password reset email sent.", Toast.LENGTH_SHORT).show()
                } else {
                    val errorMessage = task.exception?.message ?: "Error occurred."
                    Toast.makeText(context, errorMessage, Toast.LENGTH_SHORT).show()
                }
            }
    }

    private fun proceedToMainActivity(userID: String) {
        // Save userID in SharedPreferences
        val sharedPreferences = requireContext().getSharedPreferences("UserPrefs", Context.MODE_PRIVATE)
        val editor = sharedPreferences.edit()
        editor.putString("userID", userID)
        editor.apply()

        // Start MainActivity
        val intent = Intent(requireContext(), MainActivity::class.java)
        intent.putExtra("userID", userID)
        startActivity(intent)
        activity?.finish()
    }
}

