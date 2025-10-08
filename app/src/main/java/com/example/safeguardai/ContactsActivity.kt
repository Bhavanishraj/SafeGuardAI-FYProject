package com.example.safeguardai

import android.os.Bundle
import android.text.InputType
import android.widget.EditText
import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.safeguardai.data.AppDatabase
import com.example.safeguardai.data.Contact
import com.example.safeguardai.ui.ContactsAdapter
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class ContactsActivity : AppCompatActivity() {

    private val dao by lazy { AppDatabase.get(this).contactDao() }
    private lateinit var adapter: ContactsAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // ✅ Use your exact layout name here
        setContentView(R.layout.activitiescontact)
        supportActionBar?.title = "Emergency Contacts"

        val rv = findViewById<RecyclerView>(R.id.rvContacts)
        rv.layoutManager = LinearLayoutManager(this)
        adapter = ContactsAdapter(mutableListOf()) { /* swipe handles delete */ }
        rv.adapter = adapter

        // ✅ Swipe to delete contacts
        val swipe = object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ) = false

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val contact = adapter.getItemAt(viewHolder.bindingAdapterPosition)
                lifecycleScope.launch { dao.delete(contact) }
            }
        }
        ItemTouchHelper(swipe).attachToRecyclerView(rv)

        // ✅ Add new contact button
        findViewById<com.google.android.material.floatingactionbutton.FloatingActionButton>(R.id.fabAdd)
            .setOnClickListener { showAddDialog() }

        // ✅ Observe database changes in real-time
        lifecycleScope.launch {
            dao.getAll().collectLatest { contacts -> adapter.submit(contacts) }
        }
    }

    private fun showAddDialog() {
        val nameInput = EditText(this).apply { hint = "Name" }
        val phoneInput = EditText(this).apply {
            hint = "Phone (with country code)"
            inputType = InputType.TYPE_CLASS_PHONE
        }

        // ✅ Container for input fields
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = dp(16)
            setPadding(pad, pad, pad, 0)
            addView(nameInput)
            addView(phoneInput)
        }

        // ✅ Build and show the dialog
        AlertDialog.Builder(this)
            .setTitle("Add Emergency Contact")
            .setView(container)
            .setPositiveButton("Save") { _, _ ->
                val name = nameInput.text.toString().trim()
                val phone = phoneInput.text.toString().trim()
                if (name.isNotEmpty() && phone.isNotEmpty()) {
                    lifecycleScope.launch { dao.insert(Contact(name = name, phone = phone)) }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
