package com.example.expensetracker

import android.app.DatePickerDialog
import android.app.Dialog
import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.AbsListView
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.GridView
import android.widget.ImageView
import com.example.expensetracker.data.SavingGoal
import com.example.expensetracker.data.Transaction
import com.example.expensetracker.databinding.ActivityAddSavingGoalBinding
import com.example.expensetracker.databinding.ActivityEditSavingGoalBinding
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import java.util.Calendar

class EditSavingGoalActivity : AppCompatActivity() {
    private lateinit var binding: ActivityEditSavingGoalBinding
    private lateinit var userID: String
    private lateinit var goalID: String

    private val startDate = Calendar.getInstance()
    private val endDate = Calendar.getInstance()
    private var selectedCategoryId: String? = null // To store the selected category ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        userID = intent.getStringExtra("userID").toString()
        goalID = intent.getStringExtra("savingGoalID").toString()

        binding = ActivityEditSavingGoalBinding.inflate(layoutInflater)
        fetchGoal()

        binding.btnUpdate.setOnClickListener{
            updateTransaction()
        }

        binding.iconBack.setOnClickListener { finish() }

        binding.editImage.setOnClickListener{
            showIconSelectionDialog()
        }

        binding.editStartDate.setOnClickListener { showDatePickerDialog(binding.editStartDate, isStartDate = true) }
        binding.editEndDate.setOnClickListener { showDatePickerDialog(binding.editEndDate, isStartDate = false) }

        binding.btnRemove.setOnClickListener {
            showPopup()
        }

        setContentView(binding.root)
    }

    private fun fetchGoal() {
        val goalRef = FirebaseDatabase.getInstance("https://expensetracker-a260c-default-rtdb.asia-southeast1.firebasedatabase.app").getReference("SavingGoal").child(goalID)
        goalRef.get().addOnSuccessListener { snapshot ->
            val goal = snapshot.getValue(SavingGoal::class.java)
            if (goal != null) {
                binding.editTitle.setText(goal.title)
                binding.editAmount.setText(String.format("%.2f" ,goal.amount))
                binding.editStartDate.setText(goal.startDate)
                binding.editEndDate.setText(goal.endDate)

                binding.editImage.setText(goal.icon)

                val resourceId = resources.getIdentifier(goal.icon, "drawable", packageName)
                if (resourceId != 0) {
                    // Set the image resource if the resource ID is valid
                    binding.iconImage.setImageResource(resourceId)
                } else {
                    // Handle case where the resource ID is not found
                    binding.editImage.setText("icon_money")
                    binding.iconImage.setImageResource(R.drawable.icon_money)
                }
            } else {
                Log.e("DEBUGTEST", "Goal not found")
            }
        }.addOnFailureListener { exception ->
            Log.e("DEBUGTEST", "Error fetching goal details: ${exception.message}")
        }
    }
    private fun updateTransaction() {
        val title = binding.editTitle.text.toString()
        val amount = binding.editAmount.text.toString().toDoubleOrNull() ?: 0.0
        val startDate = binding.editStartDate.text.toString()
        val endDate = binding.editEndDate.text.toString()
        val notes = binding.editNotes.text.toString()
        val icon = binding.editImage.text.toString()

        if (title.isNotEmpty() && icon.isNotEmpty() && amount!= 0.0 && startDate.isNotEmpty() && endDate.isNotEmpty() && startDate!=endDate) {
            val goalRef = FirebaseDatabase.getInstance("https://expensetracker-a260c-default-rtdb.asia-southeast1.firebasedatabase.app")
                .getReference("SavingGoal")

            val goal = SavingGoal(goalID,title,amount,startDate,endDate, icon, notes)
            goalRef.child(goalID).setValue(goal).addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    finish() // Close the activity after successful update
                } else {
                    Log.e("DEBUGTEST", "Error updating goal: ${task.exception?.message}")
                }
            }
        } else {
            // Handle missing fields
            binding.tvErrorMsg.setText("Please fill all required fields")
        }
    }

    // DATE
    // DK WHY START CAN BIGGER than END DATE
    private fun showDatePickerDialog(editText: EditText, isStartDate: Boolean) {
        val calendar = if (isStartDate) startDate else endDate
        val currentDate = Calendar.getInstance()

        val datePickerDialog = DatePickerDialog(
            this,
            { _, selectedYear, selectedMonth, selectedDay ->
                calendar.set(selectedYear, selectedMonth, selectedDay)

                val formattedDate = String.format("%02d/%02d/%04d", selectedDay,selectedMonth + 1,selectedYear)
                editText.setText(formattedDate)

                if (!isStartDate) {
                    // Allow end date to be the same or after the start date
                    if ( endDate.timeInMillis < startDate.timeInMillis) {
                        binding.tvErrorMsg.text="End date cannot be before start date."}
                }
            },
            calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)
        )

        // Set the minimum date for end date selection to the start date
        if (!isStartDate) {
            datePickerDialog.datePicker.minDate = startDate.timeInMillis
        } else {
            datePickerDialog.datePicker.minDate = currentDate.timeInMillis
        }

        datePickerDialog.show()
    }
    private fun showPopup() {
        val dialog = Dialog(this)
        dialog.setContentView(R.layout.popup_confirmation)
        dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        dialog.window?.setBackgroundDrawableResource(R.drawable.shadow_bg)

        val btnCancel = dialog.findViewById<View>(R.id.btn_cancel)
        val btnRemove = dialog.findViewById<View>(R.id.btn_remove)

        btnCancel.setOnClickListener {
            dialog.dismiss()
        }
        btnRemove.setOnClickListener {
            removeGoal(goalID)
            finish()
        }

        dialog.show()
    }

    private fun removeGoal(goalID : String) {
        val userRef = FirebaseDatabase.getInstance("https://expensetracker-a260c-default-rtdb.asia-southeast1.firebasedatabase.app")
            .getReference("User")
            .child(userID)

        val goalListRef = userRef.child("SavingGoal")
        val goalRef = FirebaseDatabase.getInstance("https://expensetracker-a260c-default-rtdb.asia-southeast1.firebasedatabase.app").getReference("SavingGoal").child(goalID)
        goalListRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                var indexToRemove: String? = null

                // Find the index (key) where the value matches goalID
                for (child in snapshot.children) {
                    val id = child.value as? String
                    if (id == goalID) {
                        indexToRemove = child.key
                        break
                    }
                }

                if (indexToRemove != null) {
                    // Remove the index containing goalID
                    goalListRef.child(indexToRemove).removeValue()
                        .addOnCompleteListener { task ->
                            if (task.isSuccessful) {
                                Log.d("DEBUGTEST", "Index for transaction ID $goalID removed from transactionList.")
                            } else {
                                Log.e("DEBUGTEST", "Failed to remove index for transaction ID $goalID from transactionList: ${task.exception?.message}")
                            }
                        }
                } else {
                    Log.e("DEBUGTEST", "Transaction ID $goalID not found in transactionList.")
                }

                // Remove the transaction data
                goalRef.removeValue()
                    .addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            Log.d("DEBUGTEST", "Transaction $goalID removed successfully.")
                        } else {
                            Log.e("DEBUGTEST", "Failed to remove transaction: ${task.exception?.message}")
                        }
                    }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("DEBUGTEST", "Database error: ${error.message}")
            }
        })
    }

        // ALL ABOUT ICON BELOW
    private fun showIconSelectionDialog() {
        // Inflate the dialog layout
        val dialog = Dialog(this)
        dialog.setContentView(R.layout.popup_select_icon)
        dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        dialog.window?.setBackgroundDrawableResource(R.drawable.shadow_bg)

        val btnClose = dialog.findViewById<View>(R.id.btn_close)
        btnClose.setOnClickListener {
            dialog.dismiss()
        }

        // Set up the GridView
        val gridView = dialog.findViewById<GridView>(R.id.image_grid)
        val iconResIds = getDrawableIdsByPrefix(this, "icon_")
        val adapter = IconAdapter(this, iconResIds)
        gridView.adapter = adapter

        // Set item click listener to handle icon selection
        gridView.setOnItemClickListener { _, _, position, _ ->
            val selectedIconResId = iconResIds[position]

            // Retrieve the name of the selected icon from the resource ID
            val selectedIconName = resources.getResourceEntryName(selectedIconResId)

            // Update the text of editImage with the selected icon name
            binding.editImage.setText(selectedIconName)
            val resourceId = resources.getIdentifier(selectedIconName, "drawable", packageName)
            if (resourceId != 0) {
                // Set the image resource if the resource ID is valid
                binding.iconImage.setImageResource(resourceId)
            } else {
                // Handle case where the resource ID is not found
                // For example, you could set a default image or show an error
                binding.iconImage.setImageResource(R.drawable.icon_money)
            }
            // Dismiss the dialog
            dialog.dismiss()
        }
        // Show the dialog
        dialog.show()
    }

    private fun getDrawableIdsByPrefix(context: Context, prefix: String): List<Int> {
        val drawableResIds = mutableListOf<Int>()
        val drawableClass = R.drawable::class.java
        val fields = drawableClass.fields

        for (field in fields) {
            if (field.name.startsWith(prefix)) {
                try {
                    val resId = field.getInt(null)
                    drawableResIds.add(resId)
                } catch (e: IllegalAccessException) {
                    e.printStackTrace()
                }
            }
        }

        return drawableResIds
    }

    class IconAdapter(private val context: Context, private val iconResIds: List<Int>) : BaseAdapter() {
        override fun getCount(): Int = iconResIds.size
        override fun getItem(position: Int): Any = iconResIds[position]
        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val imageView: ImageView = convertView as? ImageView ?: ImageView(context)
            imageView.layoutParams = AbsListView.LayoutParams(150, 150)
            imageView.scaleType = ImageView.ScaleType.CENTER_CROP
            imageView.setImageResource(iconResIds[position])
            return imageView
        }
    }
}