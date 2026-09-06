package com.bixby.voiceassistant
import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class AssistantAiHandler(private val context: Context) {
    class NetworkConfigException(msg: String) : Exception(msg)

    suspend fun generateResponse(prompt: String): Result<String> = withContext(Dispatchers.IO) {
        val lower = prompt.lowercase()
        if (lower.startsWith("call ")) { return@withContext Result.success("Calling feature is disabled pending contact permissions, but I am here to help with anything else!") }

        val prefs = context.getSharedPreferences("bixby_settings", Context.MODE_PRIVATE)
        val apiKey = prefs.getString("gemini_api_key", "")

        if (apiKey.isNullOrEmpty()) {
            return@withContext Result.success("Please add your Gemini API Key in the Settings page first.")
        }

        try {
            val url = URL("https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=$apiKey")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.doOutput = true

            val jsonBody = """{"contents": [{"parts":[{"text": "You are Bixby, a highly intelligent voice assistant. Speak naturally in Hinglish or English based on the user's input. Answer concisely. User says: $prompt"}]}]}"""
            connection.outputStream.use { it.write(jsonBody.toByteArray(Charsets.UTF_8)) }

            if (connection.responseCode == 200) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                val text = JSONObject(response).getJSONArray("candidates").getJSONObject(0).getJSONObject("content").getJSONArray("parts").getJSONObject(0).getString("text")
                Result.success(text)
            } else {
                Result.failure(NetworkConfigException("Server Error: ${connection.responseCode}"))
            }
        } catch (e: Exception) { Result.failure(NetworkConfigException("Connection Failed")) }
    }
}