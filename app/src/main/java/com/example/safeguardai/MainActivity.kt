package com.example.safeguardai

import android.Manifest
import android.content.Context
import android.content.Intent
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.telephony.SmsManager
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.safeguardai.data.AppDatabase
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import com.example.safeguardai.ContactsActivity


class MainActivity : AppCompatActivity() {

    private lateinit var fused: FusedLocationProviderClient
    private lateinit var etPhone: EditText
    private lateinit var tvStatus: TextView

    private val requestPermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grantMap ->
        val allGranted = grantMap.values.all { it }
        if (allGranted) startSOS() else toast("Permissions denied. Cannot send SOS.")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        fused = LocationServices.getFusedLocationProviderClient(this)

        etPhone = findViewById(R.id.etPhone)
        tvStatus = findViewById(R.id.tvStatus)

        // load saved fallback number (optional if user has no contacts yet)
        etPhone.setText(loadNumber())

        findViewById<Button>(R.id.btnSave).setOnClickListener {
            val number = etPhone.text.toString().trim()
            if (number.isEmpty()) toast("Enter a phone number")
            else {
                saveNumber(number)
                toast("Saved")
            }
        }

        findViewById<Button>(R.id.btnSOS).setOnClickListener {
            val needed = arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.SEND_SMS
            )
            if (!hasAllPermissions(needed)) requestPermissions.launch(needed) else startSOS()
        }

        findViewById<Button>(R.id.btnContacts).setOnClickListener {
          startActivity(Intent(this, ContactsActivity::class.java))
        }
    }

    private fun startSOS() {
        if (!isLocationEnabled()) {
            tvStatus.text = "Location is OFF. Please enable GPS."
            startActivity(Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS))
            return
        }

        tvStatus.text = "Getting location…"

        val cts = CancellationTokenSource()
        fused.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cts.token)
            .addOnSuccessListener { loc ->
                if (loc != null) {
                    val msg = buildMsg(loc.latitude, loc.longitude)
                    // send to contacts (or fallback)
                    sendToAllContactsOrFallback(msg)
                } else {
                    fused.lastLocation.addOnSuccessListener { last ->
                        if (last != null) {
                            val msg = buildMsg(last.latitude, last.longitude)
                            sendToAllContactsOrFallback(msg)
                        } else {
                            tvStatus.text = "Could not get location."
                        }
                    }.addOnFailureListener {
                        tvStatus.text = "Location error: ${it.localizedMessage}"
                    }
                }
            }
            .addOnFailureListener {
                tvStatus.text = "Location error: ${it.localizedMessage}"
            }
    }

    private fun buildMsg(lat: Double, lon: Double): String {
        val link = "https://maps.google.com/?q=$lat,$lon"
        return "🚨 SOS! I need help. My location: $link"
    }

    /** Sends to saved Room contacts; if none exist, sends to the single saved number EditText. */
    private fun sendToAllContactsOrFallback(message: String) {
        lifecycleScope.launch {
            val dao = AppDatabase.get(this@MainActivity).contactDao()
            val list = dao.getAll().first() // one-time snapshot
            if (list.isNotEmpty()) {
                list.forEach { c -> sendSmsOrOpenComposer(c.phone, message) }
                tvStatus.text = "SOS sent to ${list.size} contact(s) ✔"
            } else {
                val number = loadNumber()
                if (number.isNotEmpty()) {
                    sendSmsOrOpenComposer(number, message)
                    tvStatus.text = "SOS sent to saved number ✔"
                } else {
                    tvStatus.text = "No emergency contacts saved."
                    toast("Add contacts or save a number first.")
                }
            }
        }
    }

    private fun sendSmsOrOpenComposer(phone: String, message: String) {
        try {
            val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                getSystemService(SmsManager::class.java) else SmsManager.getDefault()
            val parts = smsManager.divideMessage(message)
            smsManager.sendMultipartTextMessage(phone, null, parts, null, null)
            toast("Message sent to $phone")
        } catch (_: Exception) {
            // Fallback to SMS app
            val uri = Uri.parse("smsto:$phone")
            val intent = Intent(Intent.ACTION_SENDTO, uri).apply { putExtra("sms_body", message) }
            startActivity(intent)
        }
    }

    // --- helpers ---

    private fun hasAllPermissions(perms: Array<String>) =
        perms.all {
            ContextCompat.checkSelfPermission(this, it) ==
                    android.content.pm.PackageManager.PERMISSION_GRANTED
        }

    private fun isLocationEnabled(): Boolean {
        val lm = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return lm.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }

    private fun saveNumber(n: String) =
        getSharedPreferences("sos", MODE_PRIVATE).edit().putString("phone", n).apply()

    private fun loadNumber(): String =
        getSharedPreferences("sos", MODE_PRIVATE).getString("phone", "") ?: ""

    private fun toast(msg: String) =
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
