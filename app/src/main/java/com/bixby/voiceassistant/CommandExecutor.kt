package com.bixby.voiceassistant

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.provider.Settings
import java.util.Locale

object CommandExecutor {
    private var flashlightOn = false

    fun execute(context: Context, raw: String): String? {
        val original = raw.trim()
        val text = original.lowercase(Locale.ROOT).replace(Regex("\\s+"), " ").trim()
        if (text.isBlank()) return null

        when {
            text == "home" || text.contains("go home") || text.contains("home screen") || text.contains("होम स्क्रीन") || text == "होम" ->
                return global(context, AccessibilityService.GLOBAL_ACTION_HOME, "होम स्क्रीन खोल दी।")
            text == "back" || text.contains("go back") || text.contains("वापस") || text.contains("पीछे जाओ") ->
                return global(context, AccessibilityService.GLOBAL_ACTION_BACK, "वापस चला गया।")
            text.contains("recent apps") || text.contains("recents") || text.contains("हाल के ऐप") || text.contains("रीसेंट") ->
                return global(context, AccessibilityService.GLOBAL_ACTION_RECENTS, "हाल के ऐप खोल दिए।")
            text.contains("notifications") || text.contains("notification panel") || text.contains("नोटिफिकेशन") ->
                return global(context, AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS, "नोटिफिकेशन खोल दिए।")
            text.contains("quick settings") || text.contains("quick panel") || text.contains("क्विक सेटिंग") ->
                return global(context, AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS, "क्विक सेटिंग्स खोल दीं।")
            text.contains("power menu") || text.contains("power dialog") || text.contains("पावर मेन्यू") ->
                return global(context, AccessibilityService.GLOBAL_ACTION_POWER_DIALOG, "पावर मेन्यू खोल रहा हूँ।")
            text.contains("lock screen") || text.contains("phone lock") || text.contains("फोन लॉक") || text.contains("स्क्रीन लॉक") ->
                return global(context, AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN, "फोन लॉक कर दिया।")
            isCloseCommand(text) ->
                return global(context, AccessibilityService.GLOBAL_ACTION_HOME, "ऐप बंद करके होम स्क्रीन पर आ गया।")
        }

        if (text.contains("scroll down") || text.contains("swipe down") || text.contains("नीचे स्क्रॉल") || text.contains("नीचे करो"))
            return accessibilityScroll(true)
        if (text.contains("scroll up") || text.contains("swipe up") || text.contains("ऊपर स्क्रॉल") || text.contains("ऊपर करो"))
            return accessibilityScroll(false)

        val typeMatch = Regex("(?:type|enter|write|input|लिखो|लिखें|टाइप|डालो|भरें)\\s+(.+)", RegexOption.IGNORE_CASE).find(original)
        if (typeMatch != null) {
            val value = typeMatch.groupValues[1].trim()
            if (!AccessibilityCommandService.isEnabled()) return accessibilityRequired()
            return if (value.isNotBlank() && AccessibilityCommandService.setText(value)) "ठीक है, टेक्स्ट डाल दिया।"
            else "अभी टेक्स्ट वाले बॉक्स में नहीं लिख पाया।"
        }

        val clickTarget = Regex("(?:click|tap|press|क्लिक|टैप|दबाओ)\\s+(.+)", RegexOption.IGNORE_CASE).find(original)?.groupValues?.getOrNull(1)?.trim()
        if (!clickTarget.isNullOrBlank()) {
            if (!AccessibilityCommandService.isEnabled()) return accessibilityRequired()
            return if (AccessibilityCommandService.clickText(clickTarget)) "ठीक है, कर दिया।" else "वह बटन या विकल्प नहीं मिला।"
        }

        val flashlightRequested = containsAny(text, "flashlight", "torch", "फ्लैशलाइट", "टॉर्च")
        val flashlightOff = containsAny(text, "turn off", "switch off", "shut off", "बंद", "ऑफ")
        val flashlightOnRequest = containsAny(text, "turn on", "switch on", "enable", "चालू", "ऑन")
        val flashlightFollowUpOff = flashlightOn && isSimpleOffFollowUp(text)
        if (flashlightRequested || flashlightFollowUpOff) {
            return setFlashlight(context, !(flashlightOff || flashlightFollowUpOff || !flashlightOnRequest && flashlightRequested && text.endsWith("off")))
        }

        val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        if (containsAny(text, "volume up", "increase volume", "louder", "volume बढ़ा", "आवाज़ बढ़ा", "आवाज बढ़ा")) {
            audio.adjustVolume(AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
            return "आवाज़ बढ़ा दी।"
        }
        if (containsAny(text, "volume down", "decrease volume", "quieter", "volume कम", "आवाज़ कम", "आवाज कम")) {
            audio.adjustVolume(AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
            return "आवाज़ कम कर दी।"
        }
        if (containsAny(text, "unmute", "म्यूट हटाओ", "आवाज़ चालू", "आवाज चालू")) {
            audio.adjustVolume(AudioManager.ADJUST_UNMUTE, AudioManager.FLAG_SHOW_UI)
            return "म्यूट हटा दिया।"
        }
        if (containsAny(text, "mute", "silent", "म्यूट", "साइलेंट")) {
            audio.adjustVolume(AudioManager.ADJUST_MUTE, AudioManager.FLAG_SHOW_UI)
            return "आवाज़ म्यूट कर दी।"
        }

        val wifi = containsAny(text, "wifi", "wi-fi", "वाईफाई", "वाई-फाई")
        val bluetooth = containsAny(text, "bluetooth", "ब्लूटूथ")
        val turnOn = containsAny(text, "turn on", "switch on", "enable", "चालू", "ऑन")
        val turnOff = containsAny(text, "turn off", "switch off", "disable", "बंद", "ऑफ")

        // "Turn on/off Wi-Fi/Bluetooth" is an action command, not a Settings navigation command.
        if (wifi && (turnOn || turnOff)) {
            if (!AccessibilityCommandService.isEnabled()) return accessibilityRequired()
            val enabled = turnOn && !turnOff
            AccessibilityCommandService.setSystemTile("Wi-Fi", enabled)
            return if (enabled) "वाई-फाई चालू कर रहा हूँ।" else "वाई-फाई बंद कर रहा हूँ।"
        }
        if (bluetooth && (turnOn || turnOff)) {
            if (!AccessibilityCommandService.isEnabled()) return accessibilityRequired()
            val enabled = turnOn && !turnOff
            AccessibilityCommandService.setSystemTile("Bluetooth", enabled)
            return if (enabled) "ब्लूटूथ चालू कर रहा हूँ।" else "ब्लूटूथ बंद कर रहा हूँ।"
        }

        // Settings navigation remains available only when the user explicitly asks for settings.
        if (containsAny(text, "wifi settings", "wi-fi settings", "वाईफाई सेटिंग", "वाई-फाई सेटिंग"))
            return open(context, Intent(Settings.ACTION_WIFI_SETTINGS), "वाई-फाई सेटिंग्स खोल रहा हूँ।")
        if (containsAny(text, "bluetooth settings", "ब्लूटूथ सेटिंग"))
            return open(context, Intent(Settings.ACTION_BLUETOOTH_SETTINGS), "ब्लूटूथ सेटिंग्स खोल रहा हूँ।")
        if (containsAny(text, "display settings", "screen settings", "डिस्प्ले सेटिंग", "स्क्रीन सेटिंग"))
            return open(context, Intent(Settings.ACTION_DISPLAY_SETTINGS), "डिस्प्ले सेटिंग्स खोल रहा हूँ।")
        if (containsAny(text, "sound settings", "audio settings", "साउंड सेटिंग", "ऑडियो सेटिंग"))
            return open(context, Intent(Settings.ACTION_SOUND_SETTINGS), "साउंड सेटिंग्स खोल रहा हूँ।")
        if (containsAny(text, "settings", "setting", "सेटिंग", "सेटिंग्स"))
            return open(context, Intent(Settings.ACTION_SETTINGS), "सेटिंग्स खोल रहा हूँ।")

        val openMatch = Regex("(?:open|launch|start|run|खोलो|खोल|चलाओ|चलाएं|चालू करो)\\s+(.+)", RegexOption.IGNORE_CASE).find(original)
        if (openMatch != null) {
            val requested = openMatch.groupValues[1].trim()
            val packageName = findInstalledApp(context, requested)
            if (packageName != null) {
                val launch = context.packageManager.getLaunchIntentForPackage(packageName)
                if (launch != null) {
                    launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    return try {
                        context.startActivity(launch)
                        "ठीक है, $requested खोल रहा हूँ।"
                    } catch (_: Exception) {
                        "$requested खोल नहीं पाया।"
                    }
                }
            }
            return "$requested नाम का ऐप इस फोन में नहीं मिला।"
        }

        if (text.startsWith("call ") || text.startsWith("कॉल ")) {
            val number = original.replaceFirst(Regex("(?i)^\\s*(call|कॉल)\\s*"), "").trim()
            if (number.isNotBlank()) {
                return try {
                    context.startActivity(Intent(Intent.ACTION_DIAL).apply {
                        data = android.net.Uri.parse("tel:${number.replace(" ", "")}")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    })
                    "कॉल स्क्रीन खोल रहा हूँ।"
                } catch (_: Exception) { "कॉल स्क्रीन नहीं खोल पाया।" }
            }
        }

        if (containsAny(text, "alarm", "अलार्म")) {
            return try {
                context.startActivity(Intent(android.provider.AlarmClock.ACTION_SET_ALARM).apply {
                    putExtra(android.provider.AlarmClock.EXTRA_SKIP_UI, false)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                })
                "अलार्म सेट करने की स्क्रीन खोल रहा हूँ।"
            } catch (_: Exception) { "अलार्म स्क्रीन नहीं खोल पाया।" }
        }

        return null
    }

    private fun setFlashlight(context: Context, on: Boolean): String {
        return try {
            val camera = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val id = camera.cameraIdList.firstOrNull { info ->
                camera.getCameraCharacteristics(info).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            } ?: return "इस फोन पर फ्लैशलाइट उपलब्ध नहीं है।"
            camera.setTorchMode(id, on)
            flashlightOn = on
            if (on) "फ्लैशलाइट चालू कर दी।" else "फ्लैशलाइट बंद कर दी।"
        } catch (_: Exception) { "फ्लैशलाइट बदल नहीं पाई।" }
    }

    private fun isCloseCommand(text: String): Boolean {
        return text == "close" || text == "exit" || text == "quit" ||
            text.contains("close app") || text.contains("close this app") ||
            text.contains("app बंद") || text.contains("ऐप बंद") || text.contains("इसे बंद") ||
            text.contains("इसे क्लोज") || text.contains("बंद कर दो")
    }

    private fun isSimpleOffFollowUp(text: String): Boolean {
        return text == "off" || text == "turn off" || text == "turn it off" ||
            text == "switch it off" || text == "बंद करो" || text == "इसे बंद करो" ||
            text == "बंद कर दो" || text == "इसे बंद कर दो" || text == "ऑफ करो" || text == "ऑफ कर दो"
    }

    private fun containsAny(text: String, vararg values: String): Boolean = values.any { text.contains(it) }

    private fun accessibilityRequired(): String = "फोन कंट्रोल के लिए Accessibility Service को एक बार चालू करना होगा।"

    private fun global(context: Context, action: Int, success: String): String {
        if (!AccessibilityCommandService.isEnabled()) return accessibilityRequired()
        return if (AccessibilityCommandService.global(action)) success else "यह फोन action अभी नहीं कर पाया।"
    }

    private fun accessibilityScroll(forward: Boolean): String {
        if (!AccessibilityCommandService.isEnabled()) return accessibilityRequired()
        return if (AccessibilityCommandService.scroll(forward)) "स्क्रॉल कर दिया।" else "अभी स्क्रॉल नहीं कर पाया।"
    }

    private fun open(context: Context, intent: Intent, message: String): String {
        return try {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            message
        } catch (_: Exception) { "यह स्क्रीन अभी नहीं खोल पाया।" }
    }

    private fun findInstalledApp(context: Context, spokenName: String): String? {
        val pm = context.packageManager
        val normalized = spokenName.trim().lowercase(Locale.ROOT)
        val aliases = mapOf(
            "youtube" to "youtube", "यूट्यूब" to "youtube",
            "chrome" to "chrome", "क्रोम" to "chrome",
            "whatsapp" to "whatsapp", "व्हाट्सऐप" to "whatsapp", "व्हाट्सएप" to "whatsapp",
            "instagram" to "instagram", "इंस्टाग्राम" to "instagram",
            "facebook" to "facebook", "फेसबुक" to "facebook",
            "gmail" to "gmail", "जीमेल" to "gmail",
            "maps" to "maps", "google maps" to "maps", "मैप्स" to "maps",
            "camera" to "camera", "कैमरा" to "camera",
            "gallery" to "gallery", "गैलरी" to "gallery",
            "phone" to "phone", "फोन" to "phone",
            "messages" to "messages", "message" to "messages", "मैसेज" to "messages",
            "play store" to "play store", "प्ले स्टोर" to "play store"
        )
        val target = aliases[normalized] ?: normalized
        val knownPackages = mapOf(
            "youtube" to "com.google.android.youtube",
            "chrome" to "com.android.chrome",
            "whatsapp" to "com.whatsapp",
            "instagram" to "com.instagram.android",
            "facebook" to "com.facebook.katana",
            "gmail" to "com.google.android.gm",
            "maps" to "com.google.android.apps.maps",
            "camera" to "com.sec.android.app.camera",
            "phone" to "com.samsung.android.dialer",
            "messages" to "com.samsung.android.messaging",
            "play store" to "com.android.vending"
        )
        val knownPackage = knownPackages[target]
        if (knownPackage != null) {
            try {
                if (pm.getLaunchIntentForPackage(knownPackage) != null) return knownPackage
            } catch (_: Exception) { }
        }

        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val activities = pm.queryIntentActivities(launcherIntent, 0)
        return activities.firstOrNull { info ->
            val label = info.loadLabel(pm).toString().lowercase(Locale.ROOT)
            label == target || label.contains(target) || target.contains(label)
        }?.activityInfo?.packageName
    }
}
