package com.example.expensetracker

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.expensetracker.data.SavingGoal
import com.example.expensetracker.data.Transaction
import com.example.expensetracker.data.User
import com.example.expensetracker.dataAdapter.SavingGoalAdapter
import com.example.expensetracker.databinding.FragmentSavingGoalBinding
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.database.*
import java.text.SimpleDateFormat
import java.util.*

class SavingGoalFragment : Fragment() {

    private lateinit var binding: FragmentSavingGoalBinding
    private lateinit var userRef: DatabaseReference
    private lateinit var savingGoalRef: DatabaseReference
    private lateinit var transactionRef: DatabaseReference
    private lateinit var savingGoalList: ArrayList<SavingGoal>
    private lateinit var transactionList: ArrayList<Transaction>
    private lateinit var adapter: SavingGoalAdapter
    private lateinit var userID: String

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentSavingGoalBinding.inflate(inflater, container, false)
        val view = binding.root
        userID = (requireActivity() as MainActivity).getUserID()

        // Find the FloatingActionButton in the inflated view
        val btnAddSavingGoal = view.findViewById<FloatingActionButton>(R.id.btn_add_goal)

        // Set an OnClickListener on the button
        btnAddSavingGoal.setOnClickListener {
            val intent = Intent(requireContext(), AddSavingGoalActivity::class.java)
            intent.putExtra("userID", userID)
            startActivity(intent)
        }

        // Set up the RecyclerView and adapter
        adapter = SavingGoalAdapter(ArrayList(), ArrayList()) { savingGoal ->
            // Handle the click event
            val intent = Intent(requireContext(), EditSavingGoalActivity::class.java)
            intent.putExtra("savingGoalID", savingGoal.id)
            intent.putExtra("userID", userID)
            startActivity(intent)
        }
        binding.recyclerGoal.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerGoal.setHasFixedSize(true)
        binding.recyclerGoal.adapter = adapter

        return view
    }

    private fun fetchSavingGoalsAndTransactions() {
        userRef = FirebaseDatabase.getInstance("https://expensetracker-a260c-default-rtdb.asia-southeast1.firebasedatabase.app").getReference("User").child(userID)
        userRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                Log.d("SavingGoalFragment", "User data snapshot: ${snapshot.value}")
                if (snapshot.exists()) {
                    val user = snapshot.getValue(User::class.java)
                    if (user != null) {
                        val savingGoalIds = user.goalList ?: emptyList()
                        Log.d("SavingGoalFragment", "Saving goal IDs: $savingGoalIds")
                        if (savingGoalIds.isNotEmpty()) {
                            fetchSavingGoals(savingGoalIds)
                        } else {
                            handleNoSavingGoals()
                        }
                    }
                } else {
                    handleNoSavingGoals()
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("SavingGoalFragment", "Database error: ${error.message}")
            }
        })
    }

    private fun fetchSavingGoals(savingGoalIds: List<String>) {
        savingGoalRef = FirebaseDatabase.getInstance("https://expensetracker-a260c-default-rtdb.asia-southeast1.firebasedatabase.app").getReference("SavingGoal")
        transactionRef = FirebaseDatabase.getInstance("https://expensetracker-a260c-default-rtdb.asia-southeast1.firebasedatabase.app").getReference("Transaction")  // Ensure this is initialized

        savingGoalList = ArrayList()
        transactionList = ArrayList()
        var completedListeners = 0

        if (savingGoalIds.isEmpty()) {
            handleNoSavingGoals()
            return
        }

        for (id in savingGoalIds) {
            if (id.isNullOrEmpty()) {
                Log.w("SavingGoalFragment", "Skipping null or empty saving goal ID.")
                completedListeners++
                continue
            }

            savingGoalRef.child(id).addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    Log.d("SavingGoalFragment", "Saving goal snapshot for ID $id: ${snapshot.value}")
                    if (snapshot.exists()) {
                        try {
                            val savingGoal = snapshot.getValue(SavingGoal::class.java)
                            Log.d("SavingGoalFragment", "Parsed Saving Goal: $savingGoal")
                            if (savingGoal != null) {
                                savingGoalList.add(savingGoal)
                            } else {
                                Log.w("SavingGoalFragment", "Saving goal data for ID $id is null.")
                            }
                        } catch (e: Exception) {
                            Log.e("SavingGoalFragment", "Error parsing saving goal $id: ${e.message}")
                        }
                    } else {
                        Log.w("SavingGoalFragment", "Saving goal ID $id not found in database.")
                    }
                    completedListeners++
                    if (completedListeners == savingGoalIds.size) {
                        // Sort the list before updating the adapter
                        sortSavingGoalsByStartDate()
                        fetchTransactionsForSavingGoal()
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e("SavingGoalFragment", "Database error: ${error.message}")
                }
            })
        }
    }

    private fun sortSavingGoalsByStartDate() {
        val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        savingGoalList.sortWith { goal1, goal2 ->
            val date1 = sdf.parse(goal1.startDate)
            val date2 = sdf.parse(goal2.startDate)
            date1?.compareTo(date2) ?: 0
        }
    }

    private fun fetchTransactionsForSavingGoal() {
        transactionRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                transactionList.clear()
                for (transactionSnapshot in snapshot.children) {
                    val transaction = transactionSnapshot.getValue(Transaction::class.java)
                    if (transaction != null) {
                        transactionList.add(transaction)
                    }
                }
                updateSavingGoalAdapter()
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("SavingGoalFragment", "Database error: ${error.message}")
            }
        })
    }

    private fun updateSavingGoalAdapter() {
        val filteredSavingGoals = savingGoalList.filter { savingGoal ->
            val savedAmount = calculateSavingAmount(savingGoal)
            val remainingAmount = savingGoal.amount - savedAmount
            savingGoal.amount != 0.0 || remainingAmount != 0.0
        }.toCollection(ArrayList()) // Convert List to ArrayList

        adapter.updateList(filteredSavingGoals, transactionList)
    }

    private fun calculateSavingAmount(savingGoal: SavingGoal): Double {
        val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        val startDate = sdf.parse(savingGoal.startDate)
        val endDate = sdf.parse(savingGoal.endDate)
        var savingAmount = 0.0

        Log.d("SavingGoalFragment", "Calculating saving amount for goal ${savingGoal.title}, startDate: $startDate, endDate: $endDate")

        // Find the categoryID for the savingGoal.title
        fetchCategoryIdByName(savingGoal.title) { categoryId ->
            if (categoryId != null) {
                // Calculate the saving amount based on categoryId
                savingAmount = transactionList.filter { transaction ->
                    val transactionDate = sdf.parse(transaction.date)
                    transaction.category == categoryId &&
                            transactionDate != null &&
                            startDate != null &&
                            endDate != null &&
                            transactionDate in startDate..endDate &&
                            transaction.amount > 0
                }.sumOf { it.amount }
            }

            // Here you can update the UI or use the calculated savingAmount as needed
            Log.d("SavingGoalFragment", "Calculated saving amount for goal ${savingGoal.title}: $savingAmount")
        }

        return savingAmount
    }

    private fun fetchCategoryIdByName(categoryName: String, onComplete: (String?) -> Unit) {
        val categoryRef = FirebaseDatabase.getInstance("https://expensetracker-a260c-default-rtdb.asia-southeast1.firebasedatabase.app")
            .getReference("Category")

        categoryRef.orderByChild("name").equalTo(categoryName).get().addOnSuccessListener { snapshot ->
            val categoryId = snapshot.children.firstOrNull()?.key
            onComplete(categoryId)
        }.addOnFailureListener { exception ->
            Log.e("SavingGoalFragment", "Error fetching category ID: ${exception.message}")
            onComplete(null)
        }
    }

    private fun handleNoSavingGoals() {
        // Handle the case where there are no saving goals
        binding.recyclerGoal.visibility = View.GONE
    }

    override fun onResume() {
        super.onResume()
        fetchSavingGoalsAndTransactions()
    }
}
