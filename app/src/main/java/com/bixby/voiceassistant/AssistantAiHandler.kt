package com.bixby.voiceassistant

import android.content.Context
import android.util.Log
import com.bixby.voiceassistant.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

/** Executes local device commands first; normal conversation goes directly to Gemini. */
class AssistantAiHandler(context: Context) {
    private val commandExecutor = CommandExecutor(context.applicationContext)
    private val apiKey: String get() = BuildConfig.GEMINI_API_KEY.trim()
    private val httpClient = OkHttpClient()

    suspend fun generateResponse(userText: String): Result<String> = withContext(Dispatchers.IO) {
        val prompt = userText.trim()
        if (prompt.isEmpty()) return@withContext Result.failure(IllegalArgumentException("Empty user input"))
        when (val local = commandExecutor.executeIfSupported(prompt)) {
            is CommandExecutor.Result.Handled -> return@withContext Result.success(local.message)
            CommandExecutor.Result.NotHandled -> Unit
        }
        if (apiKey.isEmpty()) { Log.e("BixbyAPI", "GEMINI_API_KEY is missing"); return@withContext Result.failure(MissingGeminiApiKeyException()) }
        try {
            val requestJson = JSONObject()
                .put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", "You are Bixby. You MUST understand and reply in Hindi and Hinglish natively. Talk like a friendly human, not a robot."))))
                .put("contents", JSONArray().put(JSONObject().put("parts", JSONArray().put(JSONObject().put("text", prompt)))))
            val request = Request.Builder().url("https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=$apiKey").post(requestJson.toString().toRequestBody("application/json; charset=utf-8".toMediaType())).header("Accept", "application/json").build()
            httpClient.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) { Log.e("BixbyAPI", "HTTP ${response.code}: $body"); throw IllegalStateException("HTTP ${response.code}") }
                val parts = JSONObject(body).optJSONArray("candidates")?.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts")
                val text = buildString { if (parts != null) for (i in 0 until parts.length()) append(parts.optJSONObject(i)?.optString("text").orEmpty()) }.trim()
                if (text.isEmpty()) { Log.e("BixbyAPI", "Gemini returned no usable text: $body"); throw IllegalStateException("Empty Gemini response") }
                Result.success(text)
            }
        } catch (error: Exception) { Log.e("BixbyAPI", "OkHttp/Gemini request failed", error); Result.failure(GeminiConnectionException()) }
    }
    class MissingGeminiApiKeyException : IllegalStateException("Gemini API key is missing")
    class GeminiConnectionException : IllegalStateException("I am sorry, I am having trouble connecting right now.")
}
