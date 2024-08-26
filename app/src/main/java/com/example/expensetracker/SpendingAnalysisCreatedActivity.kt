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
    private lateinit var databaseReference1: DatabaseReference
    private lateinit var userID: String
    private lateinit var tvAmount: TextView
    private lateinit var lineChart: LineChart
    private lateinit var lineChartBottom: LineChart
    private val transactionList = ArrayList<Transaction>()
    private val transactionList1 = ArrayList<Transaction>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_spending_analysis_created)

        userID = intent.getStringExtra("userID") ?: return
        Log.d("SpendingAnalysisActivity", "Received userId: $userID")

        tvAmount = findViewById(R.id.tv_amount)
        lineChart = findViewById(R.id.chart_top)
        lineChartBottom=findViewById(R.id.chart_bottom)

        val iconBack: ImageView = findViewById(R.id.icon_back)
        iconBack.setOnClickListener {
            finish()
        }

        databaseReference = FirebaseDatabase.getInstance("https://expensetracker-a260c-default-rtdb.asia-southeast1.firebasedatabase.app")
            .getReference("User").child(userID).child("transactionList")
        databaseReference1 = FirebaseDatabase.getInstance("https://expensetracker-a260c-default-rtdb.asia-southeast1.firebasedatabase.app")
            .getReference("User").child(userID).child("transactionList")

        fetchAllTransactions()
        fetchAllTransactions1()
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
    private fun identifyPeaksAndValleys(values: List<Double>): List<String> {
        val labels = mutableListOf<String>()

        for (i in 1 until values.size - 1) {
            if (values[i] > values[i - 1] && values[i] > values[i + 1]) {
                labels.add("Peak at ${i + 1} month: ${values[i]}")
            } else if (values[i] < values[i - 1] && values[i] < values[i + 1]) {
                labels.add("Valley at ${i + 1} month: ${values[i]}")
            }
        }

        return labels
    }

    private fun updateUI() {
        val yearTransactions = sortTransactions()
        val lineData = LineData()
        var totalAmount = 0.0
        val peakSpendingInfo = mutableListOf<String>()

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
            // Identify peaks and valleys
            /*val peaksAndValleys = identifyPeaksAndValleys(monthValues)
            Log.d("PeaksAndValleys", peaksAndValleys.toString())
            // Identify the peak (highest value)
            val maxIndex = monthValues.indices.maxByOrNull { monthValues[it] }
            if (maxIndex != null) {
                peakYear = SimpleDateFormat("yyyy", Locale.getDefault()).format(SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).parse(yearTransaction.first().date)!!)
                peakMonth = months[maxIndex]
            }*/
            // Identify the peak (highest value)

            // Identify the peak (highest value)
            val maxIndex = monthValues.indices.maxByOrNull { monthValues[it] }
            if (maxIndex != null) {
                val peakYear = SimpleDateFormat("yyyy", Locale.getDefault()).format(SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).parse(yearTransaction.first().date)!!)
                val peakMonth = months[maxIndex]
                val peakAmount = monthValues[maxIndex]
                peakSpendingInfo.add("The peak spending for year $peakYear is at month $peakMonth with an amount of RM ${String.format("%.2f", peakAmount)}.")
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

        // Print peak spending for each year
        findViewById<TextView>(R.id.tv_chart_bottom_label).text = peakSpendingInfo.joinToString("\n")
    }
    private fun getRandomColor(): Int {
        val random = java.util.Random()
        return Color.argb(255, random.nextInt(256), random.nextInt(256), random.nextInt(256))
    }

    data class Transaction(
        val id: String = "",
        val amount: Double = 0.0,
        val date: String = "", // Date in "yyyy-MM-dd" format
        val category: String = "" // Add this property
    )

    private fun fetchAllTransactions1() {
        databaseReference1.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                transactionList1.clear()
                if (snapshot.exists()) {
                    val transactionCount = snapshot.childrenCount.toInt()
                    for (transactionSnapshot in snapshot.children) {
                        val transactionId = transactionSnapshot.value.toString()

                        fetchTransactionDetailsForChartBottom(transactionId, transactionCount)
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



    private fun fetchTransactionDetailsForChartBottom(transactionId: String, transactionCount: Int) {
        val transRef = FirebaseDatabase.getInstance("https://expensetracker-a260c-default-rtdb.asia-southeast1.firebasedatabase.app")
            .getReference("Transaction").child(transactionId)

        transRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(transactionSnapshot: DataSnapshot) {
                if (transactionSnapshot.exists()) {
                    val transaction = transactionSnapshot.getValue(Transaction::class.java)
                    if (transaction != null && transaction.amount < 0) {
                        transactionList1.add(transaction)
                        Log.d("CreateSpendAnalysis", "Added transaction: ID=${transaction.id}, Amount=${transaction.amount}, Date=${transaction.date}, Category ID=${transaction.category}")
                    }

                    sortTransactionsForChartBottom()
                    updateUIForChartBottom()

                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("CreateSpendAnalysis", "Database error: ${error.message}")
            }
        })
    }

    private fun updateUIForChartBottom() {
        val categoryTransactions = sortTransactionsForChartBottom()
        val lineDataBottom = LineData()


        for (categoryTransaction in categoryTransactions) {
            val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
            val calendar = Calendar.getInstance()

            // Process transactions for chart bottom
            val dataMapBottom = mutableMapOf<String, Double>() // Key: Month-Year, Value: Total Amount
            for (transaction in categoryTransaction) {
                try {
                    calendar.time = dateFormat.parse(transaction.date) ?: continue
                    val monthYear = SimpleDateFormat("MMM yyyy", Locale.getDefault()).format(calendar.time)
                    dataMapBottom[monthYear] = (dataMapBottom[monthYear] ?: 0.0) + Math.abs(transaction.amount)

                } catch (e: Exception) {
                    Log.e("CreateSpendAnalysis", "Date parsing error for chart bottom: ${e.message}")
                }
            }

            // Create line chart objects for each category
            val months = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
            val monthValuesBottom = months.map { 0.0 }.toMutableList()

            // Update the month values with the transaction amounts
            for ((date, amount) in dataMapBottom) {
                val month = date.substringBefore(" ")
                val index = months.indexOf(month)
                if (index != -1) {
                    monthValuesBottom[index] = amount
                }
            }

            // Create the entries for the line chart
            val entriesBottom = monthValuesBottom.mapIndexed { index, value -> Entry(index.toFloat(), value.toFloat()) }
            val categoryId = categoryTransaction.first().category
            val dataSetBottom = LineDataSet(entriesBottom, "CATEGORY $categoryId")
            dataSetBottom.color = getRandomColor()
            dataSetBottom.valueTextColor = Color.WHITE

            lineDataBottom.addDataSet(dataSetBottom)
        }

        lineChartBottom.data = lineDataBottom
        lineChartBottom.description.isEnabled = false
        lineChartBottom.invalidate() // refresh chart

        // Set the x-axis values
        val xAxisBottom = lineChartBottom.xAxis
        xAxisBottom.valueFormatter = IndexAxisValueFormatter(listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"))


    }
    private fun sortTransactionsForChartBottom(): List<List<Transaction>> {
        Log.d("CreateSpendAnalysis", "Sorting transactions for chart bottom...")
        try {
            val sortedTransactions = transactionList1.sortedBy { SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).parse(it.date) }
            Log.d("CreateSpendAnalysis", "Transactions sorted successfully for chart bottom.")
            Log.d("CreateSpendAnalysis", "Sorted transactions for chart bottom: $sortedTransactions")

            val categoryTransactions = sortedTransactions.groupBy { it.category }
                .map { entry -> entry.value }
            Log.d("CreateSpendAnalysis", "Transactions grouped by category for chart bottom: $categoryTransactions")
            return categoryTransactions
        } catch (e: ParseException) {
            Log.e("CreateSpendAnalysis", "Error parsing date for chart bottom: ${e.message}")
            return emptyList()
        }
    }
}