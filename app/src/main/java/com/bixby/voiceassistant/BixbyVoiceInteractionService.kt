package com.bixby.voiceassistant

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.service.voice.VoiceInteractionService
import androidx.core.content.ContextCompat

/** Android Assistant entry point for Bixby. */
class BixbyVoiceInteractionService : VoiceInteractionService() {
    override fun onReady() {
        super.onReady()
        android.util.Log.d("BixbyLifecycle", "VoiceInteractionService ready; listening remains disabled until explicit user trigger")
    }

    override fun onLaunchVoiceAssistFromKeyguard() {
        try {
            val micGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
            val notificationGranted = Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            if (!micGranted || !notificationGranted) return
            val intent = Intent(this, MainActivity::class.java).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP) }
            startActivity(intent)
        } catch (e: Exception) { android.util.Log.e("BixbyCrash", "Failed to launch assistant from keyguard", e) }
    }
}
