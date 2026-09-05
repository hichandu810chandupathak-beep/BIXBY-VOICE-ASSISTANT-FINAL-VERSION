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
        try {
            val onboardingComplete = getSharedPreferences("bixby_onboarding", MODE_PRIVATE).getBoolean("completed", false)
            val micGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
            val notificationGranted = Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            if (onboardingComplete && micGranted && notificationGranted) HotwordListeningService.start(this)
            else android.util.Log.d("BixbyLifecycle", "VoiceInteractionService ready; listening deferred until onboarding/permissions are complete")
        } catch (e: Exception) {
            android.util.Log.e("BixbyLifecycle", "VoiceInteractionService initialization failed", e)
        }
    }

    override fun onLaunchVoiceAssistFromKeyguard() {
        try {
            val intent = Intent(this, MainActivity::class.java).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP) }
            startActivity(intent)
        } catch (e: Exception) { android.util.Log.e("BixbyLifecycle", "Failed to launch assistant from keyguard", e) }
    }
}
