package com.example.expensetracker

import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class SpendingAnalysisCreatedActivity : AppCompatActivity() {
    private lateinit var databaseReference: DatabaseReference
    private lateinit var userID: String
    private lateinit var tvAmount: TextView
    private lateinit var lineChart: LineChart
    private val transactionList = ArrayList<Transaction>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_spending_analysis_created)

        userID = intent.getStringExtra("userID") ?: return
        Log.d("SpendingAnalysisActivity", "Received userId: $userID")

        tvAmount = findViewById(R.id.tv_amount)
        lineChart = findViewById(R.id.chart_top)

        val iconBack: ImageView = findViewById(R.id.icon_back)
        iconBack.setOnClickListener {
            finish()
        }

        databaseReference = FirebaseDatabase.getInstance("https://expensetracker-a260c-default-rtdb.asia-southeast1.firebasedatabase.app")
            .getReference("User").child(userID).child("transactionList")

        fetchAllTransactions()
    }

    private fun fetchAllTransactions() {
        databaseReference.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                transactionList.clear()
                if (snapshot.exists()) {
                    val transactionCount = snapshot.childrenCount.toInt()
                    for (transactionSnapshot in snapshot.children) {
                        val transactionId = transactionSnapshot.value.toString()
                        fetchTransactionDetails(transactionId, transactionCount)
                    }
                } else {
                    Log.d("CreateSpendAnalysis", "No transactions found")
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("CreateSpendAnalysis", "Database error: ${error.message}")
            }
        })
    }

    private fun fetchTransactionDetails(transactionId: String, transactionCount: Int) {
        val transRef = FirebaseDatabase.getInstance("https://expensetracker-a260c-default-rtdb.asia-southeast1.firebasedatabase.app")
            .getReference("Transaction").child(transactionId)

        transRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(transactionSnapshot: DataSnapshot) {
                if (transactionSnapshot.exists()) {
                    val transaction = transactionSnapshot.getValue(Transaction::class.java)
                    if (transaction != null && transaction.amount < 0) {
                        transactionList.add(transaction)
                        Log.d("CreateSpendAnalysis", "Added transaction: ID=${transaction.id}, Amount=${transaction.amount}, Date=${transaction.date}")
                    }

                    sortTransactions()
                    updateUI()

                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("CreateSpendAnalysis", "Database error: ${error.message}")
            }
        })
    }

    private fun sortTransactions(): List<List<Transaction>> {
        Log.d("CreateSpendAnalysis", "Sorting transactions...")
        try {
            val sortedTransactions = transactionList.sortedBy { SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).parse(it.date) }
            Log.d("CreateSpendAnalysis", "Transactions sorted successfully.")
            Log.d("CreateSpendAnalysis", "Sorted transactions: $sortedTransactions")

            val yearTransactions = sortedTransactions.groupBy { SimpleDateFormat("yyyy", Locale.getDefault()).format(SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).parse(it.date)!!) }
                .map { entry -> entry.value }
            Log.d("CreateSpendAnalysis", "Transactions grouped by year: $yearTransactions")
            return yearTransactions
        } catch (e: ParseException) {
            Log.e("CreateSpendAnalysis", "Error parsing date: ${e.message}")
            return emptyList()
        }
    }
    private fun updateUI() {
        val yearTransactions = sortTransactions()
        val lineData = LineData()
        var totalAmount = 0.0

        for (yearTransaction in yearTransactions) {
            val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
            val calendar = Calendar.getInstance()

            // Process transactions for chart
            val dataMap = mutableMapOf<String, Double>() // Key: Month-Year, Value: Total Amount
            for (transaction in yearTransaction) {
                try {
                    calendar.time = dateFormat.parse(transaction.date) ?: continue
                    val monthYear = SimpleDateFormat("MMM yyyy", Locale.getDefault()).format(calendar.time)
                    dataMap[monthYear] = (dataMap[monthYear] ?: 0.0) + Math.abs(transaction.amount)
                    totalAmount += Math.abs(transaction.amount)
                } catch (e: Exception) {
                    Log.e("CreateSpendAnalysis", "Date parsing error: ${e.message}")
                }
            }

            // Create line chart objects for each year
            val months = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
            val monthValues = months.map { 0.0 }.toMutableList()

            // Update the month values with the transaction amounts
            for ((date, amount) in dataMap) {
                val month = date.substringBefore(" ")
                val index = months.indexOf(month)
                if (index != -1) {
                    monthValues[index] = amount
                }
            }

            // Create the entries for the line chart
            val entries = monthValues.mapIndexed { index, value -> Entry(index.toFloat(), value.toFloat()) }
            val year = SimpleDateFormat("yyyy", Locale.getDefault()).format(SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).parse(yearTransaction.first().date)!!)
            val dataSet = LineDataSet(entries, "YEAR $year")
            dataSet.color = getRandomColor()
            dataSet.valueTextColor = Color.WHITE

            lineData.addDataSet(dataSet)
        }

        lineChart.data = lineData
        lineChart.description.isEnabled = false
        lineChart.invalidate() // refresh chart

        // Set the x-axis values
        val xAxis = lineChart.xAxis
        xAxis.valueFormatter = IndexAxisValueFormatter(listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"))

        // Display total amount on tv_amount
        tvAmount.text = "RM ${String.format("%.2f", totalAmount)}"
    }
    private fun getRandomColor(): Int {
        val random = java.util.Random()
        return Color.argb(255, random.nextInt(256), random.nextInt(256), random.nextInt(256))
    }
    data class Transaction(
        val id: String = "",
        val amount: Double = 0.0,
        val date: String = "" // Date in "yyyy-MM-dd" format
    )
}