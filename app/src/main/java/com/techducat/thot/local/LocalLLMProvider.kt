package com.techducat.thot.local

/**
 * Offline / local AI provider.
 *
 * In the absence of an on-device model binary, this implementation:
 *  - Detects common task types (explain, summarize, respond, write) from the prompt
 *  - Mirrors the screen context back with structured guidance
 *  - Provides a useful fallback when there's no internet or no API key
 *
 * To wire in a real on-device model (e.g. Google AI Edge / MediaPipe LLM Inference,
 * or a llama.cpp JNI binding) replace the body of [handle] with a call to your
 * model's inference function. The interface stays the same.
 */
class LocalLLMProvider {

    /**
     * Process [prompt] (which may include screen context prepended by [ThotTask.buildFullPrompt])
     * and return a response string synchronously.
     * This is called on a background thread by [ThotCoreProvider].
     */
    fun handle(prompt: String): String {
        val lower = prompt.lowercase()

        return when {
            // Explanation request
            lower.contains("explain") || lower.contains("what is") || lower.contains("what does") ->
                buildExplanation(prompt)

            // Summarisation
            lower.contains("summarize") || lower.contains("summary") || lower.contains("tldr") ->
                buildSummary(prompt)

            // Response drafting
            lower.contains("respond") || lower.contains("reply") || lower.contains("answer") ->
                buildResponse(prompt)

            // Writing / drafting
            lower.contains("write") || lower.contains("draft") || lower.contains("compose") ->
                buildDraft(prompt)

            // Generic fallback
            else -> buildGeneric(prompt)
        }
    }

    // ── Response builders ──────────────────────────────────────────────────────

    private fun buildExplanation(prompt: String): String {
        val hasContext = prompt.contains("[Screen context")
        return if (hasContext) {
            "📖 Explanation (offline mode)\n\n" +
            "Based on what's visible on your screen, here's a plain-language breakdown:\n\n" +
            "• The content appears to be an interface or document requiring interpretation.\n" +
            "• Key terms or sections should be read top-to-bottom for context flow.\n" +
            "• For a detailed AI explanation, please configure an OpenAI or Anthropic API key in Thot settings."
        } else {
            "📖 Offline mode — I can provide better explanations with an OpenAI or Anthropic API key. " +
            "Your question: \"$prompt\""
        }
    }

    private fun buildSummary(prompt: String): String {
        val hasContext = prompt.contains("[Screen context")
        return if (hasContext) {
            "📋 Summary (offline mode)\n\n" +
            "The screen content has been captured. For an intelligent summary, enable OpenAI or Anthropic " +
            "in Thot settings.\n\n" +
            "In offline mode, Thot can copy/export the text for you to paste into another tool."
        } else {
            "📋 I need either screen context or a connected AI provider to summarise content."
        }
    }

    private fun buildResponse(prompt: String): String =
        "💬 Suggested response (offline mode)\n\n" +
        "I'm running without an AI backend. Here's a generic polite response template:\n\n" +
        "\"Thank you for your message. I've reviewed the content and will get back to you " +
        "with a detailed reply shortly.\"\n\n" +
        "Enable OpenAI or Anthropic in Thot settings for context-aware responses."

    private fun buildDraft(prompt: String): String =
        "✍️ Draft (offline mode)\n\n" +
        "To draft personalised content I need an active AI provider. " +
        "Please add your OpenAI or Anthropic API key in Thot Settings → AI Provider.\n\n" +
        "Once connected, I can write emails, messages, posts, and more based on your current screen."

    private fun buildGeneric(prompt: String): String =
        "🤖 Thot (offline mode)\n\n" +
        "I received your request but I'm running in local mode without a language model. " +
        "For full AI capabilities:\n" +
        "  1. Open Thot settings\n" +
        "  2. Set your OpenAI or Anthropic API key\n" +
        "  3. Select your preferred provider\n\n" +
        "Your prompt: \"${prompt.take(120)}${if (prompt.length > 120) "…" else ""}\""
}
