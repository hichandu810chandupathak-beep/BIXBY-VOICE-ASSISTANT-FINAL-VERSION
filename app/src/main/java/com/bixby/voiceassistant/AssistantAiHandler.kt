package com.bixby.voiceassistant

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class AssistantAiHandler(private val context: android.content.Context) {
    class GeminiConnectionException(msg: String) : Exception(msg)
    // We will inject the API key later.
    private val apiKey = "YOUR_GEMINI_API_KEY_HERE"
    
    suspend fun generateResponse(prompt: String): Result<String> = withContext(Dispatchers.IO) {
        val lower = prompt.lowercase()
        if (lower.startsWith("call ")) {
            return@withContext Result.success("Calling feature triggered for: " + prompt.substring(5))
        }
        
        try {
            val url = URL("https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=$apiKey")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.doOutput = true
            
            val jsonBody = """{"contents": [{"parts":[{"text": "You are Bixby, a smart voice assistant. Answer concisely in English or Hindi/Hinglish. User says: $prompt"}]}]}"""
            connection.outputStream.use { it.write(jsonBody.toByteArray(Charsets.UTF_8)) }
            
            if (connection.responseCode == 200) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                val text = JSONObject(response).getJSONArray("candidates").getJSONObject(0).getJSONObject("content").getJSONArray("parts").getJSONObject(0).getString("text")
                Result.success(text)
            } else {
                Result.failure(GeminiConnectionException("API Error: ${connection.responseCode}"))
            }
        } catch (e: Exception) {
            Result.failure(GeminiConnectionException("Connection Failed"))
        }
    }
}
