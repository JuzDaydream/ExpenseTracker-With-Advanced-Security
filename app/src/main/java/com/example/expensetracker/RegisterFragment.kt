package com.example.expensetracker

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import com.example.expensetracker.data.User
import com.example.expensetracker.databinding.FragmentRegisterBinding
import com.google.android.gms.location.LocationServices
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

class RegisterFragment : Fragment() {
    private lateinit var binding: FragmentRegisterBinding
    private lateinit var userDB: DatabaseReference
    private var nextUserId:Int = 0
    private var userList : ArrayList<User> = arrayListOf()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentRegisterBinding.inflate(inflater, container, false)
        userDB = FirebaseDatabase.getInstance("https://expensetracker-a260c-default-rtdb.asia-southeast1.firebasedatabase.app")
            .getReference("User")
        binding.tvErrorMsg.text = ""
        requestLocationPermissions()
        fetchData(object : InvokeFirst {
            override fun invoke() {
                var a = 1
            }
        })

        binding.btnRegister.setOnClickListener {
            val email = binding.editEmail.text.toString().trim()
            val password = binding.editPass.text.toString().trim()

            // Email Validation
            if (email.isEmpty() || !email.contains("@") || !email.endsWith(".com")) {
                binding.tvErrorMsg.text = "Invalid email"
                return@setOnClickListener
            }

            // Password Validation
            if (password.isEmpty() || password.length < 6 || !password.any { it.isDigit() } || !password.any { it.isLetter() }) {
                binding.tvErrorMsg.text = "Password must be at least 6 characters long and contain letters and numbers."
                return@setOnClickListener
            }

            // Empty Validation
            if (email.isEmpty() || password.isEmpty()) {
                binding.tvErrorMsg.text = "Email or Password cannot be Empty!"
                return@setOnClickListener
            }

            // Unique Email Validation
            var isUnique = true
            for (i in 1..userList.size) {
                if (userList[i - 1].email == email) {
                    isUnique = false
                    binding.tvErrorMsg.text = "This Email has been registered!"
                    break
                }
            }

            if (isUnique) {
                binding.tvErrorMsg.text = ""

                getCurrentLocation { latitude, longitude ->
                    getNextUserID()
                    val id = "U" + nextUserId
                    val user = User(
                        id,
                        email,
                        password,
                        longitude,
                        latitude,
                        arrayListOf(""),
                        arrayListOf(""),
                        arrayListOf("")
                    )

                    // Register the user with Firebase Authentication
                    val auth = FirebaseAuth.getInstance()
                    auth.createUserWithEmailAndPassword(email, password)
                        .addOnCompleteListener { task ->
                            if (task.isSuccessful) {
                                // Save the user data to the database
                                userDB.child(user.id).setValue(user)
                                    .addOnCompleteListener { task2 ->
                                        if (task2.isSuccessful) {
                                            // Send verification email
                                            auth.currentUser?.sendEmailVerification()?.addOnCompleteListener { emailTask ->
                                                if (emailTask.isSuccessful) {
                                                    activity?.runOnUiThread {
                                                        Toast.makeText(requireContext(), "Account Created. Check your email for verification.", Toast.LENGTH_LONG).show()
                                                    }
                                                } else {
                                                    activity?.runOnUiThread {
                                                        Toast.makeText(requireContext(), "Error sending verification email.", Toast.LENGTH_LONG).show()
                                                    }
                                                }
                                            }

                                            // Navigate to LoginFragment after account creation
                                            Toast.makeText(requireContext(), "Please verify your email", Toast.LENGTH_LONG).show()
                                            parentFragmentManager.popBackStackImmediate(null, FragmentManager.POP_BACK_STACK_INCLUSIVE)
                                            parentFragmentManager.beginTransaction()
                                                .replace(R.id.fragmentContainerView, LoginFragment())
                                                .commit()
                                        } else {
                                            val error = task2.exception?.message ?: "Unknown error"
                                            activity?.runOnUiThread {
                                                Toast.makeText(requireContext(), "Error: $error", Toast.LENGTH_LONG).show()
                                            }
                                        }
                                    }
                            } else {
                                val error = task.exception?.message ?: "Unknown error"
                                activity?.runOnUiThread {
                                    Toast.makeText(requireContext(), "Error: $error", Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                }
            } else {
                binding.tvErrorMsg.text = "Email is already in use."
            }
        }

        binding.tvLoginText2.setOnClickListener {
            parentFragmentManager.popBackStackImmediate(null, FragmentManager.POP_BACK_STACK_INCLUSIVE)
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainerView, LoginFragment())
                .commit()
        }

        return binding.root
    }

    private fun getNextUserID() {
        // Find the maximum current ID number
        val maxId = userList.map { it.id.replace("U", "").toIntOrNull() ?: 0 }.maxOrNull() ?: 0
        nextUserId = maxId + 1
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

                // Update nextUserId based on the fetched userList
                getNextUserID()

                // Invoke the callback function
                invoke.invoke()
            }

            override fun onCancelled(error: DatabaseError) {
                activity?.runOnUiThread {
                    Toast.makeText(requireContext(), "Error: ${error.message}", Toast.LENGTH_LONG).show()
                }
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
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestLocationPermissions() {
        ActivityCompat.requestPermissions(
            requireActivity(),
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
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
                activity?.runOnUiThread {
                    Toast.makeText(requireContext(), "Unable to retrieve location", Toast.LENGTH_SHORT).show()
                }
                return
            }
            fusedLocationClient.lastLocation
                .addOnSuccessListener { location: Location? ->
                    val latitude = location?.latitude?.toString() ?: "0.0"
                    val longitude = location?.longitude?.toString() ?: "0.0"
                    callback(latitude, longitude)
                }
        } else {
            activity?.runOnUiThread {
                Toast.makeText(requireContext(), "Location permissions are required", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
