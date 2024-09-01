package com.example.expensetracker

import android.app.DatePickerDialog
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.appcompat.app.AppCompatActivity
import com.example.expensetracker.data.Transaction
import com.example.expensetracker.databinding.ActivityAddTransactionBinding
import com.google.firebase.database.FirebaseDatabase
import java.util.Calendar

class AddTransactionActivity : AppCompatActivity() {
    private lateinit var binding: ActivityAddTransactionBinding
    private lateinit var categoryMap: Map<String, String>
    private lateinit var goalMap: Map<String, String>
    private lateinit var userID: String
    private lateinit var transType: String

    private var selectedCategoryId: String? = null // To store the selected category ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddTransactionBinding.inflate(layoutInflater)
        setContentView(binding.root)
        userID = intent.getStringExtra("userID").toString()
        transType = intent.getStringExtra("transType").toString()
        Log.d("DEBUGTEST", "UserID: $userID")
        binding.iconBack.setOnClickListener { finish() }
        fetchCategories()

        binding.btnCreate.setOnClickListener {
            Log.d("DEBUGTEST", "BtnClicked")
            getNextTransactionID { newId ->
                Log.d("DEBUGTEST", "Generated New ID: $newId")
                saveTransaction(newId)
            }
        }

        binding.editDate.setOnClickListener{
            openDatePicker()
        }
    }

    private fun fetchCategories() {
        val database = FirebaseDatabase.getInstance("https://expensetracker-a260c-default-rtdb.asia-southeast1.firebasedatabase.app")
        val categoriesRef = database.getReference("Category")
        val goalRef = database.getReference("SavingGoal")
        val userRef = database.getReference("User").child(userID).child("goalList")

        // Fetch user's goalList
        userRef.get().addOnSuccessListener { userSnapshot ->
            val userGoalIds = mutableListOf<String>()
            for (goalSnapshot in userSnapshot.children) {
                val goalId = goalSnapshot.getValue(String::class.java)
                goalId?.let { userGoalIds.add(it) }
            }

            // Fetch categories
            categoriesRef.get().addOnSuccessListener { categorySnapshot ->
                // Filter categories by transType
                categoryMap = categorySnapshot.children.filter {
                    val type = it.child("type").getValue(String::class.java)
                    type == transType // Filter by the transaction type (Expense/Income)
                }.associate {
                    it.key!! to it.child("name").getValue(String::class.java)!!
                }

                // Declare variables for names and IDs to be used across both Expense and Income
                val categoryNames: MutableList<String> = mutableListOf("Select Category")
                val categoryIds: MutableList<String> = mutableListOf("")

                if (transType == "Income") {
                    // If transType is Income, fetch saving goals
                    goalRef.get().addOnSuccessListener { goalSnapshot ->
                        // Filter saving goals to only include those in the user's goalList
                        goalMap = goalSnapshot.children.filter {
                            userGoalIds.contains(it.key)
                        }.associate {
                            it.key!! to it.child("title").getValue(String::class.java)!!
                        }

                        // Sort saving goals alphabetically
                        val sortedGoals = goalMap.toList().sortedBy { (_, value) -> value }.toMap()

                        // Sort categories alphabetically
                        val sortedCategories = categoryMap.toList().sortedBy { (_, value) -> value }.toMap()

                        // Combine sorted saving goals and categories
                        categoryNames.addAll(sortedGoals.values + sortedCategories.values)
                        categoryIds.addAll(sortedGoals.keys + sortedCategories.keys)

                        // Set up the spinner adapter after both categories and saving goals are fetched
                        setupSpinner(categoryNames, categoryIds)

                    }.addOnFailureListener { exception ->
                        Log.e("DEBUGTEST", "Error fetching saving goals: ${exception.message}")
                    }
                } else {
                    // For Expense, sort all categories alphabetically
                    val sortedCategories = categoryMap.toList().sortedBy { (_, value) -> value }.toMap()

                    // Add sorted category data
                    categoryNames.addAll(sortedCategories.values)
                    categoryIds.addAll(sortedCategories.keys)

                    // Set up the spinner adapter immediately
                    setupSpinner(categoryNames, categoryIds)
                }

            }.addOnFailureListener { exception ->
                Log.e("DEBUGTEST", "Error fetching categories: ${exception.message}")
            }

        }.addOnFailureListener { exception ->
            Log.e("DEBUGTEST", "Error fetching user goals: ${exception.message}")
        }
    }

    private fun setupSpinner(categoryNames: List<String>, categoryIds: List<String>) {
        // Set up the spinner adapter
        val adapter = ArrayAdapter(this, R.layout.spinner_category, R.id.text1, categoryNames)
        adapter.setDropDownViewResource(R.layout.spinner_category)
        binding.spinnerCategory.adapter = adapter

        // Handle spinner item selection
        binding.spinnerCategory.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                selectedCategoryId = if (position == 0) null else categoryIds[position]
            }

            override fun onNothingSelected(parent: AdapterView<*>) {
                // Do nothing
            }
        }
    }


    private fun saveTransaction(newId: String) {
        Log.d("DEBUGTEST", "saveTransaction method called with ID: $newId")
        val title = binding.editTitle.text.toString()
        var amount =0.0
        if (transType=="Income"){
            val posAmount = binding.editAmount.text.toString().toDoubleOrNull() ?: 0.0
            if (posAmount<0.0){
                amount = -posAmount
            }else {
                amount = binding.editAmount.text.toString().toDoubleOrNull() ?: 0.0
            }
        }else if (transType=="Expense"){
            val negAmount = binding.editAmount.text.toString().toDoubleOrNull() ?: 0.0
            if (negAmount>0.0){
                amount = -negAmount
            }else{
                amount= binding.editAmount.text.toString().toDoubleOrNull() ?: 0.0
            }
        }
        val date = binding.editDate.text.toString()
        val notes = binding.editNotes.text.toString()
        val categoryId = selectedCategoryId ?: return // Ensure a category is selected
        Log.d("DEBUGTEST", "Selected Category ID: $categoryId")

        if (title.isNotEmpty() && amount != 0.0 && date.isNotEmpty()) {
            val transRef = FirebaseDatabase.getInstance("https://expensetracker-a260c-default-rtdb.asia-southeast1.firebasedatabase.app").getReference("Transaction")

            val transaction = Transaction(newId, title, amount, date,categoryId, notes)
            transRef.child(newId).setValue(transaction).addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    // Update user's transaction list
                    val userRef = FirebaseDatabase.getInstance("https://expensetracker-a260c-default-rtdb.asia-southeast1.firebasedatabase.app").getReference("User").child(userID).child("transactionList")

                    // Get current maximum index for sorting
                    userRef.get().addOnSuccessListener { snapshot ->
                        val maxIndex = snapshot.children.mapNotNull {
                            it.key?.split(":")?.firstOrNull()?.toIntOrNull()
                        }.maxOrNull() ?: 0

                        val newIndex = maxIndex + 1

                        userRef.child(newIndex.toString()).setValue(newId).addOnCompleteListener { userTask ->
                            if (userTask.isSuccessful) {
                                finish() // Close the activity after successful record
                            } else {
                                Log.e("DEBUGTEST", "Error updating user transaction list: ${userTask.exception?.message}")
                            }
                        }
                    }.addOnFailureListener { exception ->
                        Log.e("DEBUGTEST", "Error fetching transaction list for sorting: ${exception.message}", exception)
                    }
                } else {
                    Log.e("DEBUGTEST", "Error recording transaction: ${task.exception?.message}")
                }
            }
        } else {
            // Handle missing fields
            binding.tvErrorMsg.setText("Please fill all required fields")
        }
    }

    private fun getNextTransactionID(callback: (String) -> Unit) {
        val transRef = FirebaseDatabase.getInstance("https://expensetracker-a260c-default-rtdb.asia-southeast1.firebasedatabase.app").getReference("Transaction")

        transRef.get().addOnSuccessListener { snapshot ->
            if (snapshot.exists()) {
                // Extract and parse IDs
                val ids = snapshot.children.mapNotNull {
                    it.child("id").getValue(String::class.java)
                }

                // Extract numeric part from IDs
                val maxId = ids.mapNotNull { id ->
                    id.replace("T", "").toIntOrNull()
                }.maxOrNull() ?: 0

                // Generate new ID
                val newId = "T${maxId + 1}"

                // Invoke callback with the new ID
                callback(newId)
            } else {
                // Handle case where there are no transactions yet
                val newId = "T1"
                callback(newId)
            }
        }.addOnFailureListener { exception ->
            Log.e("AddTransactionActivity", "Error fetching transactions: ${exception.message}", exception)
        }
    }
        private fun openDatePicker() {
            // on below line we are getting
            // the instance of our calendar.
            val c = Calendar.getInstance()

            // on below line we are getting
            // our day, month and year.
            val year = c.get(Calendar.YEAR)
            val month = c.get(Calendar.MONTH)
            val day = c.get(Calendar.DAY_OF_MONTH)

            // on below line we are creating a
            // variable for date picker dialog.
            val datePickerDialog = DatePickerDialog(
                // on below line we are passing context.
                this,
                { view, year, monthOfYear, dayOfMonth ->
                    // on below line we are setting
                    // date to our edit text.
                    val formattedDate = String.format("%02d/%02d/%04d", dayOfMonth, monthOfYear + 1, year)
                    binding.editDate.setText(formattedDate)
                },
                // on below line we are passing year, month
                // and day for the selected date in our date picker.
                year,
                month,
                day
            )
            // at last we are calling show
            // to display our date picker dialog.
            datePickerDialog.show()

        }
    }

