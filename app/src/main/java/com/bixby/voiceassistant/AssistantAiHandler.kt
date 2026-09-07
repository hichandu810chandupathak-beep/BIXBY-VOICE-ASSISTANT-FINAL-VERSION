package com.bixby.voiceassistant
import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class AssistantAiHandler(private val context: Context) {
    suspend fun generateResponse(prompt: String): Result<String> = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences("bixby_settings", Context.MODE_PRIVATE)
        val apiKey = prefs.getString("gemini_api_key", "")?.trim()
        if (apiKey.isNullOrEmpty()) return@withContext Result.failure(Exception("API Key missing. Check Settings."))
        try {
            val url = URL("https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=$apiKey")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.doOutput = true
            val safePrompt = prompt.replace("\"", "\\\"").replace("\n", " ")
            val jsonBody = """{"contents": [{"parts":[{"text": "You are Bixby. Be concise and conversational. User: $safePrompt"}]}]}"""
            connection.outputStream.use { it.write(jsonBody.toByteArray(Charsets.UTF_8)) }
            
            if (connection.responseCode == 200) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                val text = JSONObject(response).getJSONArray("candidates").getJSONObject(0).getJSONObject("content").getJSONArray("parts").getJSONObject(0).getString("text")
                Result.success(text)
            } else {
                val err = connection.errorStream?.bufferedReader()?.use { it.readText() } ?: "Unknown Error"
                Result.failure(Exception("HTTP ${connection.responseCode}: $err"))
            }
        } catch (e: Exception) { Result.failure(Exception("Network Error: ${e.message}")) }
    }
}