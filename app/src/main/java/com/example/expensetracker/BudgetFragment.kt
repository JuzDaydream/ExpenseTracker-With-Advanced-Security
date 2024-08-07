package com.example.expensetracker

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.expensetracker.data.Budget
import com.example.expensetracker.data.Transaction
import com.example.expensetracker.data.User
import com.example.expensetracker.dataAdapter.BudgetAdapter
import com.example.expensetracker.databinding.FragmentBudgetBinding
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.database.*
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.absoluteValue

class BudgetFragment : Fragment() {
    private lateinit var binding: FragmentBudgetBinding
    private lateinit var userRef: DatabaseReference
    private lateinit var budgetRef: DatabaseReference
    private lateinit var transactionRef: DatabaseReference
    private lateinit var budgetList: ArrayList<Budget>
    private lateinit var transactionList: ArrayList<Transaction>
    private lateinit var adapter: BudgetAdapter
    private lateinit var userID: String
    private lateinit var categoryMap: MutableMap<String, String>
    private var selectedMonth: String = SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(Date())

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentBudgetBinding.inflate(inflater, container, false)
        val view = binding.root
        userID = (requireActivity() as MainActivity).getUserID()
// Find the FloatingActionButton in the inflated view
        val btnAddBudget = view.findViewById<FloatingActionButton>(R.id.btn_addBudget)


        // Set an OnClickListener on the button
        btnAddBudget.setOnClickListener {
            val intent = Intent(requireContext(), AddBudgetActivity::class.java)
            intent.putExtra("userID", userID)
            startActivity(intent)
        }


        // Pass the click handler to the adapter
        adapter = BudgetAdapter(ArrayList(), ArrayList(), mutableMapOf()) { budget ->
            // Handle the click event
            val intent = Intent(requireContext(), EditBudgetActivity::class.java)
            intent.putExtra("budgetID", budget.id)
            intent.putExtra("userID", userID)
            startActivity(intent)
        }
        binding.recyclerBudget.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerBudget.setHasFixedSize(true)
        binding.recyclerBudget.adapter = adapter


        return view
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
                fetchBudgetsAndTransactions()
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("BudgetFragment", "Error fetching categories: ${error.message}")
            }
        })
    }

    private fun fetchBudgetsAndTransactions() {
        userRef = FirebaseDatabase.getInstance("https://expensetracker-a260c-default-rtdb.asia-southeast1.firebasedatabase.app").getReference("User").child(userID)
        userRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    val user = snapshot.getValue(User::class.java)
                    if (user != null) {
                        val budgetIds = user.budgetList ?: emptyList()
                        if (budgetIds.isNotEmpty()) {
                            fetchBudgets(budgetIds)
                        } else {
                            handleNoBudgets()
                        }
                    }
                } else {
                    handleNoBudgets()
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("BudgetFragment", "Database error: ${error.message}")
            }
        })
    }

    private fun fetchBudgets(budgetIds: List<String>) {
        budgetRef = FirebaseDatabase.getInstance("https://expensetracker-a260c-default-rtdb.asia-southeast1.firebasedatabase.app").getReference("Budget")
        transactionRef = FirebaseDatabase.getInstance("https://expensetracker-a260c-default-rtdb.asia-southeast1.firebasedatabase.app").getReference("Transaction")

        budgetList = ArrayList()
        transactionList = ArrayList()
        var completedListeners = 0

        if (budgetIds.isEmpty()) {
            handleNoBudgets()
            return
        }

        for (id in budgetIds) {
            if (id.isNullOrEmpty()) {
                Log.w("BudgetFragment", "Skipping null or empty budget ID.")
                completedListeners++
                continue
            }

            budgetRef.child(id).addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (snapshot.exists()) {
                        try {
                            val budget = snapshot.getValue(Budget::class.java)
                            if (budget != null) {
                                budgetList.add(budget)
                            } else {
                                Log.w("BudgetFragment", "Budget data for ID $id is null.")
                            }
                        } catch (e: Exception) {
                            Log.e("BudgetFragment", "Error parsing budget $id: ${e.message}")
                        }
                    } else {
                        Log.w("BudgetFragment", "Budget ID $id not found in database.")
                    }
                    completedListeners++
                    if (completedListeners == budgetIds.size) {
                        fetchTransactionsForBudget()
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e("BudgetFragment", "Database error: ${error.message}")
                }
            })
        }
    }

    private fun fetchTransactionsForBudget() {
        transactionRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                transactionList.clear()
                for (transactionSnapshot in snapshot.children) {
                    val transaction = transactionSnapshot.getValue(Transaction::class.java)
                    if (transaction != null) {
                        transactionList.add(transaction)
                    }
                }
                updateBudgetAdapter()
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("BudgetFragment", "Database error: ${error.message}")
            }
        })
    }

    private fun updateBudgetAdapter() {
        val filteredBudgets = budgetList.filter { budget ->
            val spentAmount = calculateSpentAmount(budget)
            val remainingAmount = budget.amount - spentAmount
            budget.amount != 0.0 || remainingAmount != 0.0
        }.toCollection(ArrayList()) // Convert List to ArrayList

        adapter.updateList(filteredBudgets, transactionList, categoryMap)
    }
    private fun calculateSpentAmount(budget: Budget): Double {
        val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        val startDate = sdf.parse(budget.startDate)
        val endDate = sdf.parse(budget.endDate)

        return transactionList.filter { transaction ->
            val transactionDate = sdf.parse(transaction.date)
            transaction.category == budget.category &&
                    transactionDate != null &&
                    startDate != null &&
                    endDate != null &&
                    transactionDate in startDate..endDate &&
                    transaction.amount < 0
        }.sumOf { it.amount }.absoluteValue
    }

    /* private fun calculateSpentAmount(budget: Budget): Double {
         val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
         val startDate = sdf.parse(budget.startDate)
         val endDate = sdf.parse(budget.endDate)

         return transactionList.filter { transaction ->
             val transactionDate = sdf.parse(transaction.date)
             transaction.category == budget.category &&
                     transactionDate != null &&
                     startDate != null &&
                     endDate != null &&
                     transactionDate in startDate..endDate &&
                     transaction.amount < 0
         }.sumOf { it.amount }
     }
 */
    private fun handleNoBudgets() {
        // Handle the case where there are no budgets
        binding.recyclerBudget.visibility = View.GONE
    }

    override fun onResume() {
        super.onResume()
        fetchCategories()
        fetchBudgetsAndTransactions()
    }
}