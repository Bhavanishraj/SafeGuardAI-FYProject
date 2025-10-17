package com.example.safeguardai

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.telephony.SmsManager
import android.widget.Button
import android.widget.CheckBox
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

// Shake detection imports
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager

class MainActivity : AppCompatActivity() {

    private lateinit var fused: FusedLocationProviderClient
    private lateinit var etPhone: EditText
    private lateinit var tvStatus: TextView
    private lateinit var cbSendAll: CheckBox
    private lateinit var cbShakeDetect: CheckBox

    // Shake detection state
    private lateinit var sensorManager: SensorManager
    private var accelMagnitude = 0f
    private var accelCurrent = 0f
    private var accelLast = 0f
    private var shakeCount = 0
    private var windowStartMs = 0L
    private var lastTriggerMs = 0L

    // Select contact result
    private val pickContactLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val phone = result.data?.getStringExtra("selected_phone")
            val name = result.data?.getStringExtra("selected_name")
            phone?.let { etPhone.setText(it) }
            name?.let { toast("Selected contact: $name") }
            cbSendAll.isChecked = false // default to single-send after picking a contact
        }
    }

    // Permission launcher
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
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager

        etPhone = findViewById(R.id.etPhone)
        tvStatus = findViewById(R.id.tvStatus)
        cbSendAll = findViewById(R.id.cbSendAll)
        cbShakeDetect = findViewById(R.id.cbShakeDetect)

        // Load saved fallback number
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
            if (!hasAllPermissions(needed)) requestPermissions.launch(needed)
            else startSOS()
        }

        findViewById<Button>(R.id.btnContacts).setOnClickListener {
            pickContactLauncher.launch(Intent(this, ContactsActivity::class.java))
        }

        // Turn sensor on/off with the checkbox (saves battery)
        cbShakeDetect.setOnCheckedChangeListener { _, checked ->
            if (checked) registerShakeListener() else unregisterShakeListener()
        }
    }

    // =========================
    // SOS flow
    // =========================
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
                    routeMessage(msg)
                } else {
                    fused.lastLocation.addOnSuccessListener { last ->
                        if (last != null) {
                            val msg = buildMsg(last.latitude, last.longitude)
                            routeMessage(msg)
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

    private fun routeMessage(message: String) {
        if (cbSendAll.isChecked) {
            sendToAllContactsOnly(message)
        } else {
            val number = etPhone.text.toString().trim()
            if (number.isEmpty()) {
                tvStatus.text = "No number selected."
                toast("Pick a contact or enter a number first.")
            } else {
                sendSmsOrOpenComposer(number, message)
                tvStatus.text = "SOS sent to selected contact ✔"
            }
        }
    }

    private fun buildMsg(lat: Double, lon: Double): String {
        val link = "https://maps.google.com/?q=$lat,$lon"
        return "🚨 SOS! I need help. My location: $link"
    }

    private fun sendToAllContactsOnly(message: String) {
        lifecycleScope.launch {
            val dao = AppDatabase.get(this@MainActivity).contactDao()
            val list = dao.getAll().first()
            if (list.isEmpty()) {
                tvStatus.text = "No emergency contacts saved."
                toast("Add emergency contacts first.")
                return@launch
            }
            list.forEach { c -> sendSmsOrOpenComposer(c.phone, message) }
            tvStatus.text = "SOS sent to ${list.size} emergency contact(s) ✔"
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
            val uri = Uri.parse("smsto:$phone")
            val intent = Intent(Intent.ACTION_SENDTO, uri).apply { putExtra("sms_body", message) }
            startActivity(intent)
        }
    }

    // =========================
    // Shake detection
    // =========================
    private val shakeListener = object : SensorEventListener {
        override fun onSensorChanged(e: SensorEvent) {
            if (!cbShakeDetect.isChecked) return

            val x = e.values[0]
            val y = e.values[1]
            val z = e.values[2]

            val magnitude = kotlin.math.sqrt((x * x + y * y + z * z).toDouble()).toFloat()

            // high-pass filter to remove gravity
            accelLast = accelCurrent
            accelCurrent = magnitude
            val delta = accelCurrent - accelLast
            accelMagnitude = accelMagnitude * 0.9f + delta

            val now = SystemClock.elapsedRealtime()

            // tweak 11f–13f; higher = harder shake required
            val isStrongShake = kotlin.math.abs(accelMagnitude) > 12f

            // 1.5s window to count shakes
            if (windowStartMs == 0L || now - windowStartMs > 1500L) {
                windowStartMs = now
                shakeCount = 0
            }
            if (isStrongShake) shakeCount++

            // fire when 3 shakes detected; 6s cooldown
            if (shakeCount >= 3 && now - lastTriggerMs > 6000L) {
                lastTriggerMs = now
                shakeCount = 0
                tvStatus.text = "Shake detected — triggering SOS…"
                startSOS()
            }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }

    private fun registerShakeListener() {
        val acc = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        if (acc == null) {
            cbShakeDetect.isChecked = false
            toast("No accelerometer on this device")
            return
        }
        sensorManager.registerListener(
            shakeListener,
            acc,
            SensorManager.SENSOR_DELAY_GAME
        )
    }

    private fun unregisterShakeListener() {
        try { sensorManager.unregisterListener(shakeListener) } catch (_: Exception) {}
    }

    // =========================
    // Helpers
    // =========================
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

    // Respect lifecycle
    override fun onResume() {
        super.onResume()
        if (::cbShakeDetect.isInitialized && cbShakeDetect.isChecked) registerShakeListener()
    }

    override fun onPause() {
        super.onPause()
        unregisterShakeListener()
    }
}
