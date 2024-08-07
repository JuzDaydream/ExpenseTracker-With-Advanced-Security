package com.example.expensetracker.dataAdapter

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.expensetracker.R
import com.example.expensetracker.data.Budget
import com.example.expensetracker.data.Transaction
import com.google.firebase.database.FirebaseDatabase
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.absoluteValue

class BudgetAdapter(
    private val budgetList: ArrayList<Budget>,
    private val transactionList: ArrayList<Transaction>,
    private val categoryMap: MutableMap<String, String>,
    private val onItemClick: (Budget) -> Unit
) : RecyclerView.Adapter<BudgetAdapter.BudgetViewHolder>() {

    inner class BudgetViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val budgetName: TextView = itemView.findViewById(R.id.budget_name)
        val budgetTotal: TextView = itemView.findViewById(R.id.budget_total)
        val budgetSpentAmount: TextView = itemView.findViewById(R.id.budget_spent_amount)
        val budgetRemainAmount: TextView = itemView.findViewById(R.id.budget_remain_amount)
        val budgetPercent: TextView = itemView.findViewById(R.id.budget_percent)
        val budgetStartDate: TextView = itemView.findViewById(R.id.budget_startDate)
        val budgetEndDate: TextView = itemView.findViewById(R.id.budget_endDate)
        val budgetRemain: TextView = itemView.findViewById(R.id.budget_remain)
        val budgetIcon: ImageView = itemView.findViewById(R.id.icon)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BudgetViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.budget_item, parent, false)
        return BudgetViewHolder(view)
    }

    override fun onBindViewHolder(holder: BudgetViewHolder, position: Int) {
        val budget = budgetList[position]

        fetchCategoryName(budget.category) { categoryName ->
            holder.budgetName.text = categoryName ?: "Unknown Category"
        }
        holder.budgetTotal.text = "RM${String.format("%.2f" ,budget.amount)}"

        val spentAmount = calculateSpentAmount(budget)
        holder.budgetSpentAmount.text = "RM${String.format("%.2f" ,spentAmount)}"

        val remainingAmount = budget.amount - spentAmount

        if (remainingAmount < 0) {
            holder.budgetRemain.text = "Overspend"
            holder.budgetRemainAmount.text = "RM${String.format("%.2f",remainingAmount.absoluteValue)}"
        } else {
            holder.budgetRemainAmount.text = "RM${String.format("%.2f" ,remainingAmount)}"
        }

        val percent = if (budget.amount != 0.0) (remainingAmount / budget.amount) * 100 else 0.0
        holder.budgetPercent.text = "${percent.toInt()}%"

        holder.budgetStartDate.text = budget.startDate
        holder.budgetEndDate.text = budget.endDate

        holder.budgetRemainAmount.setTextColor(
            ContextCompat.getColor(
                holder.itemView.context,
                if (remainingAmount >= 0) R.color.green else R.color.red
            )
        )

        // Set up the click listener
        holder.itemView.setOnClickListener {
            onItemClick(budget)
        }
        // Set icon based on iconName
        val iconName = categoryMap[budget.category]
        if (iconName != null) {
            val iconResId = holder.itemView.context.resources.getIdentifier(
                iconName,
                "drawable",
                holder.itemView.context.packageName
            )
            holder.budgetIcon.setImageResource(iconResId)
        }
    }

    override fun getItemCount(): Int {
        return budgetList.size
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

    private fun fetchCategoryName(categoryId: String, onComplete: (String?) -> Unit) {
        val categoryRef = FirebaseDatabase.getInstance("https://expensetracker-a260c-default-rtdb.asia-southeast1.firebasedatabase.app")
            .getReference("Category")
            .child(categoryId)

        categoryRef.child("name").get().addOnSuccessListener { snapshot ->
            val categoryName = snapshot.getValue(String::class.java)
            onComplete(categoryName)
        }.addOnFailureListener { exception ->
            Log.e("DEBUGTEST", "Error fetching category name: ${exception.message}")
            onComplete(null)
        }
    }


    fun updateList(
        newBudgets: ArrayList<Budget>,
        newTransactions: ArrayList<Transaction>,
        newCategoryMap: MutableMap<String, String>
    ) {
        budgetList.clear()
        budgetList.addAll(newBudgets.distinctBy { it.id }) // Ensure unique items
        transactionList.clear()
        transactionList.addAll(newTransactions)
        categoryMap.clear()
        categoryMap.putAll(newCategoryMap)
        notifyDataSetChanged()
    }
}