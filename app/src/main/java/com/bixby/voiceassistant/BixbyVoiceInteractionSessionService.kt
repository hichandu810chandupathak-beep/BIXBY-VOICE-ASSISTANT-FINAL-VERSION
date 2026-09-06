package com.bixby.voiceassistant

import android.content.Context
import android.content.Intent
import android.service.voice.VoiceInteractionSession
import android.service.voice.VoiceInteractionSessionService

/** Creates the assistant session used by Android's voice interaction framework. */
class BixbyVoiceInteractionSessionService : VoiceInteractionSessionService() {
    override fun onNewSession(args: android.os.Bundle?): VoiceInteractionSession {
        return BixbyVoiceInteractionSession(this)
    }
}

private class BixbyVoiceInteractionSession(context: android.content.Context) : android.service.voice.VoiceInteractionSession(context) {
    override fun onShow(args: android.os.Bundle?, showFlags: Int) {
        super.onShow(args, showFlags)
        val intent = android.content.Intent(context, MainActivity::class.java).apply {
            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        context.startActivity(intent)
    }
}
