package com.example.expensetracker

import android.app.Dialog
import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AbsListView
import android.widget.BaseAdapter
import android.widget.GridView
import android.widget.ImageView
import androidx.appcompat.app.AlertDialog
import com.example.expensetracker.databinding.ActivityAddCategoryBinding
import com.google.firebase.database.FirebaseDatabase

class AddCategoryActivity : AppCompatActivity() {
    private lateinit var binding:ActivityAddCategoryBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddCategoryBinding.inflate(layoutInflater)
        binding.iconBack.setOnClickListener { finish() }

        binding.editImage.setOnClickListener{
            showIconSelectionDialog()
        }

        binding.btnCreate.setOnClickListener {
            getNextCategoryID { newId ->
                Log.d("DEBUGTEST", "Generated New ID: $newId")
                saveCategory(newId)
            }
        }

        setContentView(binding.root)
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
                // For example, you could set a default image or show an error
                binding.iconImage.setImageResource(R.drawable.icon_money)
            }
            // Dismiss the dialog
            dialog.dismiss()
        }
        // Show the dialog
        dialog.show()
    }

    private fun saveCategory(newId: String) {
        Log.d("DEBUGTEST", "saveCategory method called with ID: $newId")

        // Retrieve category details from input fields
        val name = binding.editName.text.toString()
        val icon = binding.editImage.text.toString()

        if (name.isNotEmpty() && icon.isNotEmpty()) {
            val categoryRef = FirebaseDatabase.getInstance("https://expensetracker-a260c-default-rtdb.asia-southeast1.firebasedatabase.app")
                .getReference("Category")

            // Create a new Category object
            val category = mapOf(
                "id" to newId,
                "name" to name,
                "icon" to icon
            )

            // Save the category in Firebase
            categoryRef.child(newId).setValue(category).addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    // Successfully saved the category
                    Log.d("DEBUGTEST", "Category saved successfully")
                    finish() // Close the activity or fragment after successful record
                } else {
                    // Log the error
                    Log.e("DEBUGTEST", "Error saving category: ${task.exception?.message}")
                }
            }
        } else {
            // Handle missing fields
            binding.tvErrorMsg.setText("Please fill all required fields")
        }
    }


    private fun getNextCategoryID(callback: (String) -> Unit) {
        val categoryRef = FirebaseDatabase.getInstance("https://expensetracker-a260c-default-rtdb.asia-southeast1.firebasedatabase.app").getReference("Category")

        categoryRef.get().addOnSuccessListener { snapshot ->
            if (snapshot.exists()) {
                // Extract and parse IDs
                val ids = snapshot.children.mapNotNull {
                    it.child("id").getValue(String::class.java)
                }

                // Extract numeric part from IDs
                val maxId = ids.mapNotNull { id ->
                    id.replace("C", "").toIntOrNull()
                }.maxOrNull() ?: 0

                // Generate new ID
                val newId = "C${maxId + 1}"

                // Invoke callback with the new ID
                callback(newId)
            } else {
                // Handle case where there are no categories yet
                val newId = "C1"
                callback(newId)
            }
        }.addOnFailureListener { exception ->
            Log.e("AddCategoryActivity", "Error fetching categories: ${exception.message}", exception)
        }
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