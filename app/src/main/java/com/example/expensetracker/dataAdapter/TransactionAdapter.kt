package com.example.expensetracker.dataAdapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.expensetracker.R
import com.example.expensetracker.data.Transaction

class TransactionAdapter(
    private var transactionList: ArrayList<Transaction>,
    private val categoryMap: Map<String, String>,
    private val onItemClick: (Transaction) -> Unit // Correctly defined lambda
) : RecyclerView.Adapter<TransactionAdapter.MyViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MyViewHolder {
        val itemView = LayoutInflater.from(parent.context).inflate(R.layout.transaction_item, parent, false)
        return MyViewHolder(itemView)
    }

    override fun onBindViewHolder(holder: MyViewHolder, position: Int) {
        val currentItem = transactionList[position]
        holder.trans_name.text = currentItem.title

        // Fetch the iconName using the categoryId
        val iconName = categoryMap[currentItem.category]
        val context = holder.itemView.context
        val resourceId = context.resources.getIdentifier(iconName ?: "", "drawable", context.packageName)
        holder.icon.setImageResource(if (resourceId != 0) resourceId else R.drawable.icon_money)

        // Set the amount and color
        holder.trans_amount.text = String.format(
            if (currentItem.amount > 0) "+RM%.2f" else "-RM%.2f",
            Math.abs(currentItem.amount)
        )
        holder.trans_amount.setTextColor(
            ContextCompat.getColor(holder.itemView.context, if (currentItem.amount > 0) R.color.green else R.color.red)
        )

        holder.trans_note.text = currentItem.note
        holder.trans_date.text = currentItem.date

        // Set click listener
        holder.itemView.setOnClickListener {
            onItemClick(currentItem)
        }
    }

    override fun getItemCount(): Int {
        return transactionList.size
    }

    class MyViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val icon: ImageView = itemView.findViewById(R.id.icon)
        val trans_name: TextView = itemView.findViewById(R.id.category_name)
        val trans_amount: TextView = itemView.findViewById(R.id.trans_amount)
        val trans_note: TextView = itemView.findViewById(R.id.trans_note)
        val trans_date: TextView = itemView.findViewById(R.id.trans_date)
    }

    fun updateList(newList: ArrayList<Transaction>) {
        transactionList.clear()
        transactionList.addAll(newList.distinctBy { it.id }) // Ensure unique items
        notifyDataSetChanged()
    }
}
