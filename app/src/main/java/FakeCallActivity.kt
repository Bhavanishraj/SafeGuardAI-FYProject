package com.example.safeguardai

import android.media.RingtoneManager
import android.media.Ringtone
import android.os.Bundle
import android.os.Vibrator
import android.os.VibrationEffect
import androidx.appcompat.app.AppCompatActivity
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import android.content.Intent

class FakeCallActivity : AppCompatActivity() {

    private var ringtone: Ringtone? = null
    private var vibrator: Vibrator? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // show full screen over lock
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                or WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)

        setContentView(R.layout.activity_fake_call) // create layout below

        val callerName = intent.getStringExtra("caller_name") ?: "Caller"
        val callerNumber = intent.getStringExtra("caller_number") ?: ""

        findViewById<TextView>(R.id.tvCallerName).text = callerName
        findViewById<TextView>(R.id.tvCallerNumber).text = callerNumber

        // Play ringtone
        val ringtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        ringtone = RingtoneManager.getRingtone(this, ringtoneUri)
        ringtone?.play()

        // Vibrate repeatedly
        vibrator = getSystemService(VIBRATOR_SERVICE) as Vibrator
        if (vibrator?.hasVibrator() == true) {
            val pattern = longArrayOf(0, 1000, 500) // wait, vibrate, pause
            // VibrationEffect required on API 26+
            val effect = VibrationEffect.createWaveform(pattern, 0) // repeat index 0
            vibrator?.vibrate(effect)
        }

        findViewById<Button>(R.id.btnDismiss).setOnClickListener {
            stopAndFinish()
        }

        findViewById<Button>(R.id.btnAnswer).setOnClickListener {
            // optionally simulate call answer: stop and show UI or dialer
            stopAndFinish()
        }
    }

    private fun stopAndFinish() {
        ringtone?.stop()
        vibrator?.cancel()
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        ringtone?.stop()
        vibrator?.cancel()
    }

    override fun onBackPressed() {
        // block back if you want (or allow)
        stopAndFinish()
    }
}
