package com.techducat.thot.local

/**
 * Built-in offline provider — works out of the box with no API key required.
 *
 * This provider uses heuristic pattern matching on the captured screen context
 * to generate structured, useful responses entirely on-device. No network call,
 * no account, no cost.
 *
 * It is the default provider for fresh installs so that:
 *  - New users and reviewers see the app working immediately.
 *  - Users on metered connections or without AI accounts still get value.
 *  - Users can optionally upgrade to a cloud or on-device LLM in Settings.
 *
 * To wire in a real on-device model (MediaPipe / llama.cpp) select the
 * corresponding provider in Settings — this class is kept as the zero-config
 * fallback.
 */
class LocalLLMProvider {

    /**
     * Process [prompt] (which may include screen context prepended by
     * [ThotTask.buildFullPrompt]) and return a response string synchronously.
     * Called on a background thread by [ThotCoreProvider].
     */
    fun handle(prompt: String): String {
        val lower = prompt.lowercase()
        val context = extractScreenContext(prompt)
        val hasContext = context.isNotBlank()

        return when {
            lower.contains("explain") || lower.contains("what is") || lower.contains("what does") ->
                buildExplanation(context, hasContext)

            lower.contains("summarize") || lower.contains("summarise") ||
            lower.contains("summary") || lower.contains("tldr") ->
                buildSummary(context, hasContext)

            lower.contains("respond") || lower.contains("reply") || lower.contains("answer") ->
                buildResponse(context, hasContext)

            lower.contains("write") || lower.contains("draft") || lower.contains("compose") ->
                buildDraft(context, hasContext)

            else -> buildGeneric(context, hasContext)
        }
    }

    // ── Context extraction ─────────────────────────────────────────────────────

    /** Pull the raw screen text out of a prompt built by [ThotTask.buildFullPrompt]. */
    private fun extractScreenContext(prompt: String): String {
        val start = prompt.indexOf("[Screen context captured from current app]")
        val end   = prompt.indexOf("[User request]")
        if (start == -1 || end == -1 || end <= start) return ""
        return prompt.substring(start + "[Screen context captured from current app]".length, end).trim()
    }

    // ── Response builders ──────────────────────────────────────────────────────

    private fun buildExplanation(context: String, hasContext: Boolean): String {
        if (!hasContext) return noContextMessage("explain content")

        val sentences = context
            .split(Regex("[.!?]\\s+"))
            .map { it.trim() }
            .filter { it.length > 20 }
            .take(5)

        val bullets = sentences.joinToString("\n") { "  • $it." }

        return """
📖 Explanation (built-in offline AI)

Here's a plain-language breakdown of what's on your screen:

$bullets

${upgradeNote()}
        """.trimIndent()
    }

    private fun buildSummary(context: String, hasContext: Boolean): String {
        if (!hasContext) return noContextMessage("summarize")

        val words = context.split(Regex("\\s+")).filter { it.isNotBlank() }
        val wordCount = words.size
        val keyPhrases = words
            .filter { it.length > 5 }
            .groupBy { it.lowercase() }
            .entries
            .sortedByDescending { it.value.size }
            .take(5)
            .map { it.key }

        val snippet = words.take(40).joinToString(" ")
        val ellipsis = if (wordCount > 40) "…" else ""

        return """
📋 Summary (built-in offline AI)

Content length: ~$wordCount words

Opening: "$snippet$ellipsis"

Key terms spotted: ${if (keyPhrases.isEmpty()) "none identified" else keyPhrases.joinToString(", ")}

${upgradeNote()}
        """.trimIndent()
    }

    private fun buildResponse(context: String, hasContext: Boolean): String {
        val intro = if (hasContext)
            "Based on the content visible on your screen, here are some response options:"
        else
            "Here are some general response templates you can adapt:"

        return """
💬 Suggested response (built-in offline AI)

$intro

Option A — Acknowledge and follow up:
"Thanks for sharing this. I'll review it carefully and get back to you with my thoughts."

Option B — Positive agreement:
"This looks good to me. Happy to move forward on this basis."

Option C — Request clarification:
"Could you clarify [specific point]? I want to make sure I understand before responding."

${upgradeNote()}
        """.trimIndent()
    }

    private fun buildDraft(context: String, hasContext: Boolean): String {
        val contextHint = if (hasContext)
            "Using the content visible on your screen as context:"
        else
            "Here's a general-purpose draft template:"

        return """
✍️ Draft (built-in offline AI)

$contextHint

Subject: [Add a clear, specific subject]

Hi [Name],

I hope this message finds you well.

[State your main point clearly in the first sentence.]

[Provide any necessary background or context — 1–2 sentences.]

[State what you need or what action you're requesting.]

Please let me know if you have any questions.

Best regards,
[Your name]

${upgradeNote()}
        """.trimIndent()
    }

    private fun buildGeneric(context: String, hasContext: Boolean): String {
        val contextLine = if (hasContext)
            "Screen content captured (${context.split(" ").size} words)."
        else
            "No screen content captured — enable the Accessibility permission for context-aware responses."

        return """
🤖 Thot AI (built-in offline mode)

$contextLine

Thot is running in built-in offline mode, which works without any API key or account. For more powerful AI responses — including context-aware answers, smarter summaries, and personalised drafts — you can optionally upgrade in Settings:

  • On-device AI: MediaPipe / Gemma or llama.cpp (free, private, runs on your phone)
  • Cloud AI: OpenAI GPT-4o or Anthropic Claude (requires an API key)

You can change this anytime under Settings → AI Provider.
        """.trimIndent()
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private fun noContextMessage(task: String): String =
        "ℹ️ No screen content was captured.\n\n" +
        "To $task, enable the Accessibility permission so Thot can read your current screen. " +
        "You can do this under the Permissions section on the main screen."

    private fun upgradeNote(): String =
        "─────────────────────────────\n" +
        "Running in built-in offline mode. For smarter responses, go to Settings → AI Provider\n" +
        "and select an on-device model (free) or a cloud provider (API key required)."
}
