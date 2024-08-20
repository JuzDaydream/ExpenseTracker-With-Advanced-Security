package com.example.expensetracker

import android.app.DatePickerDialog
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.util.Log
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.expensetracker.data.Transaction
import com.google.firebase.database.FirebaseDatabase
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class SpendingAnalysisActivity : AppCompatActivity() {
    private lateinit var btnCreate: AppCompatButton
    private lateinit var editDate: EditText
    private lateinit var editEndDate: EditText
    private lateinit var tvErrorMsg: TextView
    private lateinit var userId: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_spending_analysis)

        btnCreate = findViewById(R.id.btn_create)
        editDate = findViewById(R.id.edit_StartDate)
        editEndDate = findViewById(R.id.edit_EndDate)
        tvErrorMsg = findViewById(R.id.tv_errorMsg)
        val userId = intent.getStringExtra("userID") ?: return

        Log.d("SpendingAnalysisActivity", "Received userId: $userId")

        val iconBack: ImageView = findViewById(R.id.icon_back)
        iconBack.setOnClickListener {
            finish()
        }

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        editDate.setOnClickListener {
            showDatePicker(editDate)
        }

        editEndDate.setOnClickListener {
            showDatePicker(editEndDate)
        }

        btnCreate.isEnabled = false

        btnCreate.setOnClickListener {
            val startDate = editDate.text.toString()
            val endDate = editEndDate.text.toString()

            val intent = Intent(this, SpendingAnalysisCreatedActivity::class.java)
            intent.putExtra("userID", userId)
            intent.putExtra("startDate", startDate)
            intent.putExtra("endDate", endDate)
            startActivity(intent)
        }

        editDate.afterTextChanged {
            if (it != null) {
                validateDates(userId)
            }
        }

        editEndDate.afterTextChanged {
            if (it != null) {
                validateDates(userId)
            }
        }
    }

    private fun showDatePicker(editText: EditText) {
        val datePickerDialog = DatePickerDialog(
            this,
            { _, year, month, day ->
                val calendar = Calendar.getInstance()
                calendar.set(year, month, day)
                val dateFormat = "dd/MM/yyyy" // Change the date format to dd/MM/yyyy
                val date = SimpleDateFormat(dateFormat, Locale.US).format(calendar.time)
                editText.setText(date)
            },
            Calendar.getInstance().get(Calendar.YEAR),
            Calendar.getInstance().get(Calendar.MONTH),
            Calendar.getInstance().get(Calendar.DAY_OF_MONTH)
        )
        datePickerDialog.show()
    }

    private fun validateDates(userId: String) {
        val startDate = editDate.text.toString()
        val endDate = editEndDate.text.toString()

        if (startDate.isNotEmpty() && endDate.isNotEmpty()) {
            val startDateCalendar = Calendar.getInstance()
            val endDateCalendar = Calendar.getInstance()

            val startDateArray = startDate.split("/")
            val endDateArray = endDate.split("/")

            startDateCalendar.set(
                startDateArray[2].toInt(),
                startDateArray[1].toInt() - 1,
                startDateArray[0].toInt()
            )
            endDateCalendar.set(
                endDateArray[2].toInt(),
                endDateArray[1].toInt() - 1,
                endDateArray[0].toInt()
            )

            if (startDateCalendar.time.after(endDateCalendar.time)) {
                tvErrorMsg.text = "Start date cannot be after end date"
                btnCreate.isEnabled = false
            } else if (endDateCalendar.time.before(startDateCalendar.time)) {
                tvErrorMsg.text = "End date cannot be before start date"
                btnCreate.isEnabled = false
            } else {
                checkTransactionsExistence(userId)
            }
        } else {
            tvErrorMsg.text = ""
            btnCreate.isEnabled = false
        }
    }




    private fun checkTransactionsExistence(userId: String) {
        val startDateStr = editDate.text.toString()
        val endDateStr = editEndDate.text.toString()
        val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.US)

        // Parse the start and end dates
        val startDate = if (startDateStr.isNotEmpty()) sdf.parse(startDateStr) else null
        val endDate = if (endDateStr.isNotEmpty()) sdf.parse(endDateStr) else null

        if (startDate == null || endDate == null) {
            Log.e("DEBUGTEST", "Start or end date is null")
            tvErrorMsg.text = "Invalid date format"
            btnCreate.isEnabled = false
            return
        }

        Log.d("DEBUGTEST", "Checking transactions for user: $userId from $startDateStr to $endDateStr")

        val userRef = FirebaseDatabase.getInstance("https://expensetracker-a260c-default-rtdb.asia-southeast1.firebasedatabase.app")
            .getReference("User").child(userId).child("transactionList")

        userRef.get().addOnSuccessListener { snapshot ->
            if (snapshot.exists()) {
                val transactionIds = snapshot.children.mapNotNull { it.getValue(String::class.java) }
                Log.d("DEBUGTEST", "Retrieved transaction IDs: $transactionIds")

                val transactionsRef = FirebaseDatabase.getInstance("https://expensetracker-a260c-default-rtdb.asia-southeast1.firebasedatabase.app")
                    .getReference("Transaction")

                transactionsRef.get().addOnSuccessListener { transactionSnapshot ->
                    val isDuplicate = transactionIds.any { transactionId ->
                        val transaction = transactionSnapshot.child(transactionId).getValue(Transaction::class.java)

                        transaction?.let {
                            val transactionDate = if (it.date.isNotEmpty()) sdf.parse(it.date) else null
                            val isInRange = transactionDate != null && transactionDate >= startDate && transactionDate <= endDate
                            val isNegativeAmount = it.amount < 0
                            Log.d("DEBUGTEST", "Transaction ID $transactionId with date ${it.date} is in range: $isInRange, amount: ${it.amount}, is negative: $isNegativeAmount")
                            isInRange && isNegativeAmount
                        } ?: false
                    }

                    if (isDuplicate) {
                        Log.i("DEBUGTEST", "Transactions found within the specified period.")
                        tvErrorMsg.text = ""
                        btnCreate.isEnabled = true
                    } else {
                        Log.w("DEBUGTEST", "No transactions found within the specified period.")
                        tvErrorMsg.text = "No expense found in this period"
                        btnCreate.isEnabled = false
                    }
                }.addOnFailureListener { exception ->
                    Log.e("DEBUGTEST", "Error fetching transactions: ${exception.message}", exception)
                }
            } else {
                Log.w("DEBUGTEST", "No transaction IDs found for user: $userId")
                tvErrorMsg.text = "No expense found in this period"
                btnCreate.isEnabled = false
            }
        }.addOnFailureListener { exception ->
            Log.e("DEBUGTEST", "Error fetching user transaction list: ${exception.message}", exception)
        }
    }

    fun EditText.afterTextChanged(afterTextChanged: (String?) -> Unit) {
        this.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable?) {
                afterTextChanged.invoke(s?.toString())
            }
        })
    }
}