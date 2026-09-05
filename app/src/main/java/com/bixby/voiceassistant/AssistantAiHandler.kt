package com.bixby.voiceassistant

/**
 * Small AI boundary for the current phase.
 * Replace the implementation with Gemini/network code later without changing the UI controller.
 */
class AssistantAiHandler {

    fun generateResponse(userText: String): String {
        val text = userText.trim()
        if (text.isEmpty()) return "I didn't catch that. Please try again."

        return when {
            text.contains("hello", ignoreCase = true) ||
                text.contains("hi", ignoreCase = true) ||
                text.contains("namaste", ignoreCase = true) ->
                "Hello! How can I help you?"

            text.contains("time", ignoreCase = true) ->
                "I heard your request about the time. A live time provider can be connected in the next logic phase."

            else ->
                "I heard: \"$text\". I'm ready for a full AI response engine."
        }
    }
}
