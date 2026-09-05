package com.bixby.voiceassistant

import android.content.Context
import com.google.genai.kotlin.Client
import com.google.genai.kotlin.types.Content
import com.google.genai.kotlin.types.GenerateContentConfig

/** Routes local device commands before contacting Gemini. */
class AssistantAiHandler(context: Context) {

    private val commandExecutor = CommandExecutor(context.applicationContext)
    private val apiKey: String
        get() = BuildConfig.GEMINI_API_KEY.trim()

    suspend fun generateResponse(userText: String): Result<String> {
        val prompt = userText.trim()
        if (prompt.isEmpty()) return Result.failure(IllegalArgumentException("Empty user input"))

        when (val local = commandExecutor.executeIfSupported(prompt)) {
            is CommandExecutor.Result.Handled -> return Result.success(local.message)
            CommandExecutor.Result.NotHandled -> Unit
        }

        if (apiKey.isEmpty()) return Result.failure(MissingGeminiApiKeyException())

        return runCatching {
            Client(apiKey = apiKey).use { client ->
                val response = client.models.generateContent(
                    model = "gemini-flash-latest",
                    text = Content.fromText(prompt),
                    config = GenerateContentConfig(
                        systemInstruction = Content.fromText(
                            "You are Bixby, a concise, friendly Android voice assistant. Answer naturally and helpfully."
                        )
                    )
                )
                response.text?.trim().takeUnless { it.isNullOrEmpty() }
                    ?: throw IllegalStateException("Gemini returned an empty response")
            }
        }
    }

    class MissingGeminiApiKeyException : IllegalStateException("GEMINI_API_KEY is missing")
}
