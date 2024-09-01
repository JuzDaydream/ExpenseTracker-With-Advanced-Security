package com.example.expensetracker

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.expensetracker.data.Category
import com.example.expensetracker.dataAdapter.CategoryAdapter
import com.example.expensetracker.databinding.FragmentCategoryBinding
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

class CategoryFragment : Fragment() {
    private lateinit var binding: FragmentCategoryBinding
    private val categoryList = ArrayList<Category>()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        binding = FragmentCategoryBinding.inflate(inflater, container, false)

        // Initialize RecyclerView with LayoutManager
        binding.recyclerCategory.layoutManager = LinearLayoutManager(context)
        binding.iconBack.setOnClickListener {
            requireActivity().supportFragmentManager.popBackStack()
        }

        binding.btnAddCategory.setOnClickListener {
            val intent = Intent(requireContext(), AddCategoryActivity::class.java)
            startActivity(intent)
        }



        return binding.root
    }

    private fun fetchCategories() {
        // Initialize Firebase Database reference
        val categoryDB: DatabaseReference = FirebaseDatabase.getInstance("https://expensetracker-a260c-default-rtdb.asia-southeast1.firebasedatabase.app")
            .getReference("Category")

        // Initialize the adapter inside fetchCategories and set it to the RecyclerView
        val categoryAdapter = CategoryAdapter(categoryList) { category ->
            // Handle item click
            val intent = Intent(requireContext(), EditCategoryActivity::class.java)
            intent.putExtra("categoryID", category.id) // Pass the categoryID
            startActivity(intent)
        }
        binding.recyclerCategory.adapter = categoryAdapter

        // Fetch categories from the database
        categoryDB.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                try {
                    categoryList.clear()
                    for (dataSnapshot in snapshot.children) {
                        val category = dataSnapshot.getValue(Category::class.java)
                        if (category != null) {
                            categoryList.add(category)
                        } else {
                            Log.e("CategoryFragment", "Category is null: $dataSnapshot")
                        }
                    }
                    categoryList.sortBy { it.name }
                    categoryAdapter.notifyDataSetChanged()
                } catch (e: Exception) {
                    Log.e("CategoryFragment", "Error processing categories", e)
                }
            }

            override fun onCancelled(error: DatabaseError) {
                // Log the error
                Log.e("CategoryFragment", "Database error: ${error.message}")
            }
        })
    }
    override fun onResume() {
        super.onResume()
        fetchCategories()
    }
}
