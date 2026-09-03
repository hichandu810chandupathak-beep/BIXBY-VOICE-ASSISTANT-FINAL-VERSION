package com.bixby.voiceassistant

import android.content.Context
import android.service.voice.VoiceInteractionSession
import android.service.voice.VoiceInteractionSessionService

/** Creates the assistant session used by Android's voice interaction framework. */
class BixbyVoiceInteractionSessionService : VoiceInteractionSessionService() {
    override fun onNewSession(args: android.os.Bundle?): VoiceInteractionSession {
        return BixbyVoiceInteractionSession(this)
    }
}

private class BixbyVoiceInteractionSession(context: Context) : VoiceInteractionSession(context)
