package com.example.expensetracker.ui

import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.expensetracker.MainActivity
import com.example.expensetracker.R
import com.example.expensetracker.data.Category
import com.example.expensetracker.data.Transaction
import com.example.expensetracker.dataAdapter.StatAdapter
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.github.mikephil.charting.formatter.ValueFormatter
import com.github.mikephil.charting.utils.ColorTemplate
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class YearlyStatFragment : Fragment() {

    private lateinit var statAdapter: StatAdapter
    private lateinit var recyclerView: RecyclerView
    private lateinit var tvTotalAmount: TextView
    private lateinit var spinner: Spinner
    private lateinit var chart: PieChart
    private lateinit var databaseReference: DatabaseReference
    private lateinit var userID: String
    private var categoryMap: MutableMap<String, String> = mutableMapOf()
    private var selectedYear: String = SimpleDateFormat("yyyy", Locale.getDefault()).format(Date())
    private var allTransactions: ArrayList<Transaction> = ArrayList()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_yearly_stat, container, false)

        // Initialize the RecyclerView and other UI elements
        recyclerView = view.findViewById(R.id.recycler_stat)
        tvTotalAmount = view.findViewById(R.id.tv_amount)
        spinner = view.findViewById(R.id.spinner_year)
        chart = view.findViewById(R.id.chart)
        statAdapter = StatAdapter(ArrayList(), categoryMap)
        recyclerView.adapter = statAdapter
        recyclerView.layoutManager = LinearLayoutManager(requireContext())

        // Obtain userID from arguments or any other method
        userID = (requireActivity() as MainActivity).getUserID()

        databaseReference = FirebaseDatabase.getInstance("https://expensetracker-a260c-default-rtdb.asia-southeast1.firebasedatabase.app")
            .getReference("User").child(userID).child("transactionList")

        setupSpinner()
        fetchCategories()

        return view
    }

    private fun fetchTransactions() {
        val transRef = FirebaseDatabase.getInstance("https://expensetracker-a260c-default-rtdb.asia-southeast1.firebasedatabase.app").getReference("Transaction")
        databaseReference.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                allTransactions.clear()
                val yearSet = mutableSetOf<String>()
                var completedListeners = 0

                if (snapshot.exists()) {
                    for (transactionSnapshot in snapshot.children) {
                        val transactionId = transactionSnapshot.value.toString()
                        transRef.child(transactionId).addListenerForSingleValueEvent(object : ValueEventListener {
                            override fun onDataChange(transactionSnapshot: DataSnapshot) {
                                if (transactionSnapshot.exists()) {
                                    val transaction = transactionSnapshot.getValue(Transaction::class.java)
                                    if (transaction != null) {
                                        val date = parseDate(transaction.date)
                                        val year = SimpleDateFormat("yyyy", Locale.getDefault()).format(date)
                                        yearSet.add(year)
                                        allTransactions.add(transaction)
                                    }
                                }
                                completedListeners++
                                if (completedListeners == snapshot.childrenCount.toInt()) {
                                    val yearList = yearSet.toList().sortedDescending()
                                    updateSpinnerAndUI(yearList)
                                }
                            }

                            override fun onCancelled(error: DatabaseError) {
                                Log.e("YearlyStatFragment", "Database error: ${error.message}")
                            }
                        })
                    }
                } else {
                    handleNoTransactions()
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("YearlyStatFragment", "Database error: ${error.message}")
            }
        })
    }

    private fun fetchCategories() {
        val categoriesRef = FirebaseDatabase.getInstance("https://expensetracker-a260c-default-rtdb.asia-southeast1.firebasedatabase.app")
            .getReference("Category")

        categoriesRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                categoryMap.clear()
                if (snapshot.exists()) {
                    for (categorySnapshot in snapshot.children) {
                        val category = categorySnapshot.getValue(Category::class.java)
                        category?.let {
                            categoryMap[category.id] = category.name
                        }
                    }
                }
                fetchTransactions()
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("YearlyStatFragment", "Database error: ${error.message}")
            }
        })
    }

    private fun updateSpinnerAndUI(yearList: List<String>) {
        if (!isAdded) return

        if (yearList.isNotEmpty()) {
            Log.d("YearlyStatFragment", "Year list: $yearList")

            val spinnerAdapter = ArrayAdapter(requireContext(), R.layout.spinner_month, R.id.text1, yearList)
            spinnerAdapter.setDropDownViewResource(R.layout.spinner_month)
            spinner.adapter = spinnerAdapter

            val currentYear = SimpleDateFormat("yyyy", Locale.getDefault()).format(Date())
            val defaultSelection = yearList.indexOf(currentYear)

            Log.d("YearlyStatFragment", "Default year selection: $currentYear (index: $defaultSelection)")

            spinner.setSelection(if (defaultSelection != -1) defaultSelection else 0)

            // Log selected year
            Log.d("YearlyStatFragment", "Spinner selected year: ${spinner.selectedItem}")

            filterTransactionsByYear()
        } else {
            handleNoTransactions()
        }
    }

    private fun filterTransactionsByYear() {
        Log.d("YearlyStatFragment", "Filtering transactions for year: $selectedYear")

        val filteredTransactions = allTransactions.filter { transaction ->
            val isInYear = isInSelectedYear(transaction.date)
            Log.d("YearlyStatFragment", "Transaction: ${transaction.date}, In selected year: $isInYear")
            isInYear
        }

        val sortedTransactions = filteredTransactions.sortedByDescending { it.amount }

        Log.d("YearlyStatFragment", "Filtered transactions count: ${sortedTransactions.size}")

        val totalExpense = sortedTransactions.filter { it.amount < 0 }.sumOf { it.amount }
        val formattedAmount = if (totalExpense == 0.0) {
            "RM0.00"
        } else {
            String.format("RM%.2f", -totalExpense)
        }
        tvTotalAmount.text = formattedAmount

        statAdapter.updateList(sortedTransactions)
        val groupedTransactions = statAdapter.getGroupedTransactions()
        updatePieChart(groupedTransactions, categoryMap)
    }

    private fun isInSelectedYear(date: String): Boolean {
        return try {
            val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
            val transactionDate = dateFormat.parse(date)
            val transactionYear = SimpleDateFormat("yyyy", Locale.getDefault()).format(transactionDate)
            transactionYear == selectedYear
        } catch (e: Exception) {
            false
        }
    }

    private fun handleNoTransactions() {
        val currentYear = SimpleDateFormat("yyyy", Locale.getDefault()).format(Date())
        val yearList = listOf(currentYear)

        val updatedSpinnerAdapter = ArrayAdapter(requireContext(), R.layout.spinner_month, R.id.text1, yearList)
        updatedSpinnerAdapter.setDropDownViewResource(R.layout.spinner_month)
        tvTotalAmount.text = "RM0.00"
        statAdapter.updateList(emptyList())
        updatePieChart(emptyList(), categoryMap)
    }

    private fun setupSpinner() {
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                selectedYear = parent.getItemAtPosition(position).toString()
                filterTransactionsByYear()
            }

            override fun onNothingSelected(parent: AdapterView<*>) {
                // Handle the case when no item is selected
            }
        }
    }

    private fun parseDate(date: String): Date {
        return try {
            val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
            dateFormat.parse(date) ?: Date()
        } catch (e: Exception) {
            Log.e("YearlyStatFragment", "Failed to parse date: $date", e)
            Date() // Return a default date or handle the error appropriately
        }
    }

    private fun updatePieChart(groupedTransactions: List<Pair<String, Double>>, categoryMap: Map<String, String>) {
        val pieChart = view?.findViewById<PieChart>(R.id.chart) ?: return

        // Prepare data for the pie chart
        val entries = ArrayList<PieEntry>()
        for ((category, totalAmount) in groupedTransactions) {
            val categoryName = categoryMap[category] ?: "Unknown"
            entries.add(PieEntry(Math.abs(totalAmount).toFloat(), categoryName))
        }

        // Create a dataset and set its properties
        val dataSet = PieDataSet(entries, "Category")

        dataSet.setColors(ColorTemplate.JOYFUL_COLORS, 255)
        dataSet.valueTextColor = Color.parseColor("#FCFCFD")
        dataSet.valueTextSize = 14f
        dataSet.valueFormatter = object : ValueFormatter() {
            override fun getFormattedValue(value: Float): String {
                return String.format("RM%.2f", value)  // Ensure two decimal places
            }
        }
            // Create PieData and set it to the chart
        val pieData = PieData(dataSet)
        pieChart.data = pieData

        // Customize pie chart appearance
        pieChart.description.isEnabled = false

        pieChart.setBackgroundColor(Color.parseColor("#222222"))
//            pieChart.legend.position = com.github.mikephil.charting.components.Legend.LegendPosition.RIGHT_OF_CHART
        pieChart.legend.orientation =
            com.github.mikephil.charting.components.Legend.LegendOrientation.VERTICAL
        pieChart.legend.textColor = Color.parseColor("#FCFCFD")
        pieChart.legend.textSize = (14f)
        pieChart.setDrawHoleEnabled(true)
        pieChart.holeRadius = 55f
        pieChart.setHoleColor(Color.TRANSPARENT)
        pieChart.setEntryLabelColor(Color.parseColor("#FCFCFD"))
        pieChart.setEntryLabelTextSize(14f) // Set text size for labels


        // Refresh chart
        pieChart.invalidate()
    }
}