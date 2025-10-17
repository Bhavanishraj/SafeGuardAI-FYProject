package com.example.safeguardai

import android.app.Activity
import android.content.Intent
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
        setContentView(R.layout.activitiescontact)
        supportActionBar?.title = "Emergency Contacts"

        val rv = findViewById<RecyclerView>(R.id.rvContacts)
        rv.layoutManager = LinearLayoutManager(this)

        // Adapter now has onItemClick: return selected contact to MainActivity
        adapter = ContactsAdapter(
            mutableListOf(),
            onItemClick = { contact ->
                val data = Intent().apply {
                    putExtra("selected_phone", contact.phone)
                    putExtra("selected_name", contact.name)
                }
                setResult(Activity.RESULT_OK, data)
                finish()
            }
        )
        rv.adapter = adapter

        // Swipe to delete
        val swipe = object :
            ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {
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

        // FAB add
        findViewById<com.google.android.material.floatingactionbutton.FloatingActionButton>(R.id.fabAdd)
            .setOnClickListener { showAddDialog() }

        // Observe DB changes
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

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = dp(16)
            setPadding(pad, pad, pad, 0)
            addView(nameInput)
            addView(phoneInput)
        }

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
