package com.bixby.voiceassistant
import android.content.Context
import android.content.Intent
import android.service.voice.VoiceInteractionSession
import android.service.voice.VoiceInteractionSessionService

class BixbyVoiceInteractionSessionService : VoiceInteractionSessionService() {
    override fun onNewSession(args: android.os.Bundle?): VoiceInteractionSession = BixbyVoiceInteractionSession(this)
}

private class BixbyVoiceInteractionSession(context: Context) : VoiceInteractionSession(context) {
    override fun onShow(args: android.os.Bundle?, showFlags: Int) {
        super.onShow(args, showFlags)
        try {
            val intent = Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            startVoiceActivity(intent)
        } catch (e: Exception) { android.util.Log.e("Bixby", "Start failed", e) }
    }
}