package com.bixby.voiceassistant
import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent

class BixbyAccessibilityService : AccessibilityService() {
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}
    
    // SURGICAL FIX 4: Intercepts Hardware Assistant Requests via Accessibility
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra("start_voice_after_welcome", true)
        }
        startActivity(launchIntent)
        return super.onStartCommand(intent, flags, startId)
    }
}