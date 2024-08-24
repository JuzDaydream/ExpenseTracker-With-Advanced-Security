package com.example.expensetracker

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.GridView
import androidx.appcompat.app.AppCompatActivity
import com.example.expensetracker.databinding.ActivityEditCategoryBinding
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

class EditCategoryActivity : AppCompatActivity() {
    private lateinit var binding: ActivityEditCategoryBinding
    private lateinit var categoryId: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEditCategoryBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.iconBack.setOnClickListener { finish() }
        binding.btnRemove.setOnClickListener{
            showPopup()
        }
        // Retrieve the category ID from the intent
        categoryId = intent.getStringExtra("categoryID").toString()
        if (categoryId.isEmpty()) {
            Log.e("DEBUGTEST", "Category ID is missing")
            finish() // End activity if no valid ID is provided
            return
        }

        // Set up listeners
        binding.editImage.setOnClickListener {
            showIconSelectionDialog()
        }

        binding.btnUpdate.setOnClickListener {
            updateCategory()
        }

        // Fetch and populate category details
        fetchCategoryDetails()
    }

    private fun fetchCategoryDetails() {
        val categoryRef = FirebaseDatabase.getInstance("https://expensetracker-a260c-default-rtdb.asia-southeast1.firebasedatabase.app")
            .getReference("Category")
            .child(categoryId)

        categoryRef.get().addOnSuccessListener { snapshot ->
            val name = snapshot.child("name").getValue(String::class.java)
            val icon = snapshot.child("icon").getValue(String::class.java)
            val type = snapshot.child("type").getValue(String::class.java)

            if (name != null && icon != null && type !=null) {
                binding.editName.setText(name)
                binding.editImage.setText(icon)
                val resourceId = resources.getIdentifier(icon, "drawable", packageName)
                if (resourceId != 0) {
                    binding.iconImage.setImageResource(resourceId)
                }

                when (type) {
                    "Expense" -> binding.radioExpense.isChecked = true
                    "Income" -> binding.radioIncome.isChecked = true
                    else -> Log.e("DEBUGTEST", "Unknown category type")
                }

            } else {
                Log.e("DEBUGTEST", "Category details not found")
            }
        }.addOnFailureListener { exception ->
            Log.e("DEBUGTEST", "Error fetching category details: ${exception.message}")
        }
    }

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
                binding.iconImage.setImageResource(R.drawable.icon_money)
            }
            // Dismiss the dialog
            dialog.dismiss()
        }
        // Show the dialog
        dialog.show()
    }

    private fun updateCategory() {
        Log.d("DEBUGTEST", "updateCategory method called with ID: $categoryId")

        // Retrieve category details from input fields
        val name = binding.editName.text.toString()
        val icon = binding.editImage.text.toString()
        val selectedRadioId = binding.radioGroup.checkedRadioButtonId

        val type = when (selectedRadioId) {
            R.id.radio_expense -> "Expense"
            R.id.radio_income -> "Income"
            else -> null // Handle the case where neither is selected
        }

        if (name.isNotEmpty() && icon.isNotEmpty() && type !=null) {
            val categoryRef = FirebaseDatabase.getInstance("https://expensetracker-a260c-default-rtdb.asia-southeast1.firebasedatabase.app")
                .getReference("Category")

            // Create a map of updated category details
            val updatedCategory = mapOf(
                "name" to name,
                "icon" to icon,
                "type" to type
            )

            // Update the category in Firebase
            categoryRef.child(categoryId).updateChildren(updatedCategory).addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    // Successfully updated the category
                    Log.d("DEBUGTEST", "Category updated successfully")
                    finish() // Close the activity after successful update
                } else {
                    // Log the error
                    Log.e("DEBUGTEST", "Error updating category: ${task.exception?.message}")
                }
            }
        } else {
            // Handle missing fields
            binding.tvErrorMsg.setText("Please fill all required fields")
        }
    }
    private fun removeCategory(categoryId: String) {
        val categoryRef = FirebaseDatabase.getInstance("https://expensetracker-a260c-default-rtdb.asia-southeast1.firebasedatabase.app")
            .getReference("Category")
            .child(categoryId)

        categoryRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                var indexToRemove: String? = null

                for (child in snapshot.children) {
                    val id = child.value as? String
                    if (id == categoryId) {
                        indexToRemove = child.key
                        break
                    }
                }

                if (indexToRemove != null) {
                    // Remove the index containing transactionID
                    categoryRef.child(indexToRemove).removeValue()
                        .addOnCompleteListener { task ->
                            if (task.isSuccessful) {
                                Log.d("DEBUGTEST", "Index for transaction ID $categoryId removed from transactionList.")
                            } else {
                                Log.e("DEBUGTEST", "Failed to remove index for transaction ID $categoryId from transactionList: ${task.exception?.message}")
                            }
                        }
                } else {
                    Log.e("DEBUGTEST", "Transaction ID $categoryId not found in transactionList.")
                }

                categoryRef.removeValue()
                    .addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            Log.d("DEBUGTEST", "Transaction $categoryId removed successfully.")
                        } else {
                            Log.e("DEBUGTEST", "Failed to remove : ${task.exception?.message}")
                        }
                    }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("DEBUGTEST", "Database error: ${error.message}")
            }
        })
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
            removeCategory(categoryId)
            finish()
        }
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
}

