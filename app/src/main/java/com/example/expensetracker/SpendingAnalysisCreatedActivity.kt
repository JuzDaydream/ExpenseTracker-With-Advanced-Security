package com.example.expensetracker
//import com.google.firebase.database.core.view.View
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale

class SpendingAnalysisCreatedActivity : AppCompatActivity() {
    private lateinit var databaseReference: DatabaseReference
    private lateinit var databaseReference1: DatabaseReference
    private lateinit var userID: String
    private lateinit var tvAmount: TextView
    private lateinit var tvRecom: TextView
    private lateinit var barChartTop: BarChart
    private lateinit var barChartBottom: BarChart
    private val transactionList = ArrayList<Transaction>()
    private val transactionList1 = ArrayList<Transaction>()
    private lateinit var spinner_year: Spinner
    private lateinit var spinner_year2: Spinner
    private lateinit var btn_compare: AppCompatButton
    private val categoriesMap = mutableMapOf<String, String>() // Maps categoryId to categoryName
    private var previousYearSelection = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_spending_analysis_created)

        userID = intent.getStringExtra("userID") ?: return
        Log.d("SpendingAnalysisActivity", "Received userId: $userID")
        tvRecom= findViewById(R.id.tv_recom)
        tvAmount = findViewById(R.id.tv_amount)
        barChartTop = findViewById(R.id.barChartTop)
        barChartBottom = findViewById(R.id.chart_bottom)
        spinner_year = findViewById(R.id.spinner_year)
        spinner_year2 = findViewById(R.id.spinner_year2)
        btn_compare = findViewById(R.id.btn_compare)

        // Disable button initially
        btn_compare.isEnabled = false

        val iconBack: ImageView = findViewById(R.id.icon_back)
        iconBack.setOnClickListener {
            finish()
        }

        databaseReference = FirebaseDatabase.getInstance("https://expensetracker-a260c-default-rtdb.asia-southeast1.firebasedatabase.app")
            .getReference("User").child(userID).child("transactionList")
        databaseReference1 = FirebaseDatabase.getInstance("https://expensetracker-a260c-default-rtdb.asia-southeast1.firebasedatabase.app")
            .getReference("User").child(userID).child("transactionList")
        fetchCategories()
        fetchAllTransactions()
        fetchAllTransactions1()

        // Set up item selected listeners
        spinner_year.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View, position: Int, id: Long) {
                val selectedYear1 = spinner_year.selectedItem.toString()
                val selectedYear2 = spinner_year2.selectedItem?.toString() ?: ""

                if (selectedYear1 == selectedYear2) {
                    Toast.makeText(this@SpendingAnalysisCreatedActivity, "Please select a different year", Toast.LENGTH_SHORT).show()
                    spinner_year.setSelection(0) // Reset the spinner to the first item
                    btn_compare.isEnabled = false
                }
                else{
                    btn_compare.isEnabled = true


                }
            }

            override fun onNothingSelected(parent: AdapterView<*>) {}

        }

        spinner_year2.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View, position: Int, id: Long) {
                val selectedYear1 = spinner_year.selectedItem?.toString() ?: ""
                val selectedYear2 = spinner_year2.selectedItem.toString()

                if (selectedYear1 == selectedYear2) {
                    Toast.makeText(this@SpendingAnalysisCreatedActivity, "Please select a different year", Toast.LENGTH_SHORT).show()
                    spinner_year2.setSelection(0) // Reset the spinner to the first item
                    btn_compare.isEnabled = false
                }
                else{
                    btn_compare.isEnabled = true


                }
            }

            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

        btn_compare.setOnClickListener {
            val selectedYear1 = spinner_year.selectedItem?.toString()?.replace("YEAR ", "")?.toIntOrNull()
            val selectedYear2 = spinner_year2.selectedItem?.toString()?.replace("YEAR ", "")?.toIntOrNull()
            Toast.makeText(this, "Compare button clicked", Toast.LENGTH_SHORT).show()
            if (selectedYear1 == null || selectedYear2 == null || selectedYear1 == selectedYear2) {
                Toast.makeText(this, "Please select two different valid years for comparison.", Toast.LENGTH_SHORT).show()
            } else if (selectedYear1 <= selectedYear2) {
                Toast.makeText(this, "Year 1 should be greater than Year 2 for comparison.", Toast.LENGTH_SHORT).show()
            }
            else {
                val categoryYearSpending = sortTransactionsForChartBottom()

                Log.d("GivingAnalysis", "Calling identifyAndFlagSpendingIncreases with categoryYearSpending: $categoryYearSpending, selectedYear1: $selectedYear1, selectedYear2: $selectedYear2")

                identifyAndFlagSpendingIncreases(categoryYearSpending, selectedYear1, selectedYear2,tvRecom)

            }
        }

    }

    private fun fetchCategories() {
        val categoriesRef = FirebaseDatabase.getInstance("https://expensetracker-a260c-default-rtdb.asia-southeast1.firebasedatabase.app").getReference("Category")
        categoriesRef.get().addOnSuccessListener { snapshot ->
            categoriesMap.clear()
            val categoryMap = snapshot.children.associate {
                it.key!! to it.child("name").getValue(String::class.java)!!
            }

            // Store categories in memory for later use
            categoriesMap.putAll(categoryMap)

            // Log the contents of categoriesMap after adding
            Log.d("CategoryMap", "Updated Categories Map: $categoriesMap")

        }.addOnFailureListener { exception ->
            Log.e("DEBUGTEST", "Error fetching categories: ${exception.message}")
        }
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
                    Log.d("TransactionListSize", "Transaction list size: ${transactionList.size}") // Add this log message
                    populateSpinners()

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

    private fun getRandomColor(): Int {
        val random = java.util.Random()
        return Color.rgb(random.nextInt(256), random.nextInt(256), random.nextInt(256))
    }



    private fun updateUI() {
        // Group transactions by year and calculate total expenses for each year
        val yearTransactions = sortTransactions()
        val yearTotals = yearTransactions.map { yearList ->
            val totalAmount = yearList.sumOf { it.amount }
            val year = SimpleDateFormat("yyyy", Locale.getDefault()).format(SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).parse(yearList.first().date)!!)
            year to totalAmount
        }.toMap()

        // Calculate the total amount of all transactions
        val totalAmountSum = yearTotals.values.sumOf { Math.abs(it) }

        // Format total amount as RM00.00
        val formattedTotalAmount = String.format("RM%.2f", totalAmountSum.toFloat())
        tvAmount.text = formattedTotalAmount

        // Prepare data for BarChart
        val barEntries = mutableListOf<BarEntry>()
        val xAxisLabels = mutableListOf<String>()

        // Generate random colors for each bar
        val colors = MutableList(yearTotals.size) { getRandomColor() }

        yearTotals.entries.forEachIndexed { index, entry ->
            barEntries.add(BarEntry(index.toFloat(), Math.abs(entry.value.toFloat())))
            xAxisLabels.add(entry.key)
        }

        // Check if barEntries and xAxisLabels have data
        Log.d("CreateSpendAnalysis", "Bar Entries: $barEntries")
        Log.d("CreateSpendAnalysis", "XAxis Labels: $xAxisLabels")

        // Create a BarDataSet with the data and colors
        val barDataSet = BarDataSet(barEntries, "Yearly Expenses").apply {
            this.colors = colors // Apply random colors
            valueTextSize = 12f // Optionally, adjust text size for value labels
            valueTextColor = Color.WHITE // Set value text color to white
        }

        // Create BarData and set it to the chart
        val barData = BarData(barDataSet)
        barData.barWidth = 0.4f // Set bar width

        // Update BarChart
        setupBarChart(barChartTop)
        barChartTop.data = barData
        barChartTop.xAxis.apply {
            valueFormatter = IndexAxisValueFormatter(xAxisLabels)
            textColor = Color.WHITE // Set X-axis label color to white
        }
        barChartTop.axisLeft.apply {
            textColor = Color.WHITE // Set Y-axis label color to white
        }
        barChartTop.legend.apply {
            textColor = Color.WHITE // Set legend text color to white
        }
        barChartTop.invalidate() // Refresh the chart
    }

    private fun setupBarChart(barChart: BarChart) {
        barChart.xAxis.apply {
            position = XAxis.XAxisPosition.BOTTOM
            granularity = 1f
            axisMinimum = 0f
            setDrawGridLines(false)
            setDrawLabels(true)
            textColor = Color.WHITE // Ensure X-axis label color
        }
        barChart.axisLeft.apply {
            setDrawLabels(true)
            setDrawAxisLine(true)
            setDrawGridLines(true)
            textColor = Color.WHITE // Ensure Y-axis label color
        }
        barChart.axisRight.isEnabled = false
        barChart.legend.apply {
            isEnabled = true
            textSize = 12f
            formSize = 12f
            textColor = Color.WHITE // Set legend text color
        }
        barChart.description.isEnabled = false
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
                    Log.d("CreateSpendAnalysis1", "No transactions found")
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("CreateSpendAnalysis1", "Database error: ${error.message}")
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
                        Log.d("CreateSpendAnalysis1", "Added transaction: ID=${transaction.id}, Amount=${transaction.amount}, Date=${transaction.date}, Category ID=${transaction.category}")
                    }

                    val categoryYearSpending = sortTransactionsForChartBottom()
                    updateUIForChartBottom(categoryYearSpending)

                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("CreateSpendAnalysis1", "Database error: ${error.message}")
            }
        })
    }


    private fun sortTransactionsForChartBottom(): HashMap<String, HashMap<Int, Double>> {
        Log.d("CreateSpendAnalysis1", "Sorting transactions for chart bottom...")

        try {
            // Parse and sort transactions by date
            val sortedTransactions = transactionList1.sortedBy {
                SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).parse(it.date)
            }

            Log.d("CreateSpendAnalysis1", "Transactions sorted successfully for chart bottom.")

            // Group transactions by category and year
            val categoryYearSpending = HashMap<String, HashMap<Int, Double>>()

            for (transaction in sortedTransactions) {
                // Extract the year from the transaction date
                val year = SimpleDateFormat("yyyy", Locale.getDefault()).format(
                    SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).parse(transaction.date)
                ).toInt()

                val categoryId = transaction.category
                val categoryName = categoriesMap[categoryId] ?: "Unknown" // Get category name

                // Initialize the category map if it doesn't exist
                if (!categoryYearSpending.containsKey(categoryName)) {
                    categoryYearSpending[categoryName] = HashMap()
                }

                // Initialize the year total if it doesn't exist
                val yearSpending = categoryYearSpending[categoryName]
                if (!yearSpending!!.containsKey(year)) {
                    yearSpending[year] = 0.0
                }

                // Add the transaction amount to the total for that year
                yearSpending[year] = yearSpending[year]!! + transaction.amount
            }

            Log.d("CreateSpendAnalysis1", "Transactions grouped by category and year for chart bottom: $categoryYearSpending")

            return categoryYearSpending

        } catch (e: ParseException) {
            Log.e("CreateSpendAnalysis1", "Error parsing date for chart bottom: ${e.message}")
            return HashMap()
        }
    }



    private fun updateUIForChartBottom(categoryYearSpending: HashMap<String, HashMap<Int, Double>>) {
        val barEntries = ArrayList<BarEntry>()
        val categories = ArrayList<String>()
        var index = 0

        // Map to hold the color associated with each category
        val categoryColors = mutableMapOf<String, Int>()

        // List of colors to assign to different categories
        val colors = listOf(
            Color.RED, Color.BLUE, Color.GREEN, Color.YELLOW, Color.CYAN, Color.MAGENTA
        )
        var colorIndex = 0

        for ((categoryId, yearSpendingMap) in categoryYearSpending) {
            // Convert categoryId to categoryName
            val categoryName = categoriesMap[categoryId] ?: categoryId // Default to ID if not found
            // Assign a color to the category if not already assigned
            if (!categoryColors.containsKey(categoryName)) {
                categoryColors[categoryName] = colors[colorIndex % colors.size]
                colorIndex++
            }

            for ((year, totalSpending) in yearSpendingMap) {
                // Use absolute value for total spending
                barEntries.add(BarEntry(index.toFloat(), Math.abs(totalSpending.toFloat())))
                categories.add("$categoryName:$year") // Use category name
                index++
            }
        }

        val barDataSet = BarDataSet(barEntries, "Spending by Category and Year").apply {
            valueTextColor = Color.WHITE // Set bar value text color to white
        }

        // Apply colors based on the category of each entry
        val entryColors = barEntries.map { entry ->
            val categoryYear = categories[entry.x.toInt()]
            val category = categoryYear.split(":")[0] // Extract the category from "Category:Year"
            categoryColors[category] ?: Color.BLACK // Fallback to black if color is not found
        }
        barDataSet.colors = entryColors

        val barData = BarData(barDataSet)
        barData.setValueTextColor(Color.WHITE) // Set value text color to white

        barChartBottom.data = barData
        barChartBottom.xAxis.valueFormatter = IndexAxisValueFormatter(categories)
        barChartBottom.xAxis.textColor = Color.WHITE // Set X-axis text color to white
        barChartBottom.axisLeft.textColor = Color.WHITE // Set Y-axis left text color to white
        barChartBottom.axisRight.textColor = Color.WHITE // Set Y-axis right text color to white

        // Set legend text color and size
        barChartBottom.legend.textColor = Color.WHITE
        barChartBottom.legend.textSize = 10f // Set legend text size to match X-axis labels

        // Adjust the X-axis label display
        barChartBottom.xAxis.textSize = 10f // Adjust text size
        barChartBottom.xAxis.setLabelRotationAngle(30f) // Moderate label rotation
        barChartBottom.xAxis.setGranularity(1f) // Ensure every label is shown
        barChartBottom.xAxis.setLabelCount(categories.size, false) // Set label count based on categories size


        barChartBottom.invalidate() // Refresh the chart
        // identifyAndFlagSpendingIncreases(categoryYearSpending) // Add this line to flag increases
    }

    private fun populateSpinners() {
        val years = mutableListOf<String>()

        // Get unique years from transactionList
        transactionList.forEach { transaction ->
            val year = SimpleDateFormat("yyyy", Locale.getDefault()).format(SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).parse(transaction.date)!!)
            if (!years.contains(year)) {
                years.add(year)
            }
        }

        // Sort years in ascending order
        years.sort()

        // Add "YEAR" before each year
        val yearsWithYear = years.map { "YEAR $it" }

        // Create adapters for spinners
        val spinnerAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, yearsWithYear)
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)

        // Set adapters to spinners
        spinner_year.adapter = spinnerAdapter
        spinner_year2.adapter = spinnerAdapter
        // Log the years that are being added to the spinner
        Log.d("SpinnerYears", "Years in spinner: $yearsWithYear")
    }


    private fun identifyAndFlagSpendingIncreases(
        categoryYearSpending: HashMap<String, HashMap<Int, Double>>,
        selectedYear1: Int,
        selectedYear2: Int,
        tvRecom: TextView // Add a parameter to pass the TextView
    ) {
        Log.d("StartGivingAnalysis", "Calling identifyAndFlagSpendingIncreases with categoryYearSpending: $categoryYearSpending, selectedYear1: $selectedYear1, selectedYear2: $selectedYear2")
        val resultText = StringBuilder() // Use StringBuilder to build the result string

        for ((category, yearSpendingMap) in categoryYearSpending) {
            val spendingYear1 = yearSpendingMap[selectedYear1] ?: 0.0
            val spendingYear2 = yearSpendingMap[selectedYear2] ?: 0.0

            if (spendingYear2 > spendingYear1) {
                Log.d("SpendingIncrease", "Category $category has increased spending from $selectedYear1 to $selectedYear2")
                resultText.append("Increased spending in $category when comparing $selectedYear1 with $selectedYear2\n")
            } else if (spendingYear2 < spendingYear1) {
                Log.d("SpendingDecrease", "Category $category has decreased spending from $selectedYear1 to $selectedYear2")
                resultText.append("Decreased spending in $category when comparing $selectedYear1 with $selectedYear2\n")
            } else {
                Log.d("SpendingUnchanged", "Category $category has unchanged spending from $selectedYear1 to $selectedYear2")
                resultText.append("Unchanged spending in $category when comparing $selectedYear1 with $selectedYear2\n")
            }
        }

        // Update the TextView with the result
        tvRecom.text = resultText.toString()
    }


}