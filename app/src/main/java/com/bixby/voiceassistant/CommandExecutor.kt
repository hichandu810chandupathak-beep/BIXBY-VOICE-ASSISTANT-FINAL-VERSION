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
        if (text.isBlank()) return null

        // Device navigation / global actions.
        when {
            text == "home" || text.contains("go home") || text.contains("होम स्क्रीन") || text == "होम" ->
                return global(context, android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME, "होम स्क्रीन खोल दी।")
            text == "back" || text.contains("go back") || text.contains("वापस") || text.contains("पीछे जाओ") ->
                return global(context, android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK, "वापस चला गया।")
            text.contains("recent apps") || text.contains("recents") || text.contains("हाल के ऐप") ->
                return global(context, android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_RECENTS, "हाल के ऐप खोल दिए।")
            text.contains("notifications") || text.contains("notification panel") || text.contains("नोटिफिकेशन") ->
                return global(context, android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS, "नोटिफिकेशन खोल दिए।")
            text.contains("quick settings") || text.contains("quick panel") || text.contains("क्विक सेटिंग") ->
                return global(context, android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS, "क्विक सेटिंग्स खोल दीं।")
            text.contains("power menu") || text.contains("power dialog") || text.contains("पावर मेन्यू") ->
                return global(context, android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_POWER_DIALOG, "पावर मेन्यू खोल रहा हूँ।")
            text.contains("lock screen") || text.contains("फोन लॉक") || text.contains("स्क्रीन लॉक") ->
                return global(context, android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN, "फोन लॉक कर दिया।")
        }

        // Accessibility-backed UI actions.
        if (text.contains("scroll down") || text.contains("नीचे स्क्रॉल") || text.contains("नीचे करो")) {
            return accessibilityScroll(true)
        }
        if (text.contains("scroll up") || text.contains("ऊपर स्क्रॉल") || text.contains("ऊपर करो")) {
            return accessibilityScroll(false)
        }

        // Type text into the currently focused/editable field.
        val typeMatch = Regex("(?:type|enter|write|input|लिखो|लिखें|टाइप|डालो|भरें)\\s+(.+)", RegexOption.IGNORE_CASE)
            .find(raw)
        if (typeMatch != null) {
            val value = typeMatch.groupValues[1].trim()
            if (!AccessibilityCommandService.isEnabled()) return "फोन कंट्रोल के लिए Accessibility Service को एक बार चालू करना होगा।"
            if (value.isBlank()) return "लिखने के लिए टेक्स्ट नहीं मिला।"
            return if (AccessibilityCommandService.setText(value)) "ठीक है, टेक्स्ट डाल दिया।" else "अभी टेक्स्ट वाले बॉक्स में नहीं लिख पाया।"
        }

        val clickTarget = Regex("(?:click|tap|press|क्लिक|टैप|दबाओ)\\s+(.+)", RegexOption.IGNORE_CASE)
            .find(raw)?.groupValues?.getOrNull(1)?.trim()
        if (!clickTarget.isNullOrBlank()) {
            if (!AccessibilityCommandService.isEnabled()) return "फोन कंट्रोल के लिए Accessibility Service को एक बार चालू करना होगा।"
            return if (AccessibilityCommandService.clickText(clickTarget)) "ठीक है, कर दिया।" else "वह बटन या विकल्प नहीं मिला।"
        }

        // Flashlight.
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

        // Volume.
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        if (text.contains("volume up") || text.contains("volume बढ़ा") || text.contains("आवाज़ बढ़ा")) {
            audio.adjustVolume(AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
            return "आवाज़ बढ़ा दी।"
        }
        if (text.contains("volume down") || text.contains("volume कम") || text.contains("आवाज़ कम")) {
            audio.adjustVolume(AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
            return "आवाज़ कम कर दी।"
        }
        // Check unmute before mute because "unmute" contains "mute".
        if (text.contains("unmute") || text.contains("म्यूट हटाओ")) {
            audio.adjustVolume(AudioManager.ADJUST_UNMUTE, AudioManager.FLAG_SHOW_UI)
            return "म्यूट हटा दिया।"
        }
        if (text.contains("mute") || text.contains("silent") || text.contains("म्यूट") || text.contains("साइलेंट")) {
            audio.adjustVolume(AudioManager.ADJUST_MUTE, AudioManager.FLAG_SHOW_UI)
            return "आवाज़ म्यूट कर दी।"
        }

        // Common settings destinations.
        if (text.contains("wifi") || text.contains("वाईफाई")) {
            open(context, Intent(Settings.ACTION_WIFI_SETTINGS), "वाई-फाई सेटिंग्स खोल रहा हूँ।")?.let { return it }
        }
        if (text.contains("bluetooth") || text.contains("ब्लूटूथ")) {
            open(context, Intent(Settings.ACTION_BLUETOOTH_SETTINGS), "ब्लूटूथ सेटिंग्स खोल रहा हूँ।")?.let { return it }
        }
        if (text.contains("display settings") || text.contains("डिस्प्ले सेटिंग")) {
            open(context, Intent(Settings.ACTION_DISPLAY_SETTINGS), "डिस्प्ले सेटिंग्स खोल रहा हूँ।")?.let { return it }
        }
        if (text.contains("sound settings") || text.contains("sound") || text.contains("साउंड")) {
            open(context, Intent(Settings.ACTION_SOUND_SETTINGS), "साउंड सेटिंग्स खोल रहा हूँ।")?.let { return it }
        }
        if (text.contains("settings") || text.contains("setting") || text.contains("सेटिंग")) {
            open(context, Intent(Settings.ACTION_SETTINGS), "सेटिंग्स खोल रहा हूँ।")?.let { return it }
        }

        // Launch installed apps by spoken display name, not only hardcoded package IDs.
        val openMatch = Regex("(?:open|launch|start|खोलो|खोल|चलाओ|चालू करो)\\s+(.+)", RegexOption.IGNORE_CASE).find(raw)
        if (openMatch != null) {
            val requested = openMatch.groupValues[1].trim()
            val packageName = findInstalledApp(context, requested)
            if (packageName != null) {
                val launch = context.packageManager.getLaunchIntentForPackage(packageName)
                if (launch != null) {
                    launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(launch)
                    return "ठीक है, $requested खोल रहा हूँ।"
                }
            }
            return "$requested नाम का ऐप इस फोन में नहीं मिला।"
        }

        // Dial/call screen.
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

    private fun global(context: Context, action: Int, success: String): String {
        if (!AccessibilityCommandService.isEnabled()) return "फोन कंट्रोल के लिए Accessibility Service को एक बार चालू करना होगा।"
        return if (AccessibilityCommandService.global(action)) success else "यह फोन action अभी नहीं कर पाया।"
    }

    private fun accessibilityScroll(forward: Boolean): String {
        if (!AccessibilityCommandService.isEnabled()) return "फोन कंट्रोल के लिए Accessibility Service को एक बार चालू करना होगा।"
        return if (AccessibilityCommandService.scroll(forward)) "स्क्रॉल कर दिया।" else "अभी स्क्रॉल नहीं कर पाया।"
    }

    private fun open(context: Context, intent: Intent, message: String): String? {
        return try {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            message
        } catch (_: Exception) {
            null
        }
    }

    private fun findInstalledApp(context: Context, spokenName: String): String? {
        val pm = context.packageManager
        val normalized = spokenName.trim().lowercase(Locale.ROOT)
        val apps = pm.getInstalledApplications(0)
        return apps.firstOrNull {
            val label = pm.getApplicationLabel(it).toString().lowercase(Locale.ROOT)
            label == normalized || label.contains(normalized) || normalized.contains(label)
        }?.packageName
    }
}
