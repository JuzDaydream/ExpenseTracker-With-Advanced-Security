package com.example.expensetracker

import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.fragment.app.FragmentManager
import com.example.expensetracker.data.User
import com.example.expensetracker.databinding.FragmentRegisterBinding
import com.google.android.gms.location.LocationServices
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

class RegisterFragment : Fragment() {
    private lateinit var binding: FragmentRegisterBinding
    private lateinit var userDB: DatabaseReference
    private var nextUserId:Int =0
    private var userList : ArrayList<User> = arrayListOf()


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentRegisterBinding.inflate(inflater, container, false)
        userDB =
            FirebaseDatabase.getInstance("https://expensetracker-a260c-default-rtdb.asia-southeast1.firebasedatabase.app")
                .getReference("User")
        binding.tvErrorMsg.text = ""
        requestLocationPermissions()
        fetchData(object : InvokeFirst{
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

            // Unique Email Validatio
            var isUnique = true
            for(i in 1..userList.size){
                if(userList[i - 1].email == email){
                    isUnique = false
                    binding.tvErrorMsg.text="This Email has been registered!"
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
                        longitude,  // Use the obtained longitude
                        latitude,   // Use the obtained latitude
                        arrayListOf(""),
                        arrayListOf(""),
                        arrayListOf("")
                    )

                    // Save the user data to the database
                    userDB.child(user.id).setValue(user)
                        .addOnCompleteListener { task ->
                            if (task.isSuccessful) {
                                Toast.makeText(requireContext(), "Account Created", Toast.LENGTH_LONG).show()
                                parentFragmentManager.popBackStackImmediate(null, FragmentManager.POP_BACK_STACK_INCLUSIVE)

                                // Navigate to LoginFragment
                                parentFragmentManager.beginTransaction()
                                    .replace(R.id.fragmentContainerView, LoginFragment())
                                    .commit()
                            } else {
                                val error = task.exception?.message ?: "Unknown error"
                                Toast.makeText(requireContext(), "Error: $error", Toast.LENGTH_LONG).show()
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
                Toast.makeText(requireContext(), "Error: ${error.message}", Toast.LENGTH_LONG).show()
            }
        })
    }

    interface InvokeFirst{
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




}
