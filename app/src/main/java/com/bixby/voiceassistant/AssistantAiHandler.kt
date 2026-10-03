package com.bixby.voiceassistant

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class AssistantAiHandler(private val context: android.content.Context) {
    class GeminiConnectionException(msg: String) : Exception(msg)

    suspend fun generateResponse(prompt: String): Result<String> = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY.trim()
        if (apiKey.isBlank()) {
            return@withContext Result.failure(GeminiConnectionException("Gemini API key is not configured."))
        }

        try {
            val url = URL("https://generativelanguage.googleapis.com/v1beta/models/gemini-3.8-flash:generateContent")
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 15_000
                readTimeout = 30_000
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("x-goog-api-key", apiKey)
                doOutput = true
            }

            val safePrompt = JSONObject.quote(
                """
                You are Bixby, a concise and natural Android voice assistant.
                Prefer Hindi/Hinglish when the user uses Hindi/Hinglish; otherwise use English.
                Answer naturally and briefly. For current or internet-dependent questions, use Google Search grounding.
                User request: $prompt
                """.trimIndent()
            )

            val jsonBody = """{"contents":[{"parts":[{"text":$safePrompt}]}],"tools":[{"google_search":{}}]}"""
            connection.outputStream.use { it.write(jsonBody.toByteArray(Charsets.UTF_8)) }

            val responseCode = connection.responseCode
            val responseBody = if (responseCode in 200..299) {
                connection.inputStream.bufferedReader().use { it.readText() }
            } else {
                connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
            }

            if (responseCode !in 200..299) {
                return@withContext Result.failure(
                    GeminiConnectionException("Gemini API error " + responseCode + ": " + responseBody)
                )
            }

            val answer = JSONObject(responseBody)
                .optJSONArray("candidates")
                ?.optJSONObject(0)
                ?.optJSONObject("content")
                ?.optJSONArray("parts")
                ?.optJSONObject(0)
                ?.optString("text")
                ?.trim()
                .orEmpty()

            if (answer.isBlank()) {
                Result.failure(GeminiConnectionException("Gemini returned an empty response."))
            } else {
                Result.success(answer)
            }
        } catch (e: Exception) {
            Result.failure(
                GeminiConnectionException("Gemini connection failed: " + (e.message ?: "unknown error"))
            )
        }
    }
}