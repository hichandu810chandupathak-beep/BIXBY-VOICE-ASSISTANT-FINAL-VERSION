package com.bixby.voiceassistant

import com.google.genai.kotlin.Client
import com.google.genai.kotlin.types.GenerateContentConfig

/**
 * Isolated Gemini boundary. The API key is supplied by BuildConfig from local.properties.
 * No key is hardcoded in this source file.
 */
class AssistantAiHandler {

    private val apiKey: String
        get() = BuildConfig.GEMINI_API_KEY.trim()

    suspend fun generateResponse(userText: String): Result<String> {
        val prompt = userText.trim()
        if (prompt.isEmpty()) {
            return Result.failure(IllegalArgumentException("Empty user input"))
        }

        if (apiKey.isEmpty()) {
            return Result.failure(MissingGeminiApiKeyException())
        }

        return runCatching {
            Client(apiKey = apiKey).use { client ->
                val response = client.models.generateContent(
                    model = "gemini-flash-latest",
                    text = prompt,
                    config = GenerateContentConfig(
                        systemInstruction = "You are Bixby, a concise, friendly Android voice assistant. Answer naturally and helpfully."
                    )
                )

                response.text?.trim().takeUnless { it.isNullOrEmpty() }
                    ?: throw IllegalStateException("Gemini returned an empty response")
            }
        }
    }

    class MissingGeminiApiKeyException : IllegalStateException("GEMINI_API_KEY is missing")
}
