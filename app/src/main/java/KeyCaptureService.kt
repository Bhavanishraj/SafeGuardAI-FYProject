package com.example.safeguardai

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent

class KeyCaptureService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private var longPressRunnable: Runnable? = null
    private var downTime: Long = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()

        val info = AccessibilityServiceInfo().apply {
            // Let this service receive hardware key events
            flags = AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS

            // Optional: if you also want to react to basic UI events
            eventTypes = AccessibilityEvent.TYPE_VIEW_CLICKED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
        }
        serviceInfo = info
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Not used for key-only handling, but must be implemented
    }

    override fun onInterrupt() { /* no-op */ }

    override fun onKeyEvent(event: KeyEvent?): Boolean {
        event ?: return false

        // Example: long press on VOLUME_DOWN to trigger fake call
        if (event.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            when (event.action) {
                KeyEvent.ACTION_DOWN -> {
                    downTime = SystemClock.uptimeMillis()
                    // consider long press if held ~1200ms
                    longPressRunnable = Runnable {
                        launchFakeCall("+91 9876543210", "Mom")
                    }
                    handler.postDelayed(longPressRunnable!!, 1200L)
                }
                KeyEvent.ACTION_UP -> {
                    handler.removeCallbacks(longPressRunnable!!)
                }
            }
            // consume to avoid changing system volume during hold
            return true
        }

        return false
    }

    private fun launchFakeCall(number: String, name: String) {
        val intent = Intent(this, FakeCallActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra("caller_name", name)
            putExtra("caller_number", number)
        }
        startActivity(intent)
    }
}
