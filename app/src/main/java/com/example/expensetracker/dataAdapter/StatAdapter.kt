package com.example.expensetracker.dataAdapter

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.expensetracker.R
import com.example.expensetracker.data.Transaction
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

class StatAdapter(
    private var transactionList: List<Transaction>,
    private val categoryMap: Map<String, String>,
) : RecyclerView.Adapter<StatAdapter.MyViewHolder>() {

    private var groupedTransactions: List<Pair<String, Double>> = emptyList()
    private var totalAmount: Double = 0.0

    init {
        groupTransactionsAndCalculateTotal()
    }

    private fun groupTransactionsAndCalculateTotal() {
        groupedTransactions = transactionList
            .filter { it.amount < 0 } // Filter only negative transactions
            .groupBy { it.category }
            .map { entry ->
                val totalAmountCategory = entry.value.sumOf { it.amount }
                entry.key to totalAmountCategory
            }
            .filter { it.second < 0 } // Ensure we only keep categories with negative totals

        totalAmount = groupedTransactions.sumOf { it.second }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MyViewHolder {
        val itemView = LayoutInflater.from(parent.context).inflate(R.layout.stat_item, parent, false)
        return MyViewHolder(itemView)
    }

    override fun onBindViewHolder(holder: MyViewHolder, position: Int) {
        val (category, totalAmountCategory) = groupedTransactions[position]

        // Fetch the iconName using the categoryId
//        for (i=0;i<transactionList.size;i++){
//
//        }
        // Fetch the iconName using the categoryId from Firebase
        val database =
            FirebaseDatabase.getInstance("https://expensetracker-a260c-default-rtdb.asia-southeast1.firebasedatabase.app")
        val categoryRef = database.getReference("Category").child(category)

        categoryRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val iconName = snapshot.child("icon").getValue(String::class.java)
                val context = holder.itemView.context
                val resourceId =
                    context.resources.getIdentifier(iconName ?: "", "drawable", context.packageName)
                holder.icon.setImageResource(if (resourceId != 0) resourceId else R.drawable.icon_money)

                // Set category name and total amount
                holder.stat_name.text = categoryMap[category] ?: "Unknown"
                holder.stat_amount.text = String.format("RM%.2f", Math.abs(totalAmountCategory))

                // Calculate and set the percentage
                val percentage =
                    if (totalAmount != 0.0) (totalAmountCategory / totalAmount) * 100 else 0.0
                holder.stat_percent.text = String.format("%.2f%%", percentage)

                Log.d("StatAdapter", "Category: $category")
                Log.d("StatAdapter", "Icon Name: $iconName")
                Log.d("StatAdapter", "Resource ID: $resourceId")
            }

            override fun onCancelled(error: DatabaseError) {
                // Handle possible errors
                holder.icon.setImageResource(R.drawable.icon_money)
            }
        })
    }

    override fun getItemCount(): Int {
        return groupedTransactions.size
    }
    fun getGroupedTransactions(): List<Pair<String, Double>> {
        return groupedTransactions
    }
    class MyViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val icon: ImageView = itemView.findViewById(R.id.icon)
        val stat_name: TextView = itemView.findViewById(R.id.stat_name)
        val stat_amount: TextView = itemView.findViewById(R.id.stat_amount)
        val stat_percent: TextView = itemView.findViewById(R.id.stat_percent)
    }

    fun updateList(newList: List<Transaction>) {
        transactionList = newList
        groupTransactionsAndCalculateTotal()
        notifyDataSetChanged()
    }
}
