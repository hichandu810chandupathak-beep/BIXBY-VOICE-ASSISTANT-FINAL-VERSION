package com.bixby.voiceassistant

import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.provider.Settings
import java.util.Locale

object CommandExecutor {
    fun execute(context: Context, raw: String): String? {
        val text = raw.trim().lowercase(Locale.ROOT)

        if (text.contains("flashlight") || text.contains("torch") || text.contains("फ्लैशलाइट") || text.contains("टॉर्च")) {
            return try {
                val camera = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
                val id = camera.cameraIdList.firstOrNull { info ->
                    camera.getCameraCharacteristics(info).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
                } ?: return "इस फोन पर फ्लैशलाइट उपलब्ध नहीं है।"
                val state = text.contains("off") || text.contains("बंद")
                camera.setTorchMode(id, !state)
                if (state) "फ्लैशलाइट बंद कर दी।" else "फ्लैशलाइट चालू कर दी।"
            } catch (_: Exception) {
                "फ्लैशलाइट बदल नहीं पाई।"
            }
        }

        val app = when {
            text.contains("youtube") -> "com.google.android.youtube"
            text.contains("chrome") -> "com.android.chrome"
            text.contains("whatsapp") -> "com.whatsapp"
            text.contains("instagram") -> "com.instagram.android"
            text.contains("maps") || text.contains("google maps") -> "com.google.android.apps.maps"
            else -> null
        }
        if (app != null && (text.contains("open") || text.contains("खोल") || text.contains("चलाओ"))) {
            val launch = context.packageManager.getLaunchIntentForPackage(app)
            if (launch != null) {
                launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launch)
                return "ठीक है, खोल रहा हूँ।"
            }
            return "वह ऐप इस फोन में नहीं मिला।"
        }

        if (text.contains("settings") || text.contains("setting") || text.contains("सेटिंग")) {
            context.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return "सेटिंग्स खोल रहा हूँ।"
        }

        if (text.contains("volume up") || text.contains("volume बढ़ा") || text.contains("आवाज़ बढ़ा")) {
            val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            audio.adjustVolume(AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
            return "आवाज़ बढ़ा दी।"
        }

        if (text.contains("volume down") || text.contains("volume कम") || text.contains("आवाज़ कम")) {
            val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            audio.adjustVolume(AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
            return "आवाज़ कम कर दी।"
        }

        if (text.startsWith("call ") || text.startsWith("कॉल ")) {
            val number = raw.replaceFirst(Regex("(?i)^\\s*(call|कॉल)\\s*"), "").trim()
            if (number.isNotBlank()) {
                context.startActivity(Intent(Intent.ACTION_DIAL).apply {
                    data = android.net.Uri.parse("tel:${number.replace(" ", "")}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                })
                return "कॉल स्क्रीन खोल रहा हूँ।"
            }
        }

        if (text.contains("alarm") || text.contains("अलार्म")) {
            context.startActivity(Intent(android.provider.AlarmClock.ACTION_SET_ALARM).apply {
                putExtra(android.provider.AlarmClock.EXTRA_SKIP_UI, false)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
            return "अलार्म सेट करने की स्क्रीन खोल रहा हूँ।"
        }

        return null
    }
}
