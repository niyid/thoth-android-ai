package com.techducat.thot.remote

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.techducat.thot.settings.ThotPreferences
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Wraps the Anthropic Messages API (claude-sonnet-4-5 / claude-3-5-haiku).
 *
 * - Uses the Anthropic API key from [ThotPreferences].
 * - All callbacks are delivered on the **main thread**.
 * - Compatible with Anthropic API v2023-06-01.
 */
class AnthropicClient(private val context: Context) {

    private val prefs by lazy { ThotPreferences(context) }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val mainHandler = Handler(Looper.getMainLooper())

    /**
     * Send [prompt] to Claude and deliver the reply string via [callback].
     * Errors are delivered as an "Error: …" string so callers never see a null.
     */
    fun query(prompt: String, callback: (String) -> Unit) {
        val apiKey = prefs.anthropicApiKey
        if (apiKey.isBlank()) {
            deliver(
                "Error: No Anthropic API key configured. Please add it in Thot settings.",
                callback
            )
            return
        }

        val messagesArray = JSONArray().apply {
            put(JSONObject().apply {
                put("role", "user")
                put("content", prompt)
            })
        }

        val body = JSONObject().apply {
            put("model", MODEL)
            put("max_tokens", 1024)
            put("system", SYSTEM_PROMPT)
            put("messages", messagesArray)
        }

        val requestBody = body.toString()
            .toRequestBody("application/json; charset=utf-8".toMediaType())

        val request = Request.Builder()
            .url(API_URL)
            .addHeader("x-api-key", apiKey)
            .addHeader("anthropic-version", ANTHROPIC_VERSION)
            .addHeader("Content-Type", "application/json")
            .post(requestBody)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                deliver("Error: ${e.message ?: "Network failure"}", callback)
            }

            override fun onResponse(call: Call, response: Response) {
                response.use { res ->
                    val rawBody = res.body?.string() ?: ""
                    if (!res.isSuccessful) {
                        deliver("Error ${res.code}: $rawBody", callback)
                        return
                    }
                    try {
                        val json = JSONObject(rawBody)
                        val content = json
                            .getJSONArray("content")
                            .getJSONObject(0)
                            .getString("text")
                        deliver(content.trim(), callback)
                    } catch (e: Exception) {
                        deliver("Error parsing response: ${e.message}", callback)
                    }
                }
            }
        })
    }

    private fun deliver(result: String, callback: (String) -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            callback(result)
        } else {
            mainHandler.post { callback(result) }
        }
    }

    companion object {
        private const val API_URL = "https://api.anthropic.com/v1/messages"
        private const val MODEL = "claude-haiku-4-5-20251001"
        private const val ANTHROPIC_VERSION = "2023-06-01"
        private const val SYSTEM_PROMPT =
            "You are Thot, a helpful AI assistant embedded inside the Android operating system. " +
            "You can see the user's current screen context. " +
            "Be concise, practical, and actionable. When given screen context, reference it directly. " +
            "Format your responses for a mobile display — short paragraphs, avoid lengthy preambles."
    }
}
