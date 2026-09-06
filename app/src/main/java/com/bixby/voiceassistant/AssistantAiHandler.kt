package com.bixby.voiceassistant

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

/** Routes call/message commands locally; all other input goes directly to Gemini. */
class AssistantAiHandler(context: Context) {
    private val commandExecutor = CommandExecutor(context.applicationContext)
    private val httpClient = OkHttpClient()

    suspend fun generateResponse(userText: String): Result<String> = withContext(Dispatchers.IO) {
        val prompt = userText.trim()
        if (prompt.isEmpty()) return@withContext Result.failure(IllegalArgumentException("Empty user input"))

        val lower = prompt.lowercase()
        if (lower.contains("call") || lower.contains("message")) {
            return@withContext commandExecutor.executeIfSupported(prompt).let { result ->
                when (result) {
                    is CommandExecutor.Result.Handled -> Result.success(result.message)
                    CommandExecutor.Result.NotHandled -> Result.failure(IllegalStateException("Local command could not be handled"))
                }
            }
        }

        try {
            val requestJson = JSONObject()
                .put("contents", JSONArray().put(JSONObject()
                    .put("role", "user")
                    .put("parts", JSONArray().put(JSONObject().put("text", prompt)))))
                .put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", "You are Bixby, a helpful conversational voice assistant. Understand Hindi, Hinglish, and English naturally. Reply clearly and naturally. Do not behave like a rigid command parser."))))

            val request = Request.Builder()
                .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-3.8-flash:generateContent")
                .addHeader("x-goog-api-key", GEMINI_API_KEY)
                .addHeader("Content-Type", "application/json")
                .post(requestJson.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()

            httpClient.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    Log.e("BixbyAPI", "HTTP ${response.code}: $body")
                    return@withContext Result.failure(GeminiConnectionException())
                }
                val parts = JSONObject(body)
                    .optJSONArray("candidates")
                    ?.optJSONObject(0)
                    ?.optJSONObject("content")
                    ?.optJSONArray("parts")
                val text = buildString {
                    if (parts != null) {
                        for (i in 0 until parts.length()) {
                            append(parts.optJSONObject(i)?.optString("text").orEmpty())
                        }
                    }
                }.trim()
                if (text.isEmpty()) {
                    Log.e("BixbyAPI", "Gemini returned no usable text: $body")
                    return@withContext Result.failure(GeminiConnectionException())
                }
                Result.success(text)
            }
        } catch (error: Exception) {
            Log.e("BixbyAPI", "Gemini request failed", error)
            Result.failure(GeminiConnectionException())
        }
    }

    companion object {
        val GEMINI_API_KEY = "YOUR_API_KEY_HERE"
    }

    class GeminiConnectionException : IllegalStateException("Gemini connection failed")
}
