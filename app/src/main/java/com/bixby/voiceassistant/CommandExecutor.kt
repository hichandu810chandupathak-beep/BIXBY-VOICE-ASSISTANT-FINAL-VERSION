package com.bixby.voiceassistant

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.telephony.PhoneNumberUtils
import androidx.core.content.ContextCompat

/** Routes only hardware/system intents locally; all other conversation goes to Gemini. */
class CommandExecutor(private val context: Context) {
    sealed class Result { data class Handled(val message: String) : Result(); data object NotHandled : Result() }
    fun executeIfSupported(rawText: String): Result {
        val command = rawText.trim().lowercase(); if (command.isEmpty()) return Result.NotHandled
        val isHardwareOrSystemCommand = command.contains("torch") || command.contains("flashlight") || command.contains("bluetooth") || command.contains("wifi") || command.contains("wi-fi") || command.contains("go home") || command == "home" || command.contains("go back") || command == "back" || command.contains("open settings") || command.startsWith("open ") || command.startsWith("launch ") || command.startsWith("start ") || command.contains("call") || command.contains("dial") || command.contains("message") || command.contains("text") || command.contains("whatsapp")
        if (!isHardwareOrSystemCommand) return Result.NotHandled
        if (command.contains("go home") || command == "home") return Result.Handled(goHome())
        if (command.contains("go back") || command == "back") return Result.Handled(goBack())
        if (command.contains("open settings")) { openSettings(Settings.ACTION_SETTINGS); return Result.Handled("Opening settings.") }
        if (command.contains("torch") || command.contains("flashlight")) { val enabled = when { command.contains("turn off") || command.contains("switch off") -> false; command.contains("turn on") || command.contains("switch on") -> true; else -> !torchState }; return Result.Handled(setFlashlight(enabled)) }
        if (command.contains("bluetooth")) return Result.Handled(handleBluetooth(command))
        if (command.contains("wifi") || command.contains("wi-fi")) { val on = command.contains("turn on") || command.contains("switch on"); val off = command.contains("turn off") || command.contains("switch off"); if (on || off) return Result.Handled(handleWifiToggle(on)); openSettings(Settings.ACTION_WIFI_SETTINGS); return Result.Handled("Opening Wi-Fi settings.") }
        if (command.contains("whatsapp") || command.contains("message") || command.contains("text")) return Result.Handled(handleMessage(command))
        if (command.contains("call") || command.contains("dial")) return Result.Handled(handleCall(command))
        if (command.startsWith("open ") || command.startsWith("launch ") || command.startsWith("start ")) return Result.Handled(launchApp(command))
        return Result.NotHandled
    }
    private fun handleMessage(command: String): String {
        val whatsapp = command.contains("whatsapp")
        val withoutPrefix = command.replace(Regex("^.*?\\b(?:message|text|whatsapp)\\b\\s*"), "").trim()
        val targetAndBody = Regex("^(?:to\\s+)?(.+?)(?:\\s+(?:saying|that says|says|:)\\s+)(.+)$").find(withoutPrefix) ?: return "Tell me the contact and message, for example: message Rahul saying hello."
        val target = targetAndBody.groupValues[1].trim(); val body = targetAndBody.groupValues[2].trim()
        val number = extractPhoneNumber(target) ?: resolveContactNumber(target) ?: return "I couldn't find that contact."
        return try { val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:${Uri.encode(number)}")).apply { putExtra("sms_body", body); addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); if (whatsapp) setPackage("com.whatsapp") }; if (whatsapp && context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY) == null) return "WhatsApp is not installed."; context.startActivity(intent); if (whatsapp) "Opening WhatsApp to message $target." else "Opening messages to $target." } catch (_: Exception) { "I couldn't open messaging right now." }
    }
    private fun handleCall(command: String): String {
        val target = command.replace(Regex("^.*?\\b(?:call|dial)\\b\\s*"), "").trim().removeSuffix(".").trim(); if (target.isEmpty()) return "Please tell me a contact name or phone number."
        val number = extractPhoneNumber(target) ?: resolveContactNumber(target) ?: return "I couldn't find that contact."; val uri = Uri.parse("tel:${Uri.encode(number)}")
        val action = if (ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) Intent.ACTION_CALL else Intent.ACTION_DIAL
        return try { context.startActivity(Intent(action, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); if (action == Intent.ACTION_CALL) "Calling $target." else "Opening the dialer for $target." } catch (_: SecurityException) { context.startActivity(Intent(Intent.ACTION_DIAL, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); "Opening the dialer for $target." } catch (_: Exception) { "I couldn't start the call right now." }
    }
    private fun extractPhoneNumber(target: String): String? { val cleaned = target.replace(Regex("[^0-9+*#]"), ""); return if (cleaned.count { it.isDigit() } >= 3 && PhoneNumberUtils.isGlobalPhoneNumber(cleaned)) cleaned else null }
    private fun resolveContactNumber(name: String): String? {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) return null
        return try {
            val projection = arrayOf(android.provider.ContactsContract.CommonDataKinds.Phone.NUMBER, android.provider.ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val candidates = mutableListOf<Pair<String,String>>()
            context.contentResolver.query(android.provider.ContactsContract.CommonDataKinds.Phone.CONTENT_URI, projection, null, null, null)?.use { c -> val ni = c.getColumnIndexOrThrow(android.provider.ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME); val pi = c.getColumnIndexOrThrow(android.provider.ContactsContract.CommonDataKinds.Phone.NUMBER); while (c.moveToNext()) candidates += c.getString(ni) to c.getString(pi) }
            candidates.maxByOrNull { similarity(name, it.first) }?.takeIf { similarity(name, it.first) >= 0.45 }?.second
        } catch (_: Exception) { null }
    }
    private fun similarity(a: String, b: String): Double { val x = a.trim().lowercase(); val y = b.trim().lowercase(); if (x == y) return 1.0; if (x.isEmpty() || y.isEmpty()) return 0.0; if (y.contains(x) || x.contains(y)) return 0.9; val d = levenshtein(x, y); return 1.0 - d.toDouble() / maxOf(x.length, y.length) }
    private fun levenshtein(a: String, b: String): Int { val prev = IntArray(b.length + 1) { it }; for (i in a.indices) { val cur = IntArray(b.length + 1); cur[0] = i + 1; for (j in b.indices) cur[j + 1] = minOf(cur[j] + 1, prev[j + 1] + 1, prev[j] + if (a[i] == b[j]) 0 else 1); for (j in cur.indices) prev[j] = cur[j] }; return prev[b.length] }
    private fun goHome() = try { context.startActivity(Intent(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_HOME); addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }); "Going home." } catch (_: Exception) { "I couldn't go home right now." }
    private fun goBack() = if (AccessibilityCommandService.global(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)) "Going back." else "Please enable Bixby Accessibility Service to use Go back."
    private fun setFlashlight(enabled: Boolean): String { if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) return "Camera permission is needed to control the flashlight."; return try { val cm = context.getSystemService(Context.CAMERA_SERVICE) as android.hardware.camera2.CameraManager; val id = cm.cameraIdList.firstOrNull { cm.getCameraCharacteristics(it).get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE) == true } ?: return "This phone does not have a controllable flashlight."; cm.setTorchMode(id, enabled); torchState = enabled; if (enabled) "Flashlight turned on." else "Flashlight turned off." } catch (_: Exception) { "I couldn't control the flashlight right now." } }
    private fun handleWifiToggle(enable: Boolean): String { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) { openSettings(Settings.ACTION_WIFI_SETTINGS); return "Opening Wi-Fi settings. Android requires system confirmation on this version." }; return try { @Suppress("DEPRECATION") val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as android.net.wifi.WifiManager; @Suppress("DEPRECATION") if (wifi.setWifiEnabled(enable)) if (enable) "Wi-Fi turned on." else "Wi-Fi turned off." else { openSettings(Settings.ACTION_WIFI_SETTINGS); "Opening Wi-Fi settings." } } catch (_: Exception) { openSettings(Settings.ACTION_WIFI_SETTINGS); "Opening Wi-Fi settings." } }
    private fun handleBluetooth(command: String): String { val adapter = android.bluetooth.BluetoothAdapter.getDefaultAdapter() ?: return "Bluetooth is not available on this phone."; val on = command.contains("turn on") || command.contains("switch on"); val off = command.contains("turn off") || command.contains("switch off"); return try { if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) { openSettings(Settings.ACTION_BLUETOOTH_SETTINGS); "Opening Bluetooth settings." } else if (on) { @Suppress("DEPRECATION") if (adapter.enable()) "Bluetooth turned on." else { openSettings(Settings.ACTION_BLUETOOTH_SETTINGS); "Opening Bluetooth settings." } } else if (off) { @Suppress("DEPRECATION") if (adapter.disable()) "Bluetooth turned off." else { openSettings(Settings.ACTION_BLUETOOTH_SETTINGS); "Opening Bluetooth settings." } } else { openSettings(Settings.ACTION_BLUETOOTH_SETTINGS); "Opening Bluetooth settings." } } catch (_: Exception) { openSettings(Settings.ACTION_BLUETOOTH_SETTINGS); "Opening Bluetooth settings." } }
    private fun launchApp(command: String): String { val requested = command.removePrefix("open ").removePrefix("launch ").removePrefix("start ").removePrefix("the ").trim(); if (requested.isEmpty()) return "Please tell me which app to open."; val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER); val matches = context.packageManager.queryIntentActivities(launcher, PackageManager.MATCH_ALL); val match = matches.asSequence().filter { it.activityInfo.packageName != context.packageName }.map { it to it.loadLabel(context.packageManager).toString().trim() }.maxByOrNull { similarity(requested, it.second) }; val intent = match?.takeIf { similarity(requested, it.second) >= 0.45 }?.let { Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setClassName(it.first.activityInfo.packageName, it.first.activityInfo.name).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }; return if (intent != null) { context.startActivity(intent); "Opening ${match.second}." } else "I couldn't find that app." }
    private fun openSettings(action: String) { runCatching { context.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
    companion object { @Volatile private var torchState = false }
}
