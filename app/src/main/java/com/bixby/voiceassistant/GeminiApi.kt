package com.bixby.voiceassistant

import com.squareup.moshi.JsonClass
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path

@JsonClass(generateAdapter = true)
data class GeminiRequest(
    val contents: List<GeminiContent>,
    val generationConfig: GeminiGenerationConfig? = null
)

@JsonClass(generateAdapter = true)
data class GeminiGenerationConfig(
    val temperature: Double = 0.2,
    val maxOutputTokens: Int = 256
)

@JsonClass(generateAdapter = true)
data class GeminiContent(val parts: List<GeminiPart>)

@JsonClass(generateAdapter = true)
data class GeminiPart(val text: String)

@JsonClass(generateAdapter = true)
data class GeminiResponse(val candidates: List<GeminiCandidate>? = null)

@JsonClass(generateAdapter = true)
data class GeminiCandidate(val content: GeminiContent? = null)

interface GeminiApi {
    @POST("v1beta/models/{model}:generateContent")
    fun generateContent(
        @Path("model") model: String,
        @Header("x-goog-api-key") apiKey: String,
        @Body request: GeminiRequest
    ): Call<GeminiResponse>
}
