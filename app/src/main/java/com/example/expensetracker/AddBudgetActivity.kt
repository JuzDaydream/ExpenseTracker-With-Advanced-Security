package com.example.expensetracker

import android.app.DatePickerDialog
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ImageView
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton
import com.example.expensetracker.data.Budget
import com.google.firebase.database.FirebaseDatabase
import java.util.Calendar

class AddBudgetActivity : AppCompatActivity() {
    private lateinit var btnCreate: AppCompatButton
    private lateinit var spinnerCategory: Spinner
    private lateinit var editDate: EditText
    private lateinit var editEndDate: EditText
    private lateinit var userID: String
    private var selectedCategoryId: String? = null

    private val startDate = Calendar.getInstance()
    private val endDate = Calendar.getInstance()
    private lateinit var editAmount: EditText
    private lateinit var tvErrorMsg: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_budget)

        userID = intent.getStringExtra("userID").toString()
        Log.d("DEBUGTEST", "UserID: $userID")
        editDate = findViewById(R.id.edit_Date)
        editEndDate = findViewById(R.id.edit_EndDate)
        spinnerCategory = findViewById(R.id.spinner_category)
        btnCreate = findViewById(R.id.btn_create)
        editAmount = findViewById(R.id.edit_amount)
        tvErrorMsg = findViewById(R.id.tv_errorMsg)

        val iconBack: ImageView = findViewById(R.id.icon_back)
        iconBack.setOnClickListener {
            finish()
        }

        editDate.setOnClickListener {
            showDatePickerDialog(editDate, isStartDate = true)
        }
        editEndDate.setOnClickListener {
            showDatePickerDialog(editEndDate, isStartDate = false)
        }

        fetchCategories()

        btnCreate.setOnClickListener {
            getNextBudgetID { newId ->
                saveBudget(newId)
            }
        }

        addFieldWatchers()
    }

    private fun fetchCategories() {
        val categoriesRef = FirebaseDatabase.getInstance("https://expensetracker-a260c-default-rtdb.asia-southeast1.firebasedatabase.app")
            .getReference("Category")
        categoriesRef.get().addOnSuccessListener { snapshot ->
            val categoryMap = snapshot.children.associate {
                it.key!! to it.child("name").getValue(String::class.java)!!
            }

            val categoryNames = listOf("Select Category") + categoryMap.values.toList()
            val categoryIds = listOf("") + categoryMap.keys.toList()

            val adapter = ArrayAdapter(this, R.layout.spinner_category, R.id.text1, categoryNames)
            adapter.setDropDownViewResource(R.layout.spinner_category)
            spinnerCategory.adapter = adapter

            spinnerCategory.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                    selectedCategoryId = if (position == 0) null else categoryIds[position]
                    checkFieldsForEmptyValues()
                }

                override fun onNothingSelected(parent: AdapterView<*>) {}
            }
        }.addOnFailureListener { exception ->
            Log.e("DEBUGTEST", "Error fetching categories: ${exception.message}")
        }
    }

    private fun showDatePickerDialog(editText: EditText, isStartDate: Boolean) {
        val calendar = if (isStartDate) startDate else endDate
        val currentDate = Calendar.getInstance()

        val datePickerDialog = DatePickerDialog(
            this,
            { _, selectedYear, selectedMonth, selectedDay ->
                calendar.set(selectedYear, selectedMonth, selectedDay)

                val formattedDate = String.format("%02d/%02d/%04d", selectedDay,selectedMonth + 1,selectedYear)
                editText.setText(formattedDate)

                if (!isStartDate) {
                    // Allow end date to be the same or after the start date
                    if ( endDate.timeInMillis < startDate.timeInMillis) {
                        editEndDate.error = "End date cannot be before start date."
                        editEndDate.setText("")
                    }
                }
                checkFieldsForEmptyValues()
            },
            calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)
        )

        // Set the minimum date for end date selection to the start date
        if (!isStartDate) {
            datePickerDialog.datePicker.minDate = startDate.timeInMillis
        } else {
            datePickerDialog.datePicker.minDate = currentDate.timeInMillis
        }

        datePickerDialog.show()
    }

    private fun getNextBudgetID(callback: (String) -> Unit) {
        val budgetsRef = FirebaseDatabase.getInstance("https://expensetracker-a260c-default-rtdb.asia-southeast1.firebasedatabase.app")
            .getReference("Budget")

        budgetsRef.get().addOnSuccessListener { snapshot ->
            val maxId = snapshot.children.mapNotNull {
                it.child("id").getValue(String::class.java)?.replace("B", "")?.toIntOrNull()
            }.maxOrNull() ?: 0

            val newId = "B${maxId + 1}"
            callback(newId)
        }.addOnFailureListener { exception ->
            Log.e("AddBudget", "Error fetching budgets: ${exception.message}", exception)
        }
    }

    private fun saveBudget(newId: String) {
        val amount = editAmount.text.toString().toDoubleOrNull() ?: 0.0
        val startDateStr = editDate.text.toString()
        val endDateStr = editEndDate.text.toString()
        val categoryId = selectedCategoryId ?: return

        // First, check the user's budget list for duplicates
        val userRef = FirebaseDatabase.getInstance("https://expensetracker-a260c-default-rtdb.asia-southeast1.firebasedatabase.app")
            .getReference("User").child(userID).child("budgetList")

        userRef.get().addOnSuccessListener { snapshot ->
            if (snapshot.exists()) {
                val budgetIds = snapshot.children.mapNotNull { it.getValue(String::class.java) }

                val budgetsRef = FirebaseDatabase.getInstance("https://expensetracker-a260c-default-rtdb.asia-southeast1.firebasedatabase.app")
                    .getReference("Budget")

                budgetsRef.get().addOnSuccessListener { budgetSnapshot ->
                    val isDuplicate = budgetIds.any { budgetId ->
                        val budget = budgetSnapshot.child(budgetId)
                        val budgetCategoryId = budget.child("category").getValue(String::class.java)
                        val budgetStartDate = budget.child("startDate").getValue(String::class.java)
                        val budgetEndDate = budget.child("endDate").getValue(String::class.java)

                        budgetCategoryId == categoryId && budgetStartDate == startDateStr && budgetEndDate == endDateStr
                    }

                    if (isDuplicate) {
                        Log.e("DEBUGTEST", "Budget with the same category, start date, and end date already exists.")
                        tvErrorMsg.text = "A budget with the same category, start date, and end date already exists."
                    } else {
                        // Proceed to save the new budget
                        saveNewBudget(newId, amount, startDateStr, endDateStr, categoryId)
                    }
                }.addOnFailureListener { exception ->
                    Log.e("AddBudget", "Error fetching budgets: ${exception.message}", exception)
                }
            } else {
                // No existing budget list, safe to proceed
                saveNewBudget(newId, amount, startDateStr, endDateStr, categoryId)
            }
        }.addOnFailureListener { exception ->
            Log.e("DEBUGTEST", "Error fetching user budget list: ${exception.message}")
        }
    }

    private fun saveNewBudget(newId: String, amount: Double, startDateStr: String, endDateStr: String, categoryId: String) {
        if (amount != 0.0 && startDateStr.isNotEmpty() && endDateStr.isNotEmpty()) {
            val budgetsRef = FirebaseDatabase.getInstance("https://expensetracker-a260c-default-rtdb.asia-southeast1.firebasedatabase.app")
                .getReference("Budget")

            // Create Budget object with user input
            val budget = Budget(newId, amount, startDateStr, endDateStr, categoryId)

            // Save the Budget object to Firebase
            budgetsRef.child(newId).setValue(budget).addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    updateUserBudgetList(newId)
                } else {
                    Log.e("DEBUGTEST", "Error saving budget: ${task.exception?.message}")
                }
            }
        } else {
            Log.e("DEBUGTEST", "Missing required fields")
            tvErrorMsg.text = "Please fill in all required fields."
        }
    }




    private fun updateUserBudgetList(newId: String) {
        val userRef = FirebaseDatabase.getInstance("https://expensetracker-a260c-default-rtdb.asia-southeast1.firebasedatabase.app")
            .getReference("User").child(userID).child("budgetList")

        userRef.get().addOnSuccessListener { snapshot ->
            if (snapshot.exists()) {
                val maxIndex = snapshot.children.mapNotNull {
                    it.key?.toIntOrNull()
                }.maxOrNull() ?: 0

                val newIndex = maxIndex + 1

                userRef.child(newIndex.toString()).setValue(newId).addOnCompleteListener { userTask ->
                    if (userTask.isSuccessful) {
                        Log.d("DEBUGTEST", "Budget list updated successfully for user: $userID")
                        finish()
                    } else {
                        Log.e("DEBUGTEST", "Error updating user budget list: ${userTask.exception?.message}")
                    }
                }
            } else {
                Log.e("DEBUGTEST", "User budget list does not exist or is empty.")
            }
        }.addOnFailureListener { exception ->
            Log.e("DEBUGTEST", "Error fetching user budget list: ${exception.message}")
        }
    }

    private fun addFieldWatchers() {
        editDate.addTextChangedListener(fieldWatcher)
        editEndDate.addTextChangedListener(fieldWatcher)
        editAmount.addTextChangedListener(fieldWatcher)
    }

    private val fieldWatcher = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

        override fun afterTextChanged(s: Editable?) {
            checkFieldsForEmptyValues()
            validateDateOrder()
        }
    }

    private fun checkFieldsForEmptyValues() {
        val isStartDateFilled = editDate.text.isNotBlank()
        val isEndDateFilled = editEndDate.text.isNotBlank()
        val isCategorySelected = selectedCategoryId != null
        val isAmountValid = editAmount.text.toString().toDoubleOrNull() != null

        btnCreate.isEnabled = isStartDateFilled && isEndDateFilled && isCategorySelected && isAmountValid
    }

    private fun validateDateOrder() {
        if (startDate.timeInMillis > endDate.timeInMillis) {
            tvErrorMsg.text = "Start date cannot be after end date."
            btnCreate.isEnabled = false
        } else {
            tvErrorMsg.text = ""
        }
    }
}