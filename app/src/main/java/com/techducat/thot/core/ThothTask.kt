package com.techducat.thot.core

/**
 * Represents a single AI task sent to Thot.
 *
 * @param prompt         The user's instruction or question.
 * @param screenContext  Text scraped from the current screen by the AccessibilityService;
 *                       automatically prepended to the prompt when available.
 * @param provider       Which LLM backend to use.
 */
data class ThothTask(
    val prompt: String,
    val screenContext: String = "",
    val provider: ProviderType = ProviderType.LOCAL
) {
    /** Build the full prompt sent to the model, including any captured screen context. */
    fun buildFullPrompt(): String = if (screenContext.isBlank()) {
        prompt
    } else {
        """
        |[Screen context captured from current app]
        |$screenContext
        |
        |[User request]
        |$prompt
        """.trimMargin()
    }
}

enum class ProviderType {
    LOCAL,
    OPENAI;

    companion object {
        fun fromString(value: String): ProviderType =
            values().firstOrNull { it.name == value } ?: LOCAL
    }
}
