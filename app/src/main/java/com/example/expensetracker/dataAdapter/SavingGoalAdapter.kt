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
import com.example.expensetracker.data.SavingGoal
import com.example.expensetracker.data.Transaction
import com.google.firebase.database.FirebaseDatabase
import java.text.SimpleDateFormat
import java.util.*

class SavingGoalAdapter(
    private val savingGoalList: ArrayList<SavingGoal>,
    private val transactionList: ArrayList<Transaction>,
    private val onItemClick: (SavingGoal) -> Unit
) : RecyclerView.Adapter<SavingGoalAdapter.SavingGoalViewHolder>() {

    inner class SavingGoalViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val goalTitle: TextView = itemView.findViewById(R.id.saving_name)
        val goalAmount: TextView = itemView.findViewById(R.id.saving_total)
        val goalSavedAmount: TextView = itemView.findViewById(R.id.saving_saved_amount)
        val goalRemainAmount:TextView=itemView.findViewById(R.id.saving_left_amount)
        val goalPercent: TextView=itemView.findViewById(R.id.saving_percent)
        val goalPerDay: TextView=itemView.findViewById(R.id.saving_per_day)
        val goalStartDate: TextView = itemView.findViewById(R.id.saving_startDate)
        val goalEndDate: TextView = itemView.findViewById(R.id.saving_endDate)
        val goalIcon: ImageView = itemView.findViewById(R.id.icon)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SavingGoalViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.saving_goal_item, parent, false)
        return SavingGoalViewHolder(view)
    }

    override fun onBindViewHolder(holder: SavingGoalViewHolder, position: Int) {
        val savingGoal = savingGoalList[position]

        holder.goalTitle.text = savingGoal.title
        holder.goalAmount.text = "RM${String.format("%.2f", savingGoal.amount)}"

        val savedAmount = calculateSavingAmount(savingGoal)
        holder.goalSavedAmount.text = "RM${String.format("%.2f", savedAmount)}"

        val remainAmount = savingGoal.amount-savedAmount
        holder.goalRemainAmount.text= "RM${String.format("%.2f", remainAmount)}"

        // Calculate percentage of goal achieved
        val goalPercent = if (savingGoal.amount > 0) {
            (savedAmount / savingGoal.amount) * 100
        } else {
            0.0
        }
        holder.goalPercent.text = String.format("%.2f%%", goalPercent)        // goalPerDay = goalTotal/(startDate - endDate)


        // Calculate goalPerDay
        val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        val startDate = dateFormat.parse(savingGoal.startDate)
        val endDate = dateFormat.parse(savingGoal.endDate)

        val totalDays = if (startDate != null && endDate != null) {
            ((endDate.time - startDate.time) / (1000 * 60 * 60 * 24)).toInt()
        } else {
            0
        }

        val goalPerDay = if (totalDays > 0) {
            savingGoal.amount / totalDays
        } else {
            0.0
        }

        holder.goalPerDay.text = "RM${String.format("%.2f", goalPerDay)}/Day"

        holder.goalStartDate.text = savingGoal.startDate
        holder.goalEndDate.text = savingGoal.endDate

        // Set up the click listener
        holder.itemView.setOnClickListener {
            onItemClick(savingGoal)
        }

        // Set icon based on iconName
        val iconName = savingGoal.icon
        if (iconName != null) {
            val iconResId = holder.itemView.context.resources.getIdentifier(
                iconName,
                "drawable",
                holder.itemView.context.packageName
            )
            holder.goalIcon.setImageResource(iconResId)
        }
    }

    override fun getItemCount(): Int {
        return savingGoalList.size
    }

    private fun calculateSavingAmount(savingGoal: SavingGoal): Double {
        val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        val startDate = sdf.parse(savingGoal.startDate)
        val endDate = sdf.parse(savingGoal.endDate)
        var savingAmount = 0.0

        // Calculate the saving amount based on categoryId which equals savingGoal.id
        savingAmount = transactionList.filter { transaction ->
            val transactionDate = sdf.parse(transaction.date)
            transaction.category == savingGoal.id &&
                    transactionDate != null &&
                    startDate != null &&
                    endDate != null &&
                    transactionDate in startDate..endDate &&
                    transaction.amount > 0
        }.sumOf { it.amount }

        return savingAmount
    }


    fun updateList(
        newSavingGoals: ArrayList<SavingGoal>,
        newTransactions: ArrayList<Transaction>
    ) {
        savingGoalList.clear()
        savingGoalList.addAll(newSavingGoals.distinctBy { it.id }) // Ensure unique items
        transactionList.clear()
        transactionList.addAll(newTransactions)
        notifyDataSetChanged()
    }
}
