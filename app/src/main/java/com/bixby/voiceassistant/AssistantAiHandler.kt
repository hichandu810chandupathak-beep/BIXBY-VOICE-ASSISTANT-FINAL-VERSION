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
import org.json.JSONObject

/** Executes local device commands first, then contacts Gemini only for AI requests. */
class AssistantAiHandler(context: Context) {

    private val commandExecutor = CommandExecutor(context.applicationContext)
    private val apiKey: String
        get() = BuildConfig.GEMINI_API_KEY.trim()

    private val httpClient = OkHttpClient()

    suspend fun generateResponse(userText: String): Result<String> = withContext(Dispatchers.IO) {
        val prompt = userText.trim()
        if (prompt.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("Empty user input"))
        }

        // CRITICAL: local/offline commands always run before any network work.
        when (val local = commandExecutor.executeIfSupported(prompt)) {
            is CommandExecutor.Result.Handled -> {
                return@withContext Result.success(local.message)
            }
            CommandExecutor.Result.NotHandled -> Unit
        }

        if (apiKey.isEmpty()) {
            Log.e("BixbyAPI", "GEMINI_API_KEY is missing")
            return@withContext Result.failure(MissingGeminiApiKeyException())
        }

        try {
            // Gemini generateContent payload: contents -> parts -> text.
            // JSONObject.quote keeps quotes/newlines in user input valid JSON.
            val safeUserText = JSONObject.quote(prompt)
            val jsonBody = """{"contents": [{"parts": [{"text": $safeUserText}]}]}"""

            val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=${apiKey}"

            val request = Request.Builder()
                .url(url)
                .post(jsonBody.toRequestBody("application/json; charset=utf-8".toMediaType()))
                .header("Accept", "application/json")
                .build()

            httpClient.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    // Log the complete Google response so the exact rejection is visible in Logcat.
                    Log.e("BixbyAPI", "HTTP ${response.code}: $body")
                    throw IllegalStateException("HTTP ${response.code}")
                }

                val root = JSONObject(body)
                val text = root.optJSONArray("candidates")
                    ?.optJSONObject(0)
                    ?.optJSONObject("content")
                    ?.optJSONArray("parts")
                    ?.let { parts ->
                        buildString {
                            for (i in 0 until parts.length()) {
                                parts.optJSONObject(i)?.optString("text")?.let { append(it) }
                            }
                        }
                    }
                    ?.trim()
                    .orEmpty()

                if (text.isEmpty()) {
                    Log.e("BixbyAPI", "Gemini returned no usable text: $body")
                    throw IllegalStateException("Empty Gemini response")
                }

                Result.success(text)
            }
        } catch (error: Exception) {
            Log.e("BixbyAPI", "OkHttp/Gemini request failed", error)
            Result.failure(GeminiConnectionException())
        }
    }

    class MissingGeminiApiKeyException : IllegalStateException("Gemini API key is missing")

    class GeminiConnectionException : IllegalStateException("I am sorry, I am having trouble connecting right now.")
}
