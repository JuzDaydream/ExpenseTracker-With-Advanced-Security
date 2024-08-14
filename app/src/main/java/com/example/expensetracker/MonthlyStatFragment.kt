package com.example.expensetracker.ui

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
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MonthlyStatFragment : Fragment() {

    private lateinit var statAdapter: StatAdapter
    private lateinit var recyclerView: RecyclerView
    private lateinit var tvTotalAmount: TextView
    private lateinit var spinner: Spinner
    private lateinit var databaseReference: DatabaseReference
    private lateinit var userID: String // Add userID
    private var categoryMap: MutableMap<String, String> = mutableMapOf()
    private var selectedMonth: String = SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(Date())
    private var allTransactions: ArrayList<Transaction> = ArrayList()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_monthly_stat, container, false)

        // Initialize the RecyclerView and other UI elements
        recyclerView = view.findViewById(R.id.recycler_stat)
        tvTotalAmount = view.findViewById(R.id.tv_amount)
        spinner = view.findViewById(R.id.spinner_month_year)

        statAdapter = StatAdapter(ArrayList(), categoryMap)
        recyclerView.adapter = statAdapter
        recyclerView.layoutManager = LinearLayoutManager(requireContext())

        // Obtain userID from arguments or any other method
        userID = (requireActivity() as MainActivity).getUserID()

        databaseReference = FirebaseDatabase.getInstance("https://expensetracker-a260c-default-rtdb.asia-southeast1.firebasedatabase.app").getReference("User").child(userID).child("transactionList")

        setupSpinner()
        fetchCategories()

        return view
    }

    private fun fetchTransactions() {
        val transRef = FirebaseDatabase.getInstance("https://expensetracker-a260c-default-rtdb.asia-southeast1.firebasedatabase.app").getReference("Transaction")
        databaseReference.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                allTransactions.clear()
                val monthYearSet = mutableSetOf<String>()
                var completedListeners = 0

                if (snapshot.exists()) {
                    for (transactionSnapshot in snapshot.children) {
                        val transactionId = transactionSnapshot.value.toString()
                        transRef.child(transactionId).addListenerForSingleValueEvent(object : ValueEventListener {
                            override fun onDataChange(transactionSnapshot: DataSnapshot) {
                                if (transactionSnapshot.exists()) {
                                    try {
                                        val transaction = transactionSnapshot.getValue(Transaction::class.java)
                                        if (transaction != null) {
                                            val date = parseDate(transaction.date)
                                            val monthYear = SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(date)
                                            monthYearSet.add(monthYear)
                                            allTransactions.add(transaction)
                                        } else {
                                            Log.w("MonthlyStatFragment", "Transaction data for ID $transactionId is null.")
                                        }
                                    } catch (e: Exception) {
                                        Log.e("MonthlyStatFragment", "Error parsing transaction $transactionId: ${e.message}")
                                    }
                                } else {
                                    Log.w("MonthlyStatFragment", "Transaction ID $transactionId not found in database.")
                                }
                                completedListeners++
                                if (completedListeners == snapshot.childrenCount.toInt()) {
                                    val monthYearList = monthYearSet.toList().sortedDescending()
                                    updateSpinnerAndUI(monthYearList)
                                }
                            }

                            override fun onCancelled(error: DatabaseError) {
                                Log.e("MonthlyStatFragment", "Database error: ${error.message}")
                            }
                        })
                    }
                } else {
                    handleNoTransactions()
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("MonthlyStatFragment", "Database error: ${error.message}")
            }
        })
    }

    private fun updateSpinnerAndUI(monthYearList: List<String>) {
        if (!isAdded) {
            Log.w("MonthlyStatFragment", "Fragment is not attached to a context. Skipping update.")
            return
        }

        if (monthYearList.isNotEmpty()) {
            // Parse monthYearList into Date objects for correct sorting
            val dateFormat = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
            val sortedMonthYearList = monthYearList
                .map { dateFormat.parse(it) to it } // Pair the Date with its string representation
                .sortedByDescending { it.first } // Sort by Date object in descending order
                .map { it.second } // Extract sorted month-year strings

            // Update spinner adapter
            val spinnerAdapter = ArrayAdapter(requireContext(), R.layout.spinner_month, R.id.text1, sortedMonthYearList)
            spinnerAdapter.setDropDownViewResource(R.layout.spinner_month)
            spinner.adapter = spinnerAdapter

            // Set default selection safely
            val currentMonthYear = SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(Date())
            val defaultSelection = sortedMonthYearList.indexOf(currentMonthYear)
            if (defaultSelection != -1) {
                spinner.setSelection(defaultSelection)
            } else {
                // If currentMonthYear is not in the list, show it as the first item
                val updatedList = listOf(currentMonthYear) + sortedMonthYearList
                val newAdapter = ArrayAdapter(requireContext(), R.layout.spinner_month, R.id.text1, updatedList)
                newAdapter.setDropDownViewResource(R.layout.spinner_month)
                spinner.adapter = newAdapter
                spinner.setSelection(0)
            }

            filterTransactionsByMonth()
        } else {
            handleNoTransactions()
        }
    }


    private fun filterTransactionsByMonth() {
        val filteredTransactions = allTransactions.filter { transaction ->
            isInSelectedMonth(transaction.date)
        }

        val totalExpense = filteredTransactions.filter { it.amount < 0 }.sumOf { it.amount }
        val formattedAmount = if (totalExpense == 0.0) {
            "RM0.00"
        } else {
            String.format("RM%.2f", -totalExpense)
        }
        tvTotalAmount.text = formattedAmount

        statAdapter.updateList(filteredTransactions)
    }

    private fun isInSelectedMonth(date: String): Boolean {
        return try {
            val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
            val selectedDateFormat = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
            val transactionDate = dateFormat.parse(date)
            val transactionMonth = selectedDateFormat.format(transactionDate)
            transactionMonth == selectedMonth
        } catch (e: Exception) {
            false
        }
    }

    private fun handleNoTransactions() {
        val currentMonthYear = SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(Date())
        val monthYearList = listOf(currentMonthYear)

        val updatedSpinnerAdapter = ArrayAdapter(requireContext(), R.layout.spinner_month, R.id.text1, monthYearList)
        updatedSpinnerAdapter.setDropDownViewResource(R.layout.spinner_month)
    }

    private fun setupSpinner() {
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                selectedMonth = parent.getItemAtPosition(position).toString()
                filterTransactionsByMonth()
            }

            override fun onNothingSelected(parent: AdapterView<*>) {
                // Handle the case when no item is selected
            }
        }
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
                Log.e("MonthlyStatFragment", "Database error: ${error.message}")
            }
        })
    }

    private fun parseDate(date: String): Date {
        val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        return dateFormat.parse(date) ?: Date()
    }
}
