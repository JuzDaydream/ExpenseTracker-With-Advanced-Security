package com.example.expensetracker

import android.app.Dialog
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.expensetracker.data.Transaction
import com.example.expensetracker.data.User
import com.example.expensetracker.dataAdapter.TransactionAdapter
import com.example.expensetracker.databinding.FragmentTransactionBinding
import com.google.firebase.database.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
class TransactionFragment : Fragment() {
    private lateinit var binding: FragmentTransactionBinding
    private lateinit var userRef: DatabaseReference
    private lateinit var transRef: DatabaseReference
    private lateinit var transactionList: ArrayList<Transaction>
    private lateinit var adapter: TransactionAdapter
    private lateinit var userID: String
    private lateinit var categoryMap: MutableMap<String, String>
    private var selectedMonth: String = SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(Date())
    private var allTransactions: ArrayList<Transaction> = ArrayList()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentTransactionBinding.inflate(inflater, container, false)
        val view = binding.root
        userID = (requireActivity() as MainActivity).getUserID()

        Log.d("TransactionFragment", "Selected Month: $selectedMonth")
        binding.btnAddTrans.setOnClickListener {
            showPopup()
        }

        adapter = TransactionAdapter(ArrayList(), mutableMapOf()) { transaction ->
            // Handle item click
            val intent = Intent(requireContext(), EditTransactionActivity::class.java)
            intent.putExtra("transactionID", transaction.id)
            intent.putExtra("userID", userID)
            startActivity(intent)
        }
        handleNoTransactions()

        binding.recyclerTransaction.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerTransaction.setHasFixedSize(true)

//        binding.tvIncomeAmount.visibility = View.GONE
//        binding.tvExpenseAmount.visibility = View.GONE
//        binding.tvBalanceAmount.visibility = View.GONE
        binding.recyclerTransaction.visibility = View.GONE

        binding.spinnerMonthYear.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                selectedMonth = parent.getItemAtPosition(position).toString()
                fetchTransactionsForSelectedMonth(allTransactions)
            }

            override fun onNothingSelected(parent: AdapterView<*>) {
                // Do nothing
            }
        }

        return view
    }

    private fun getTransactions() {
        userRef = FirebaseDatabase.getInstance("https://expensetracker-a260c-default-rtdb.asia-southeast1.firebasedatabase.app").getReference("User").child(userID)
        userRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    val user = snapshot.getValue(User::class.java)
                    if (user != null) {
                        val transactionIds = user.transactionList ?: emptyList()
                        if (transactionIds.isNotEmpty()) {
                            fetchTransactions(transactionIds)
                        } else {
                            handleNoTransactions()
                        }
                    }
                } else {
                    handleNoTransactions()
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("TransactionFragment", "Database error: ${error.message}")
            }
        })
    }

    private fun handleNoTransactions() {
        val currentMonthYear = SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(Date())
        val monthYearList = listOf(currentMonthYear)

        val updatedSpinnerAdapter = ArrayAdapter(requireContext(), R.layout.spinner_month, R.id.text1, monthYearList)
        updatedSpinnerAdapter.setDropDownViewResource(R.layout.spinner_month)
        binding.spinnerMonthYear.adapter = updatedSpinnerAdapter
        binding.spinnerMonthYear.setSelection(0)

        fetchTransactionsForSelectedMonth(ArrayList())

        binding.tvIncomeAmount.visibility = View.VISIBLE
        binding.tvExpenseAmount.visibility = View.VISIBLE
        binding.tvBalanceAmount.visibility = View.VISIBLE
        binding.recyclerTransaction.visibility = View.VISIBLE
    }

    private fun fetchTransactions(transactionIds: List<String>) {
        transRef = FirebaseDatabase.getInstance("https://expensetracker-a260c-default-rtdb.asia-southeast1.firebasedatabase.app").getReference("Transaction")
        allTransactions.clear()
        val monthYearSet = mutableSetOf<String>()
        var completedListeners = 0

        if (transactionIds.isEmpty()) {
            handleNoTransactions()
            return
        }

        for (id in transactionIds) {
            if (id.isNullOrEmpty()) {
                Log.w("TransactionFragment", "Skipping null or empty transaction ID.")
                completedListeners++
                continue
            }

            transRef.child(id).addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (snapshot.exists()) {
                        try {
                            val transaction = snapshot.getValue(Transaction::class.java)
                            if (transaction != null) {
                                val date = parseDate(transaction.date)
                                val monthYear = SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(date)
                                monthYearSet.add(monthYear)
                                allTransactions.add(transaction)
                            } else {
                                Log.w("TransactionFragment", "Transaction data for ID $id is null.")
                            }
                        } catch (e: Exception) {
                            Log.e("TransactionFragment", "Error parsing transaction $id: ${e.message}")
                        }
                    } else {
                        Log.w("TransactionFragment", "Transaction ID $id not found in database.")
                    }
                    completedListeners++
                    if (completedListeners == transactionIds.size) {
                        val monthYearList = monthYearSet.toList().sortedDescending()
                        updateSpinnerAndUI(monthYearList)
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e("TransactionFragment", "Database error: ${error.message}")
                }
            })
        }
    }

    private fun fetchTransactionsForSelectedMonth(allTransactions: ArrayList<Transaction>) {
        val filteredList = allTransactions.filter { transaction ->
            isInSelectedMonth(transaction.date)
        }.toCollection(ArrayList())

        val totalIncome = filteredList.filter { it.amount > 0 }.sumOf { it.amount }
        val totalExpense = filteredList.filter { it.amount < 0 }.sumOf { it.amount }
        val totalBalance = totalIncome + totalExpense

        val sortedList = filteredList.sortedByDescending { parseDate(it.date) }.toCollection(ArrayList())

        if (isAdded) {
            requireActivity().runOnUiThread {
                adapter.updateList(sortedList)

                binding.tvIncomeAmount.text = String.format("+RM%.2f", totalIncome)
                binding.tvExpenseAmount.text = when {
                    totalExpense == 0.0 -> String.format("-RM%.2f", totalExpense)
                    else -> String.format("-RM%.2f", -totalExpense)
                }
                binding.tvBalanceAmount.text = when {
                    totalBalance > 0 -> {
                        binding.tvBalanceAmount.setTextColor(ContextCompat.getColor(requireContext(), R.color.green))
                        String.format("+RM%.2f", totalBalance)
                    }
                    totalBalance < 0 -> {
                        binding.tvBalanceAmount.setTextColor(ContextCompat.getColor(requireContext(), R.color.red))
                        String.format("-RM%.2f", -totalBalance)
                    }
                    else -> {
                        binding.tvBalanceAmount.setTextColor(ContextCompat.getColor(requireContext(), R.color.green))
                        String.format("RM%.2f", totalBalance)
                    }
                }
            }
        }
    }

    private fun updateSpinnerAndUI(monthYearList: List<String>) {
        if (!isAdded) {
            Log.w("TransactionFragment", "Fragment is not attached to a context. Skipping update.")
            return
        }

        if (monthYearList.isNotEmpty()) {
            // Parse monthYearList into Date objects for correct sorting
            val dateFormat = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
            val sortedMonthYearList = monthYearList
                .map { dateFormat.parse(it) to it } // Pair the Date with its string representation
                .sortedByDescending { it.first } // Sort by Date object
                .map { it.second } // Extract sorted month-year strings

            // Update spinner adapter
            val updatedSpinnerAdapter = ArrayAdapter(requireContext(), R.layout.spinner_month, R.id.text1, sortedMonthYearList)
            updatedSpinnerAdapter.setDropDownViewResource(R.layout.spinner_month)
            binding.spinnerMonthYear.adapter = updatedSpinnerAdapter

            // Set default selection safely
            val currentMonthYear = SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(Date())
            val defaultSelection = sortedMonthYearList.indexOf(currentMonthYear)
            if (defaultSelection != -1) {
                binding.spinnerMonthYear.setSelection(defaultSelection)
            } else {
                // If currentMonthYear is not in the list, show it as the first item
                val updatedList = listOf(currentMonthYear) + sortedMonthYearList
                val newAdapter = ArrayAdapter(requireContext(), R.layout.spinner_month, R.id.text1, updatedList)
                newAdapter.setDropDownViewResource(R.layout.spinner_month)
                binding.spinnerMonthYear.adapter = newAdapter
                binding.spinnerMonthYear.setSelection(0)
            }

            fetchTransactionsForSelectedMonth(allTransactions)
        } else {
            handleNoTransactions()
        }

        binding.tvIncomeAmount.visibility = View.VISIBLE
        binding.tvExpenseAmount.visibility = View.VISIBLE
        binding.tvBalanceAmount.visibility = View.VISIBLE
        binding.recyclerTransaction.visibility = View.VISIBLE
    }


    private fun isInSelectedMonth(date: String): Boolean {
        return try {
            val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
            val selectedDateFormat = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
            val transactionDate = dateFormat.parse(date)
            val transactionMonth = selectedDateFormat.format(transactionDate)
            transactionMonth == selectedMonth
        } catch (e: Exception) {
            Log.e("TransactionFragment", "Error parsing date: ${e.message}")
            false
        }
    }

    private fun parseDate(date: String): Date {
        val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        return dateFormat.parse(date) ?: Date()
    }

    private fun showPopup() {
        val dialog = Dialog(requireContext())
        dialog.setContentView(R.layout.popup_add_transaction)
        dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        dialog.window?.setBackgroundDrawableResource(R.drawable.shadow_bg)

        val cardManual = dialog.findViewById<View>(R.id.card_manual)
        val cardScan = dialog.findViewById<View>(R.id.card_scan)
        val btnClose = dialog.findViewById<View>(R.id.btn_close)
        btnClose.setOnClickListener {
            dialog.dismiss()
        }
        cardScan.setOnClickListener {
            val intent = Intent(requireContext(), ScanReceiptActivity::class.java)
            intent.putExtra("UserID", userID)
            startActivity(intent)
            dialog.dismiss()
        }

        cardManual.setOnClickListener {
            dialog.dismiss()
            val intent = Intent(requireContext(), AddTransactionActivity::class.java)
            intent.putExtra("userID", userID)
            startActivity(intent)
        }

        dialog.show()
    }

    private fun fetchCategories() {
        val categoriesRef = FirebaseDatabase.getInstance("https://expensetracker-a260c-default-rtdb.asia-southeast1.firebasedatabase.app").getReference("Category")
        categoryMap = mutableMapOf()

        categoriesRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                for (categorySnapshot in snapshot.children) {
                    val categoryId = categorySnapshot.key
                    val iconName = categorySnapshot.child("icon").getValue(String::class.java)
                    if (categoryId != null && iconName != null) {
                        categoryMap[categoryId] = iconName
                    }
                }
                adapter = TransactionAdapter(ArrayList(), categoryMap) { transaction ->
                    // Handle item click
                    val intent = Intent(requireContext(), EditTransactionActivity::class.java)
                    intent.putExtra("transactionID", transaction.id)
                    intent.putExtra("userID", userID)
                    startActivity(intent)
                }
                binding.recyclerTransaction.adapter = adapter
                getTransactions()
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("TransactionFragment", "Error fetching categories: ${error.message}")
            }
        })
    }

    override fun onResume() {
        super.onResume()
        fetchCategories()
    }
}