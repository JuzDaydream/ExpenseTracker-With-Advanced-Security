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
import com.example.expensetracker.databinding.ActivityAddSavingGoalBinding
import com.google.firebase.database.FirebaseDatabase
import java.util.Calendar

class AddSavingGoalActivity : AppCompatActivity() {
    private lateinit var binding: ActivityAddSavingGoalBinding
    private lateinit var userID: String

    private val startDate = Calendar.getInstance()
    private val endDate = Calendar.getInstance()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        userID = intent.getStringExtra("userID").toString()


        binding = ActivityAddSavingGoalBinding.inflate(layoutInflater)

        binding.iconBack.setOnClickListener { finish() }

        binding.editImage.setOnClickListener{
            showIconSelectionDialog()
        }

        binding.editStartDate.setOnClickListener { showDatePickerDialog(binding.editStartDate, isStartDate = true) }
        binding.editEndDate.setOnClickListener { showDatePickerDialog(binding.editEndDate, isStartDate = false) }

        binding.btnCreate.setOnClickListener {
            getNextGoalID{newID -> saveGoal(newID)}
        }

        setContentView(binding.root)
    }

    private fun saveGoal(newId: String) {
        Log.d("DEBUGTEST", "saveGoal method called with ID: $newId")

        // Retrieve category details from input fields
        val title = binding.editTitle.text.toString()
        val amount = binding.editAmount.text.toString().toDoubleOrNull() ?: 0.0
        val startDate = binding.editStartDate.text.toString()
        val endDate = binding.editEndDate.text.toString()
        val notes = binding.editNotes.text.toString()
        val icon = binding.editImage.text.toString()

        if (title.isNotEmpty() && icon.isNotEmpty() && amount!= 0.0 && startDate.isNotEmpty() && endDate.isNotEmpty() && startDate!=endDate) {
            val goalRef = FirebaseDatabase.getInstance("https://expensetracker-a260c-default-rtdb.asia-southeast1.firebasedatabase.app")
                .getReference("SavingGoal")

            // Create a new Category object
            val savingGoal = mapOf(
                "id" to newId,
                "title" to title,
                "startDate" to startDate,
                "endDate" to endDate,
                "amount" to amount,
                "notes" to notes,
                "icon" to icon
            )

            // Save the goal in Firebase
            goalRef.child(newId).setValue(savingGoal).addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    // Successfully saved the goal
                    Log.d("DEBUGTEST", "Goal saved successfully")

                    val userRef = FirebaseDatabase.getInstance("https://expensetracker-a260c-default-rtdb.asia-southeast1.firebasedatabase.app")
                        .getReference("User").child(userID).child("goalList")

                    userRef.get().addOnSuccessListener { snapshot ->
                        if (snapshot.exists()) {
                            val maxIndex = snapshot.children.mapNotNull {
                                it.key?.toIntOrNull()
                            }.maxOrNull() ?: 0

                            val newIndex = maxIndex + 1

                            userRef.child(newIndex.toString()).setValue(newId).addOnCompleteListener { userTask ->
                                if (userTask.isSuccessful) {
                                    finish()
                                } else {
                                }
                            }
                        } else {
                        }
                    }.addOnFailureListener { exception ->
                    }
                } else {
                    // Log the error
                    Log.e("DEBUGTEST", "Error saving goal: ${task.exception?.message}")
                }
            }
        } else {
            // Handle missing fields
            binding.tvErrorMsg.setText("Please fill all required fields")
        }
    }
    private fun getNextGoalID(callback: (String) -> Unit) {
        val goalRef = FirebaseDatabase.getInstance("https://expensetracker-a260c-default-rtdb.asia-southeast1.firebasedatabase.app")
            .getReference("SavingGoal")

        goalRef.get().addOnSuccessListener { snapshot ->
            if (snapshot.exists()) {
                // Extract and parse IDs
                val ids = snapshot.children.mapNotNull {
                    it.child("id").getValue(String::class.java)
                }

                Log.d("AddSavingGoalActivity", "Existing IDs: $ids") // Log existing IDs

                // Extract numeric part from IDs
                val maxId = ids.mapNotNull { id ->
                    id.replace("S", "").toIntOrNull()
                }.maxOrNull() ?: 0

                Log.d("AddSavingGoalActivity", "Max ID: $maxId") // Log max ID found

                // Generate new ID
                val newId = "S${maxId + 1}"

                // Invoke callback with the new ID
                callback(newId)
            } else {
                // Handle case where there are no goals yet
                val newId = "S1"
                callback(newId)
            }
        }.addOnFailureListener { exception ->
            Log.e("AddSavingGoalActivity", "Error fetching Goals: ${exception.message}", exception)
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
                        binding.tvErrorMsg.text="End date cannot be before start date."
                        binding.btnCreate.isClickable = false
                        binding.btnCreate.isEnabled = false                    }
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