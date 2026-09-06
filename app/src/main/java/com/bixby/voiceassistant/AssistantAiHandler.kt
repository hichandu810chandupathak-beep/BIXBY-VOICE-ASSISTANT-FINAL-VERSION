package com.bixby.voiceassistant
import android.content.Context
import android.content.Intent
import android.provider.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class AssistantAiHandler(private val context: Context) {
    class GeminiConnectionException(msg: String) : Exception(msg)

    suspend fun generateResponse(prompt: String): Result<String> = withContext(Dispatchers.IO) {
        val lower = prompt.lowercase()
        
        // 1. OFFLINE LOCAL COMMANDS
        if (lower.contains("wifi") || lower.contains("wi-fi")) {
            val intent = Intent(Settings.ACTION_WIFI_SETTINGS).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
            context.startActivity(intent)
            return@withContext Result.success("Opening Wi-Fi settings.")
        }
        if (lower.contains("bluetooth")) {
            val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
            context.startActivity(intent)
            return@withContext Result.success("Opening Bluetooth settings.")
        }
        if (lower.startsWith("call ")) {
            return@withContext Result.success("I need contact linking to call ${prompt.substring(5)}. Try manual dialing for now.")
        }
        if (lower.startsWith("open ")) {
            val appName = lower.substring(5).trim().replace(" ", "")
            val launchIntent = context.packageManager.getLaunchIntentForPackage("com.$appName.android") ?: context.packageManager.getLaunchIntentForPackage("com.android.$appName")
            if (launchIntent != null) { context.startActivity(launchIntent); return@withContext Result.success("Opening app.") }
        }

        // 2. ONLINE AI BRAIN (GEMINI)
        val prefs = context.getSharedPreferences("bixby_settings", Context.MODE_PRIVATE)
        val apiKey = prefs.getString("gemini_api_key", "")?.trim()

        if (apiKey.isNullOrEmpty()) { return@withContext Result.success("Please add your Gemini API Key in the Settings page.") }

        try {
            val url = URL("https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=$apiKey")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.doOutput = true

            // Secure JSON parsing to prevent crashes from quotes or newlines
            val safePrompt = prompt.replace("\"", "\\\"").replace("\n", " ")
            val jsonBody = """{"contents": [{"parts":[{"text": "You are Bixby, a smart assistant. Answer concisely in Hinglish or English. User: $safePrompt"}]}]}"""
            connection.outputStream.use { it.write(jsonBody.toByteArray(Charsets.UTF_8)) }

            if (connection.responseCode == 200) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                val text = JSONObject(response).getJSONArray("candidates").getJSONObject(0).getJSONObject("content").getJSONArray("parts").getJSONObject(0).getString("text")
                Result.success(text)
            } else {
                Result.failure(GeminiConnectionException("API Error: ${connection.responseCode}"))
            }
        } catch (e: Exception) { Result.failure(GeminiConnectionException("Internet connection failed.")) }
    }
}
