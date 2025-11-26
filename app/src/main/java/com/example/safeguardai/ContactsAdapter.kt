package com.example.safeguardai.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.safeguardai.R
import com.example.safeguardai.data.Contact

class ContactsAdapter(
    private var items: MutableList<Contact>,
    private val onItemClick: (Contact) -> Unit,          // ✅ Added click callback
    val onItemDelete: (Contact) -> Unit = {}
) : RecyclerView.Adapter<ContactsAdapter.VH>() {

    fun submit(newItems: List<Contact>) {
        items = newItems.toMutableList()
        notifyDataSetChanged()
    }

    fun getItemAt(position: Int) = items[position]

    inner class VH(v: View) : RecyclerView.ViewHolder(v) {
        val name: TextView = v.findViewById(R.id.tvName)
        val phone: TextView = v.findViewById(R.id.tvPhone)

        init {
            // ✅ Handle click for each list item
            v.setOnClickListener {
                val pos = bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) {
                    onItemClick(items[pos])
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_contact, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(h: VH, pos: Int) {
        val c = items[pos]
        h.name.text = c.name
        h.phone.text = c.phone
    }

    override fun getItemCount() = items.size
}
