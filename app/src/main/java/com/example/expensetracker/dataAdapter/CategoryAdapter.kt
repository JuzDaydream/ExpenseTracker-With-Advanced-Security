package com.example.expensetracker.dataAdapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.expensetracker.R
import com.example.expensetracker.data.Category

class CategoryAdapter(
    private var categoryList: List<Category>,
    private val onItemClick: (Category) -> Unit
) : RecyclerView.Adapter<CategoryAdapter.CategoryViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CategoryViewHolder {
        val itemView = LayoutInflater.from(parent.context).inflate(R.layout.category_item, parent, false)
        return CategoryViewHolder(itemView)
    }

    override fun onBindViewHolder(holder: CategoryViewHolder, position: Int) {
        val category = categoryList[position]
        holder.bind(category, onItemClick)
    }

    override fun getItemCount(): Int = categoryList.size

    fun updateList(newList: List<Category>) {
        categoryList = newList
        notifyDataSetChanged()
    }

    class CategoryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val icon: ImageView = itemView.findViewById(R.id.icon)
        private val category_name: TextView = itemView.findViewById(R.id.category_name)

        fun bind(category: Category, onItemClick: (Category) -> Unit) {
            val context = itemView.context
            val resourceId = context.resources.getIdentifier(category.icon, "drawable", context.packageName)
            icon.setImageResource(if (resourceId != 0) resourceId else R.drawable.icon_money)
            category_name.text = category.name

            itemView.setOnClickListener {
                onItemClick(category)
            }
        }
    }
}
