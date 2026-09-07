package com.bixby.voiceassistant
import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Bundle
import android.service.voice.VoiceInteractionService
import android.service.voice.VoiceInteractionSession
import android.service.voice.VoiceInteractionSessionService
import android.view.accessibility.AccessibilityEvent

// 1. The Core Assistant Service (Tells Android this is a Voice Assistant)
class BixbyVoiceInteractionService : VoiceInteractionService()

// 2. The Session Service (Handles the actual Assistant popup session)
class BixbySessionService : VoiceInteractionSessionService() {
    override fun onNewSession(args: Bundle?): VoiceInteractionSession {
        return object : VoiceInteractionSession(this) {
            override fun onShow(args: Bundle?, showFlags: Int) {
                super.onShow(args, showFlags)
                // When triggered by Home Button, launch the Bixby UI Overlay
                val intent = Intent(context, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    putExtra("trigger_source", "hardware_button")
                }
                context.startActivity(intent)
            }
        }
    }
}

// 3. Dummy Recognition Service to satisfy Android System Requirements
class BixbyRecognitionService : android.speech.RecognitionService() {
    override fun onStartListening(recognizerIntent: Intent?, listener: android.speech.RecognitionService.Callback?) {}
    override fun onCancel(listener: android.speech.RecognitionService.Callback?) {}
    override fun onStopListening(listener: android.speech.RecognitionService.Callback?) {}
}

// 4. Accessibility Service (Intercepts physical Power Button on One UI)
class BixbyAccessibilityService : AccessibilityService() {
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra("trigger_source", "hardware_button")
        }
        startActivity(launchIntent)
        return super.onStartCommand(intent, flags, startId)
    }
}
