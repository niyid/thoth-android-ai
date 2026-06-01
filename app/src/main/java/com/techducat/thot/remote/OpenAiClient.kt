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
 * Wraps the OpenAI Chat Completions API.
 *
 * - Uses the API key from [ThotPreferences] (set by the user in MainActivity).
 * - All callbacks are delivered on the **main thread**.
 * - Uses modern OkHttp 4.x APIs (no deprecated `.parse()` or `.body()` calls).
 */
class OpenAiClient(private val context: Context) {

    private val prefs by lazy { ThotPreferences(context) }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val mainHandler = Handler(Looper.getMainLooper())

    /**
     * Send [prompt] to GPT-4o and deliver the reply string via [callback].
     * Errors are delivered as an "Error: …" string so callers never see a null.
     */
    fun query(prompt: String, callback: (String) -> Unit) {
        val apiKey = prefs.openAiApiKey
        if (apiKey.isBlank()) {
            deliver("Error: No OpenAI API key configured. Please add it in Thot settings.", callback)
            return
        }

        val messagesArray = JSONArray().apply {
            put(JSONObject().apply {
                put("role", "system")
                put("content", SYSTEM_PROMPT)
            })
            put(JSONObject().apply {
                put("role", "user")
                put("content", prompt)
            })
        }

        val body = JSONObject().apply {
            put("model", MODEL)
            put("messages", messagesArray)
            put("max_tokens", 1024)
            put("temperature", 0.7)
        }

        val requestBody = body.toString()
            .toRequestBody("application/json; charset=utf-8".toMediaType())

        val request = Request.Builder()
            .url(API_URL)
            .addHeader("Authorization", "Bearer $apiKey")
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
                            .getJSONArray("choices")
                            .getJSONObject(0)
                            .getJSONObject("message")
                            .getString("content")
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
        private const val API_URL = "https://api.openai.com/v1/chat/completions"
        private const val MODEL = "gpt-4o"
        private const val SYSTEM_PROMPT =
            "You are Thot, a helpful AI assistant embedded inside the Android operating system. " +
            "You can see the user's current screen context. " +
            "Be concise, practical, and actionable. When given screen context, reference it directly. " +
            "Format your responses for a mobile display — short paragraphs, avoid lengthy preambles."
    }
}
