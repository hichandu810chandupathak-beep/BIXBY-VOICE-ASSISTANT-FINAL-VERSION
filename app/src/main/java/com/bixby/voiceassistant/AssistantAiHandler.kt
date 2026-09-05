package com.bixby.voiceassistant

import android.content.Context
import com.bixby.voiceassistant.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/** Routes local device commands before contacting Gemini over REST. */
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

        when (val local = commandExecutor.executeIfSupported(prompt)) {
            is CommandExecutor.Result.Handled -> return@withContext Result.success(local.message)
            CommandExecutor.Result.NotHandled -> Unit
        }

        if (apiKey.isEmpty()) {
            return@withContext Result.failure(MissingGeminiApiKeyException())
        }

        try {
            val requestJson = JSONObject()
                .put(
                    "contents",
                    org.json.JSONArray().put(
                        JSONObject().put(
                            "parts",
                            org.json.JSONArray().put(JSONObject().put("text", prompt))
                        )
                    )
                )
                .put(
                    "systemInstruction",
                    JSONObject().put(
                        "parts",
                        org.json.JSONArray().put(
                            JSONObject().put(
                                "text",
                                "You are Bixby, a concise, friendly Android voice assistant. Answer naturally and helpfully."
                            )
                        )
                    )
                )

            val request = Request.Builder()
                .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=$apiKey")
                .post(requestJson.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                .header("Accept", "application/json")
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IllegalStateException("HTTP ${response.code}")
                }

                val body = response.body?.string().orEmpty()
                val text = JSONObject(body)
                    .optJSONArray("candidates")
                    ?.optJSONObject(0)
                    ?.optJSONObject("content")
                    ?.optJSONArray("parts")
                    ?.optJSONObject(0)
                    ?.optString("text")
                    ?.trim()
                    .orEmpty()

                if (text.isEmpty()) {
                    throw IllegalStateException("Empty Gemini response")
                }

                Result.success(text)
            }
        } catch (_: Exception) {
            Result.failure(GeminiConnectionException())
        }
    }

    class MissingGeminiApiKeyException : IllegalStateException("Gemini API key is missing")

    class GeminiConnectionException : IllegalStateException("I am sorry, I am having trouble connecting right now.")
}
