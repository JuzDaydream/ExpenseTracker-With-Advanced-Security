package com.example.expensetracker
//import com.google.firebase.database.core.view.View

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Environment
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
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
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
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.text.ParseException
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.absoluteValue


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
    private lateinit var btn_generate: AppCompatButton
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
        btn_generate = findViewById(R.id.btn_generate)



        // Disable button initially
        btn_compare.isEnabled = false
        btn_generate.visibility = View.GONE
        btn_generate.isEnabled = false
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
        val category: String = "", // Add this property
        var title :String= ""
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
        val spinnerAdapter = ArrayAdapter(this, R.layout.spinner_month, R.id.text1, yearsWithYear)
        spinnerAdapter.setDropDownViewResource(R.layout.spinner_month)


        // Set adapters to spinners
        spinner_year.adapter = spinnerAdapter
        spinner_year2.adapter = spinnerAdapter
        // Log the years that are being added to the spinner
        Log.d("SpinnerYears", "Years in spinner: $yearsWithYear")
    }




    fun calculateBounds(totalSpending: Double, numMonths: Int, variationPercentage: Double): Pair<Double, Double> {
        val monthlyEstimate = totalSpending / numMonths
        val upperBound = monthlyEstimate * (1 + variationPercentage / 100)
        val lowerBound = monthlyEstimate * (1 - variationPercentage / 100)
        return Pair(lowerBound, upperBound)
    }


    private fun identifyAndFlagSpendingIncreases(
        categoryYearSpending: HashMap<String, HashMap<Int, Double>>,
        selectedYear1: Int,
        selectedYear2: Int,
        tvRecom: TextView
    ) {
        Log.d("StartGivingAnalysis", "Calling identifyAndFlagSpendingIncreases with categoryYearSpending: $categoryYearSpending, selectedYear1: $selectedYear1, selectedYear2: $selectedYear2")
        val resultText = StringBuilder()
        val increasedCategories = mutableListOf<String>()
        val decreasedCategories = mutableListOf<String>()
        val unchangedCategories = mutableListOf<String>()
        val newlyIncludedCategories = mutableListOf<String>()
        val newlyIncludedCategories2 = mutableListOf<String>()
        val newlyIncludedCategories3 = mutableListOf<String>()
        val totalSpendingYear1 = categoryYearSpending.values.sumOf { yearSpendingMap ->
            (yearSpendingMap[selectedYear1] ?: 0.0).absoluteValue
        }
        val totalSpendingYear2 = categoryYearSpending.values.sumOf { yearSpendingMap ->
            (yearSpendingMap[selectedYear2] ?: 0.0).absoluteValue
        }
        val yearTransactions = transactionList.filter { transaction ->
            val transactionYear = SimpleDateFormat("yyyy", Locale.getDefault()).format(
                SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).parse(transaction.date)!!
            )
            transactionYear.toInt() == selectedYear1 || transactionYear.toInt() == selectedYear2
        }


        Log.d("totalamount", "$totalSpendingYear1 vs $totalSpendingYear2")

        resultText.append("**Spending Trends Analysis: Year $$selectedYear1 vs Year $$selectedYear2** \n\n\n")
        resultText.append("Year-over-Year Expenditure Growth Analysis\n\n")


        if (totalSpendingYear1 > totalSpendingYear2) {
            val percentage = ((totalSpendingYear1 - totalSpendingYear2) / totalSpendingYear2) * 100
            Log.d("TotalSpendingPercentage", "Total spending: ${String.format("%.2f", percentage)}%")
            resultText.append("A notable ${String.format("%.2f", percentage)}% increase in total expenditure has been observed from year $selectedYear2 to year $selectedYear1.\n")
        } else if (totalSpendingYear1 < totalSpendingYear2) {
            val percentage = ((totalSpendingYear1 - totalSpendingYear2).absoluteValue / totalSpendingYear2) * 100
            Log.d("TotalSpendingPercentage", "Total spending: ${String.format("%.2f", percentage)}%")
            resultText.append("A ${String.format("%.2f", percentage)}% decrease in total expenditure has been observed from year $selectedYear2 to year $selectedYear1.\n") }

        resultText.append("\nCategory-Specific Expenditure Trends \n\n")


        for ((category, yearSpendingMap) in categoryYearSpending) {
            val spendingYear1 = Math.abs(yearSpendingMap[selectedYear1] ?: 0.0)
            val spendingYear2 = Math.abs(yearSpendingMap[selectedYear2] ?: 0.0)
            Log.d(
                "SpendingComparison", "Category $category: $selectedYear1 = $spendingYear1, $selectedYear2 = $spendingYear2"
            )


            if (spendingYear2 == 0.0) {
                newlyIncludedCategories.add(category)
                if (spendingYear1 > 0.0) {
                    Log.d("SpendingIncrease", "Category $category has increased spending from $selectedYear2 to $selectedYear1")
                    increasedCategories.add(category)
                }
            } else if (spendingYear1 == 0.0) {
                newlyIncludedCategories2.add(category)
                Log.d("SpendingDecrease", "Category $category has decreased spending from year $selectedYear2 to year $selectedYear1")
                decreasedCategories.add(category)
            } else if (spendingYear1 > spendingYear2) {
                Log.d("SpendingIncrease", "Category $category has increased spending from year $selectedYear2 to year $selectedYear1")
                increasedCategories.add(category)
                val percentage = Math.abs(((spendingYear1 - spendingYear2) / spendingYear2) * 100)
                Log.d("SpendingPercentage", "Category $category: ${String.format("%.2f", percentage)}%")
                resultText.append(
                    "A notable ${String.format("%.2f", percentage)}% increase in expenditure has been observed within the $category category.\n")
            } else if (spendingYear1 < spendingYear2) {
                Log.d("SpendingDecrease", "Category $category has decreased spending from year $selectedYear2 to year $selectedYear1"
                )
                decreasedCategories.add(category)
                val percentage = ((spendingYear1 - spendingYear2) / spendingYear2) * 100
                Log.d("SpendingPercentage", "Category $category: ${String.format("%.2f", percentage)}%")
                resultText.append("A ${String.format("%.2f", Math.abs(percentage))}% decrease in expenditure has been observed within the $category category.\n")
            } else {
                newlyIncludedCategories3.add(category)
                Log.d("SpendingUnchanged", "Category $category has unchanged spending from year $selectedYear2 to year $selectedYear1")
                unchangedCategories.add(category) }
        }


        if (newlyIncludedCategories.isNotEmpty()) {
            resultText.append("\nCategory-Specific Expenditure Increases \n\n")
            resultText.append("Compared to year $selectedYear2, your expenditure has risen due to the inclusion of the ${
                newlyIncludedCategories.joinToString(" and ")} category.\n"
            )
        }
        if (newlyIncludedCategories2.isNotEmpty()) {
            resultText.append("\nCategory-Specific Expenditure Decreases \n\n")
            resultText.append("Compared to year $selectedYear2, your expenditure has diminished following the reduction in the ${newlyIncludedCategories2.joinToString(" and ")} category.\n")
        }
        if (newlyIncludedCategories3.isNotEmpty()) {
            resultText.append("\nCategories with Consistent Expenditure \n\n")
            resultText.append("Your expenditures in the ${newlyIncludedCategories3.joinToString(" and ")} category have remained consistent since $selectedYear2.\n")
        }


        resultText.append("\n**Summary**\n\n")


        if (increasedCategories.isNotEmpty()) {
            resultText.append("An upward trend in expenditure has been observed across the following categories: ${increasedCategories.joinToString(", ")}.\n")
        }
        if (decreasedCategories.isNotEmpty()) {
            resultText.append(
                "A reduction in expenditure has been noted in the following categories: ${decreasedCategories.joinToString(", ")}.\n")
        }
        if (unchangedCategories.isNotEmpty()) {
            resultText.append("Expenditures have remained consistent in the following categories: ${unchangedCategories.joinToString(", ")}.\n")
        }


        //  ADDING
        Log.d("CreateSpendAnalysis", "Calculating monthly totals...")

        try {
            val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())

            // Initialize maps to store the monthly totals for each year
            val monthlyTotalsYear1 = mutableMapOf<String, Double>()
            val monthlyTotalsYear2 = mutableMapOf<String, Double>()

            // Filter transactions for the selected years
            val filteredTransactions = yearTransactions.filter { transaction ->
                val transactionYear = SimpleDateFormat("yyyy", Locale.getDefault()).format(dateFormat.parse(transaction.date)!!)
                transactionYear.toInt() == selectedYear1 || transactionYear.toInt() == selectedYear2 }

            // Group filtered transactions by year and month
            val transactionsByYearAndMonth = filteredTransactions.groupBy { transaction ->
                val date = dateFormat.parse(transaction.date)!!
                val year = SimpleDateFormat("yyyy", Locale.getDefault()).format(date)
                val month = SimpleDateFormat("MM", Locale.getDefault()).format(date)
                Pair(year, month) // Pair of year and month
            }

            // Sum up the total amount for each month and separate by year
            transactionsByYearAndMonth.forEach { (yearMonth, transactions) ->
                val (year, month) = yearMonth
                val totalAmount = Math.abs(transactions.sumOf { it.amount })
                if (year.toInt() == selectedYear1) {
                    monthlyTotalsYear1[month] = monthlyTotalsYear1.getOrDefault(month, 0.0) + totalAmount
                } else if (year.toInt() == selectedYear2) {
                    monthlyTotalsYear2[month] = monthlyTotalsYear2.getOrDefault(month, 0.0) + totalAmount
                }
            }

            Log.d("MonthTotal", "Monthly totals for $selectedYear1: $monthlyTotalsYear1")
            Log.d("MonthTotal", "Monthly totals for $selectedYear2: $monthlyTotalsYear2")

            // Variable to track anomalies
            var anomalyCount = 0

            // Calculate anomalies for Year 1
            val (lowerBoundYear1, upperBoundYear1) = calculateBounds(totalSpendingYear1, 12, 20.0)
            monthlyTotalsYear1.forEach { (month, totalAmount) ->
                if (totalAmount < lowerBoundYear1) {
                    Log.d("AnomalyReport", "Year $selectedYear1, Month $month: Total amount $totalAmount is below lower bound $lowerBoundYear1")
                } else if (totalAmount > upperBoundYear1) {
                    Log.d("AnomalyReport", "Year $selectedYear1, Month $month: Total amount $totalAmount is above upper bound $upperBoundYear1\n")
                    anomalyCount++
                }
            }

            // Calculate anomalies for Year 2
            val (lowerBoundYear2, upperBoundYear2) = calculateBounds(totalSpendingYear2, 12, 20.0)
            monthlyTotalsYear2.forEach { (month, totalAmount) ->
                if (totalAmount < lowerBoundYear2) {
                    Log.d("AnomalyReport", "Year $selectedYear2, Month $month: Total amount $totalAmount is below lower bound $lowerBoundYear2\n")
                } else if (totalAmount > upperBoundYear2) {
                    Log.d("AnomalyReport", "Year $selectedYear2, Month $month: Total amount $totalAmount is above upper bound $upperBoundYear2\n")

                }
            }

            if (anomalyCount > 0) {
                resultText.append("\n*Anomaly Report*\n")
            }

            // 1. Filter transactions based on the selected year
            val filteredTransactions1 = yearTransactions.filter { transaction ->
                val transactionYear = SimpleDateFormat("yyyy", Locale.getDefault()).format(
                    dateFormat.parse(transaction.date)!!
                ).toInt()
                transactionYear == selectedYear1
            }

// Log the filtered transactions to verify the correct filtering by year
            Log.d("FilteredTransactions1", "Transactions for the selected years $selectedYear1 :")
            filteredTransactions1.forEach { transaction ->
                Log.d("FilteredTransactions1", "Date: ${transaction.date}, Amount: ${transaction.amount}, Title: ${transaction.title}")
            }
            // 1. Filter transactions based on the selected year
            val filteredTransactions2 = yearTransactions.filter { transaction ->
                val transactionYear = SimpleDateFormat("yyyy", Locale.getDefault()).format(
                    dateFormat.parse(transaction.date)!!
                ).toInt()
                transactionYear == selectedYear2
            }

// Log the filtered transactions to verify the correct filtering by year
            Log.d("FilteredTransactions2", "Transactions for the selected years $selectedYear2:")
            filteredTransactions2.forEach { transaction ->
                Log.d("FilteredTransactions2", "Date: ${transaction.date}, Amount: ${transaction.amount}, Title: ${transaction.title}")
            }


            // Compare the same month across different years
            monthlyTotalsYear1.forEach { (month, totalAmountYear1) ->
                val totalAmountYear2 = monthlyTotalsYear2[month] ?: 0.0
                val monthInt = month.toInt() // Convert month to integer
                val monthFormatter = DateTimeFormatter.ofPattern("MMM", Locale.ENGLISH)
                val monthDate = LocalDate.of(selectedYear1, monthInt, 1)
                val formattedMonth = monthDate.format(monthFormatter)


                if (totalAmountYear1 > upperBoundYear1 && totalAmountYear2 <= upperBoundYear2) {
                    val (topTransaction, percentage)= findPeakMonthTransactions(month, yearTransactions)
                    val categorytopID =  topTransaction?.category
                    val categorytopName = categoriesMap[categorytopID] ?: "Unknown" // Get category name
                    if (topTransaction != null) {
                        val result = "\nIdentified a one-time high spending event in $selectedYear1 that did not occur in $selectedYear2 : \nOn ${topTransaction.date}, a ${topTransaction.title} expense of RM${Math.abs(topTransaction.amount)} accounted for ${"%.2f".format(percentage)}% to the total monthly amount.\n\n\nRecommendation: \nConsider setting aside a contingency fund for unexpected high expenses, based on this one-time event, to better manage your budget and avoid any financial strain.\n\n"
                        resultText.append(result)
                    }
                } else if (totalAmountYear1 > upperBoundYear1 && totalAmountYear2 > upperBoundYear2) {
                    val (topTransactionYear1, percentageYear1) = findPeakMonthTransactions(month, filteredTransactions1)
                    val (topTransactionYear2, percentageYear2) = findPeakMonthTransactions(month, filteredTransactions2)

                    if (topTransactionYear1 != null && topTransactionYear2 != null) {
                        val categoryIDY1 =  topTransactionYear1.category
                        val categoryIDY2 =  topTransactionYear2.category
                        val categoryNameY1 = categoriesMap[categoryIDY1] ?: "Unknown" // Get category name
                        val categoryNameY2 = categoriesMap[categoryIDY2] ?: "Unknown" // Get category name
                        val sameCategory = topTransactionYear1.category == topTransactionYear2.category
                        val result = if (sameCategory) {
                            "\nBoth years showed high spending in the $categoryNameY1 category during $formattedMonth: \n \n> In $selectedYear1, a ${topTransactionYear1.title} expense occured on ${topTransactionYear1.date}, contributing ${"%.2f".format(percentageYear1)}% to the total monthly expenditure.\n \n> In $selectedYear2, a ${topTransactionYear2.title} expense occured on ${topTransactionYear2.date}, contributing ${"%.2f".format(percentageYear2)}% to the total monthly expenditure.\n\n\n" +
                                    "Recommendation: \nSince this is a repeating spending trend for the $categoryNameY1 category, consider reviewing and adjusting your budget for this category in the long term.\n"

                        } else {
                            "\nHigh spending occurred in $formattedMonth across both years, but in different categories: \n\n> In $selectedYear1, a ${topTransactionYear1.title} expense in the $categoryNameY1 category on ${topTransactionYear1.date} contribute ${"%.2f".format(percentageYear1)}% to the total monthly expenditure. \n\n> In $selectedYear2, a ${topTransactionYear2.title} expense in the $categoryNameY2 category on ${topTransactionYear2.date} contribute  ${"%.2f".format(percentageYear2)}% to the total monthly expenditure.\n"+
                                    "\n\nAdvice:\nGiven the recurring high spending in $formattedMonth, it's important to recognize this pattern and prepare in advance. Consider reviewing your spending habits during this month and allocate extra funds to your budget to handle potential increases in expenses.\n"+"\nRecommendation:\nAdd a buffer to your budget for $formattedMonth to cover unexpected expenses and prevent overspending."
                        }
                        resultText.append(result)
                    }

                }
            }


            // Update the TextView with the result
            tvRecom.text= resultText.toString()

            if (!resultText.isEmpty()) {
                btn_generate.visibility = View.VISIBLE
                btn_generate.isEnabled = true
                btn_generate.setOnClickListener {
                    println("button clicked")
                    Log.d("GenerateButton", "Button clicked, generating report...")

                    // Create a new file
                    val file = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "report.txt")
                    Log.d("GenerateButton", "File path: ${file.absolutePath}")

                    // Check if the file already exists
                    if (file.exists()) {
                        Log.d("GenerateButton", "File already exists, appending to it...")
                    } else {
                        Log.d("GenerateButton", "File does not exist, creating a new one...")
                    }

                    try {
                        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), 123)
                        } else {
                            // Write to the file
                            // Write the resultText to the file
                            val writer = FileWriter(file)
                            writer.write(resultText.toString())
                            writer.close()
                            Log.d("GenerateButton", "Wrote resultText to file: ${resultText.toString()}")
                        }


                        // Open the file using an Intent
                        val intent = Intent(Intent.ACTION_VIEW)
                        intent.setDataAndType(Uri.fromFile(file), "text/plain")
                        startActivity(intent)

                        Log.d("GenerateButton", "Report generation complete!")
                    } catch (e: IOException) {
                        Log.e("GenerateButton", "Error writing to file: ${e.message}")
                    } catch (e: Exception) {
                        Log.e("GenerateButton", "Error generating report: ${e.message}")
                    }

                }
            } //endif for resulttext.empty

        } catch (e: ParseException) {
            Log.e("CreateMonthTotal", "Error parsing date: ${e.message}")

        }
    }

    private fun findPeakMonthTransactions(
        peakMonth: String,
        yearTransactions: List<Transaction>
    ): Pair<Transaction?, Double> {
        // 1. specific month all transactions
        val transactionsInPeakMonth = yearTransactions.filter { transaction ->
            val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
            val transactionDate = dateFormat.parse(transaction.date)!!
            val month = SimpleDateFormat("MM", Locale.getDefault()).format(transactionDate)
            month == peakMonth
        }
        // Log the filtered transactions to see which ones are for the specified month
        Log.d("FilteredTransactions", "Transactions for month $peakMonth:")
        transactionsInPeakMonth.forEach { transaction ->
            Log.d("FilteredTransactions", "Date: ${transaction.date}, Amount: ${transaction.amount}, Title: ${transaction.title}")
        }


        // 2. sort transactions from big to small
        val sortedTransactions = transactionsInPeakMonth.sortedBy { it.amount }
        // Log the sorted transactions to see the order by amount
        Log.d("SortedTransactions", "Transactions sorted by amount for month $peakMonth:")
        sortedTransactions.forEach { transaction ->
            Log.d("SortedTransactions", "Date: ${transaction.date}, Amount: ${transaction.amount}, Title: ${transaction.title}")
        }

        // 3. Get the top transaction (if available)
        val topTransaction = sortedTransactions.firstOrNull()

        // 4. Calculate the total amount for the month
        val totalAmountForMonth = sortedTransactions.sumOf { Math.abs(it.amount)}
        Log.d("TotalAmount", "Total Amount for month $peakMonth: $totalAmountForMonth")
        // 5. Calculate the percentage if the top transaction exists
        val percentage = if (topTransaction != null && totalAmountForMonth > 0) {
            val topTransactionAmount = Math.abs(topTransaction.amount)
            (topTransactionAmount / totalAmountForMonth) * 100
        } else {
            0.0
        }
        Log.d("PercentageCalculation", "Top Transaction Amount: ${topTransaction?.amount ?: "N/A"}")
        Log.d("PercentageCalculation", "Percentage Contribution: ${"%.2f".format(percentage)}%")
        // 6. Output or record the top transaction and its percentage contribution
        if (topTransaction != null) {
            Log.d("TopTransaction", "Top transaction for peak month $peakMonth:")
            Log.d("TopTransaction", "Date: ${topTransaction.date}, Amount: ${topTransaction.amount}, Description: ${topTransaction.title}")
            Log.d("TopTransaction", "This transaction contributes ${"%.2f".format(percentage)}% to the total amount of the month.")
        } else {
            Log.d("TopTransaction", "No transactions found for the peak month $peakMonth.")
        }

        return Pair(topTransaction, percentage)
    }


}
