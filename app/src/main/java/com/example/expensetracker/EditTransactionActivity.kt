package com.example.expensetracker

import android.app.DatePickerDialog
import android.app.Dialog
import android.content.Intent
import android.os.Bundle
import android.text.TextUtils
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContentProviderCompat.requireContext
import com.example.expensetracker.data.Transaction
import com.example.expensetracker.databinding.ActivityEditTransactionBinding
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import java.util.Calendar

class EditTransactionActivity : AppCompatActivity() {
    private lateinit var binding: ActivityEditTransactionBinding
    private lateinit var categoryMap: Map<String, String>
    private lateinit var userID: String
    private lateinit var transactionId: String

    private var selectedCategoryId: String? = null // To store the selected category ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEditTransactionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        userID = intent.getStringExtra("userID").toString()
        transactionId = intent.getStringExtra("transactionID").toString()
        if (transactionId == null) {
            Log.e("DEBUGTEST", "Transaction ID is null")
            finish() // End activity if no valid ID is provided
            return
        }
        Log.d("DEBUGTEST", "Editing transaction with ID: $transactionId")
        Log.d("DEBUGTEST", "UserID: $userID")
        Log.d("DEBUGTEST", "TransactionID: $transactionId")

        binding.iconBack.setOnClickListener { finish() }
        binding.btnRemove.setOnClickListener{
            showPopup()
        }
        fetchCategories()
        fetchTransactionDetails()

        binding.btnUpdate.setOnClickListener {
            Log.d("DEBUGTEST", "BtnClicked")
            updateTransaction()
        }

        binding.editDate.setOnClickListener {
            openDatePicker()
        }
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
            binding.spinnerCategory.adapter = adapter

            binding.spinnerCategory.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                    if (position == 0) {
                        selectedCategoryId = null
                    } else {
                        selectedCategoryId = categoryIds[position]
                    }
                }

                override fun onNothingSelected(parent: AdapterView<*>) {
                    // Do nothing
                }
            }
        }.addOnFailureListener { exception ->
            Log.e("DEBUGTEST", "Error fetching categories: ${exception.message}")
        }
    }

    private fun fetchTransactionDetails() {
        val transRef = FirebaseDatabase.getInstance("https://expensetracker-a260c-default-rtdb.asia-southeast1.firebasedatabase.app").getReference("Transaction").child(transactionId)
        transRef.get().addOnSuccessListener { snapshot ->
            val transaction = snapshot.getValue(Transaction::class.java)
            if (transaction != null) {
                binding.editTitle.setText(transaction.title)
                binding.editAmount.setText(String.format("%.2f" ,transaction.amount))
                binding.editDate.setText(transaction.date)
                binding.editNotes.setText(transaction.note)
                selectedCategoryId = transaction.category

                val categoryNames = listOf("Select Category") + categoryMap.values.toList()
                val categoryIds = listOf("") + categoryMap.keys.toList()
                val categoryIndex = categoryIds.indexOf(selectedCategoryId)
                binding.spinnerCategory.setSelection(categoryIndex)
            } else {
                Log.e("DEBUGTEST", "Transaction not found")
            }
        }.addOnFailureListener { exception ->
            Log.e("DEBUGTEST", "Error fetching transaction details: ${exception.message}")
        }
    }

    private fun updateTransaction() {
        Log.d("DEBUGTEST", "updateTransaction method called with ID: $transactionId")
        val title = binding.editTitle.text.toString()
        val amount = binding.editAmount.text.toString().toDoubleOrNull() ?: 0.0
        val date = binding.editDate.text.toString()
        val notes = binding.editNotes.text.toString()
        val categoryId = selectedCategoryId ?: return // Ensure a category is selected
        Log.d("DEBUGTEST", "Selected Category ID: $categoryId")

        if (title.isNotEmpty() && amount != 0.0 && date.isNotEmpty()) {
            val transRef = FirebaseDatabase.getInstance("https://expensetracker-a260c-default-rtdb.asia-southeast1.firebasedatabase.app").getReference("Transaction")

            val transaction = Transaction(transactionId, title, amount, date, categoryId, notes)
            transRef.child(transactionId).setValue(transaction).addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    finish() // Close the activity after successful update
                } else {
                    Log.e("DEBUGTEST", "Error updating transaction: ${task.exception?.message}")
                }
            }
        } else {
            // Handle missing fields
            binding.tvErrorMsg.setText("Please fill all required fields")
        }
    }

    private fun openDatePicker() {
        val c = Calendar.getInstance()
        val year = c.get(Calendar.YEAR)
        val month = c.get(Calendar.MONTH)
        val day = c.get(Calendar.DAY_OF_MONTH)

        val datePickerDialog = DatePickerDialog(
            this,
            { view, year, monthOfYear, dayOfMonth ->
                val formattedDate = String.format("%02d/%02d/%04d", dayOfMonth, monthOfYear + 1, year)
                binding.editDate.setText(formattedDate)
            },
            year,
            month,
            day
        )
        datePickerDialog.show()
    }
    private fun removeTransaction(transactionID: String) {
        val userRef = FirebaseDatabase.getInstance("https://expensetracker-a260c-default-rtdb.asia-southeast1.firebasedatabase.app")
            .getReference("User")
            .child(userID)

        val transactionListRef = userRef.child("transactionList")
        val transactionRef = FirebaseDatabase.getInstance("https://expensetracker-a260c-default-rtdb.asia-southeast1.firebasedatabase.app").getReference("Transaction").child(transactionID)
        transactionListRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                var indexToRemove: String? = null

                // Find the index (key) where the value matches transactionID
                for (child in snapshot.children) {
                    val id = child.value as? String
                    if (id == transactionID) {
                        indexToRemove = child.key
                        break
                    }
                }

                if (indexToRemove != null) {
                    // Remove the index containing transactionID
                    transactionListRef.child(indexToRemove).removeValue()
                        .addOnCompleteListener { task ->
                            if (task.isSuccessful) {
                                Log.d("DEBUGTEST", "Index for transaction ID $transactionID removed from transactionList.")
                            } else {
                                Log.e("DEBUGTEST", "Failed to remove index for transaction ID $transactionID from transactionList: ${task.exception?.message}")
                            }
                        }
                } else {
                    Log.e("DEBUGTEST", "Transaction ID $transactionID not found in transactionList.")
                }

                // Remove the transaction data
                transactionRef.removeValue()
                    .addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            Log.d("DEBUGTEST", "Transaction $transactionID removed successfully.")
                        } else {
                            Log.e("DEBUGTEST", "Failed to remove transaction: ${task.exception?.message}")
                        }
                    }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("DEBUGTEST", "Database error: ${error.message}")
            }
        })
    }


    private fun showPopup() {
        val dialog = Dialog(this)
        dialog.setContentView(R.layout.popup_confirmation)
        dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        dialog.window?.setBackgroundDrawableResource(R.drawable.shadow_bg)

        val btnCancel = dialog.findViewById<View>(R.id.btn_cancel)
        val btnRemove = dialog.findViewById<View>(R.id.btn_remove)

        btnCancel.setOnClickListener {
            dialog.dismiss()
        }
        btnRemove.setOnClickListener {
            removeTransaction(transactionId)
            finish()
        }

        dialog.show()
    }
}
