package com.example.expensetracker

import android.app.DatePickerDialog
import android.app.Dialog
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ImageView
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton
import com.example.expensetracker.data.Budget
import com.example.expensetracker.databinding.ActivityEditBudgetBinding
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import java.util.Calendar

class EditBudgetActivity : AppCompatActivity() {
    private lateinit var spinnerCategory: Spinner
    private lateinit var binding: ActivityEditBudgetBinding
    private var selectedCategoryId: String? = null
    private lateinit var categoryMap: Map<String, String>
    private lateinit var budgetId: String
    private lateinit var userId: String
    private lateinit var btnUpdate: AppCompatButton
    private lateinit var btnRemove: AppCompatButton
    private lateinit var editDate: EditText
    private lateinit var editEndDate: EditText
    private lateinit var editAmount: EditText
    private lateinit var tvErrorMsg: TextView
    private val startDate = Calendar.getInstance()
    private val endDate = Calendar.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEditBudgetBinding.inflate(layoutInflater)
        setContentView(binding.root)
        editDate = findViewById(R.id.edit_Date)
        editEndDate = findViewById(R.id.edit_EndDate)
        spinnerCategory = findViewById(R.id.spinner_category)
        btnUpdate = findViewById(R.id.btn_update)
        btnRemove = findViewById(R.id.btn_remove)
        editAmount = findViewById(R.id.edit_amount)
        tvErrorMsg = findViewById(R.id.tv_errorMsg)
        budgetId = intent.getStringExtra("budgetID")?.takeIf { it.isNotEmpty() } ?: run {
            Log.e("DEBUGTEST", "Budget ID is null or empty")
            finish()
            return
        }

        userId = intent.getStringExtra("userID")?.takeIf { it.isNotEmpty() } ?: run {
            Log.e("DEBUGTEST", "User ID is null or empty")
            finish()
            return
        }
        Log.d("DEBUGTEST", "Successfully retrieved Budget ID: $budgetId and User ID: $userId")

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
        fetchBudget()
        addFieldWatchers()
        btnUpdate.setOnClickListener {
            updateBudget(budgetId)
        }
        btnRemove.setOnClickListener {
            showPopup()
        }
    }

    private fun showDatePickerDialog(editText: EditText, isStartDate: Boolean) {
        val calendar = if (isStartDate) startDate else endDate
        val currentDate = Calendar.getInstance()

        val datePickerDialog = DatePickerDialog(
            this,
            { _, selectedYear, selectedMonth, selectedDay ->
                calendar.set(selectedYear, selectedMonth, selectedDay)
                val formattedDate = "${selectedDay}/${selectedMonth + 1}/${selectedYear}"
                editText.setText(formattedDate)
                if (!isStartDate && endDate.timeInMillis < startDate.timeInMillis) {
                    editEndDate.error = "End date cannot be before start date."
                    editEndDate.setText("")
                    btnUpdate.isEnabled = false
                    btnRemove.isEnabled = false
                }
                checkFieldsForEmptyValues()
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        )

        if (!isStartDate) {
            datePickerDialog.datePicker.minDate = startDate.timeInMillis
        } else {
            datePickerDialog.datePicker.minDate = currentDate.timeInMillis
        }

        datePickerDialog.show()
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

    private fun validateDateOrder() {
        if (startDate.timeInMillis > endDate.timeInMillis) {
            tvErrorMsg.text = "Start date cannot be after end date."
            btnUpdate.isEnabled = false
            btnRemove.isEnabled = false
        } else {
            tvErrorMsg.text = ""
        }
    }

    private fun checkFieldsForEmptyValues() {
        val isStartDateFilled = editDate.text.isNotBlank()
        val isEndDateFilled = editEndDate.text.isNotBlank()
        val isCategorySelected = selectedCategoryId != null
        val isAmountValid = editAmount.text.toString().toDoubleOrNull() != null
        btnRemove.isEnabled = isStartDateFilled && isEndDateFilled && isCategorySelected && isAmountValid
        btnUpdate.isEnabled = isStartDateFilled && isEndDateFilled && isCategorySelected && isAmountValid
    }

    private fun fetchBudget() {
        val budgetRef = FirebaseDatabase.getInstance("https://expensetracker-a260c-default-rtdb.asia-southeast1.firebasedatabase.app")
            .getReference("Budget")
            .child(budgetId)

        budgetRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    val startDate = snapshot.child("startDate")?.value as? String ?: ""
                    val endDate = snapshot.child("endDate")?.value as? String ?: ""
                    val amount = snapshot.child("amount")?.value as? Long ?: 0L
                    val categoryId = snapshot.child("category")?.value as? String ?: ""
                    val amountDouble = amount.toDouble()

                    // Set the values of the UI components using the retrieved budget information
                    selectedCategoryId = categoryId
                    editAmount.setText(String.format("%.2f" ,amountDouble))
                    editDate.setText(startDate)
                    editEndDate.setText(endDate)

                    // Log retrieved values for debugging
                    Log.d("DEBUGTEST", "Budget details - Start Date: $startDate, End Date: $endDate, Amount: $amountDouble, Category ID: $categoryId")

                    // Set spinner selection to the budget's category
                    if (this@EditBudgetActivity::categoryMap.isInitialized) {
                        setSpinnerSelection(categoryId)
                    }
                } else {
                    finish()
                }
            }

            override fun onCancelled(error: DatabaseError) {
                // Handle error
            }
        })
    }

    private fun fetchCategories() {
        val categoriesRef = FirebaseDatabase.getInstance("https://expensetracker-a260c-default-rtdb.asia-southeast1.firebasedatabase.app").getReference("Category")
        categoriesRef.get().addOnSuccessListener { snapshot ->
            categoryMap = snapshot.children.associate {
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
                    Log.d("DEBUGTEST", "Selected Category ID: $selectedCategoryId")
                    checkFieldsForEmptyValues()
                }

                override fun onNothingSelected(parent: AdapterView<*>) {}
            }

            // Log category map for debugging
            Log.d("DEBUGTEST", "Fetched categories: $categoryMap")

        }.addOnFailureListener { exception ->
            Log.e("DEBUGTEST", "Error fetching categories: ${exception.message}")
        }
    }

    private fun setSpinnerSelection(categoryId: String?) {
        val categoryIds = listOf("") + categoryMap.keys.toList()
        val categoryIndex = categoryIds.indexOf(categoryId)
        if (categoryIndex >= 0) {
            spinnerCategory.setSelection(categoryIndex)
            Log.d("DEBUGTEST", "Spinner set to category index: $categoryIndex")
        } else {
            Log.d("DEBUGTEST", "Category ID $categoryId not found in category map")
        }
    }

    private fun updateBudget(budgetId: String) {
        val budgetRef = FirebaseDatabase.getInstance("https://expensetracker-a260c-default-rtdb.asia-southeast1.firebasedatabase.app")
            .getReference("Budget")
            .child(budgetId)

        val budgetData = Budget(
            amount = editAmount.text.toString().toDouble(),
            startDate = editDate.text.toString(),
            endDate = editEndDate.text.toString(),
            category = selectedCategoryId!!
        )

        budgetRef.setValue(budgetData)
            .addOnSuccessListener {
                Log.d("DEBUGTEST", "Budget updated successfully")
                finish()
            }
            .addOnFailureListener { exception ->
                Log.e("DEBUGTEST", "Error updating budget: ${exception.message}")
            }
    }

    private fun showPopup() {
        val dialog = Dialog(this)
        dialog.setContentView(R.layout.popup_confirmation)
        dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        dialog.window?.setBackgroundDrawableResource(R.drawable.shadow_bg)

        val btnDeleteBudget = dialog.findViewById<AppCompatButton>(R.id.btn_remove)
        btnDeleteBudget.setOnClickListener {
            deleteBudget(budgetId)
            dialog.dismiss()
            finish()

        }

        val btnCancel = dialog.findViewById<AppCompatButton>(R.id.btn_cancel)
        btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun deleteBudget(budgetId: String) {
        val userRef = FirebaseDatabase.getInstance("https://expensetracker-a260c-default-rtdb.asia-southeast1.firebasedatabase.app")
            .getReference("User")
            .child(userId)

        val budgetListRef = userRef.child("budgetList")
        val budgetRef = FirebaseDatabase.getInstance("https://expensetracker-a260c-default-rtdb.asia-southeast1.firebasedatabase.app").getReference("Budget").child(budgetId)
        budgetListRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                var indexToRemove: String? = null

                // Find the index (key) where the value matches transactionID
                for (child in snapshot.children) {
                    val id = child.value as? String
                    if (id == budgetId) {
                        indexToRemove = child.key
                        break
                    }
                }

                if (indexToRemove != null) {
                    // Remove the index containing transactionID
                    budgetListRef.child(indexToRemove).removeValue()
                        .addOnCompleteListener { task ->
                            if (task.isSuccessful) {
                                Log.d("DEBUGTEST", "Index for budget ID $budgetId removed from budgetList.")
                            } else {
                                Log.e("DEBUGTEST", "Failed to remove index for budget ID $budgetId from budgetList: ${task.exception?.message}")
                            }
                        }
                } else {
                    Log.e("DEBUGTEST", "Transaction ID $budgetId not found in budgetList.")
                }

                // Remove the transaction data
                budgetRef.removeValue()
                    .addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            Log.d("DEBUGTEST", "Budget $budgetId removed successfully.")
                        } else {
                            Log.e("DEBUGTEST", "Failed to remove budget: ${task.exception?.message}")
                        }
                    }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("DEBUGTEST", "Database error: ${error.message}")
            }
        })
    }
}
