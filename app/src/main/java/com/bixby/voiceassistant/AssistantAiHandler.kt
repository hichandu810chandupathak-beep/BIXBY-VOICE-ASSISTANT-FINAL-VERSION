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
    suspend fun generateResponse(prompt: String): Result<String> = withContext(Dispatchers.IO) {
        val lower = prompt.lowercase()
        
        if (lower.contains("wifi") || lower.contains("wi-fi")) {
            context.startActivity(Intent(Settings.ACTION_WIFI_SETTINGS).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
            return@withContext Result.success("Opening Wi-Fi settings.")
        }
        if (lower.contains("bluetooth")) {
            context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
            return@withContext Result.success("Opening Bluetooth settings.")
        }

        val prefs = context.getSharedPreferences("bixby_settings", Context.MODE_PRIVATE)
        val apiKey = prefs.getString("gemini_api_key", "")?.trim()
        if (apiKey.isNullOrEmpty()) { return@withContext Result.success("Please add your Gemini API Key in Settings.") }

        try {
            val url = URL("https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=$apiKey")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.doOutput = true

            val safePrompt = prompt.replace("\"", "\\\"").replace("\n", " ")
            val jsonBody = """{"contents": [{"parts":[{"text": "You are Bixby. Answer concisely in Hinglish or English. User: $safePrompt"}]}]}"""
            connection.outputStream.use { it.write(jsonBody.toByteArray(Charsets.UTF_8)) }

            if (connection.responseCode == 200) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                try {
                    val text = JSONObject(response).getJSONArray("candidates").getJSONObject(0).getJSONObject("content").getJSONArray("parts").getJSONObject(0).getString("text")
                    Result.success(text)
                } catch (e: Exception) { Result.success("I understood, but formatting failed.") }
            } else {
                val err = connection.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                Result.failure(Exception("API Error: ${connection.responseCode}. Check your API Key."))
            }
        } catch (e: Exception) { Result.failure(Exception("Internet connection failed.")) }
    }
}
