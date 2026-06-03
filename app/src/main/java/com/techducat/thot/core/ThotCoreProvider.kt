package com.techducat.thot.core

import android.content.Context
import com.techducat.thot.local.LocalLLMProvider
import com.techducat.thot.local.MediaPipeLLMProvider
import com.techducat.thot.local.LlamaCppProvider
import com.techducat.thot.remote.AnthropicClient
import com.techducat.thot.remote.OpenAiClient
import com.techducat.thot.settings.ThotPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The central dispatcher that routes [ThotTask]s to the correct LLM backend.
 *
 * All callbacks are delivered on the Android main thread so callers can update
 * the UI directly without an extra `runOnUiThread` call.
 */
class ThotCoreProvider(private val context: Context) : ThotContextProvider {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val prefs by lazy { ThotPreferences(context) }

    private val localProvider by lazy { LocalLLMProvider() }
    private val mediaPipeProvider by lazy { MediaPipeLLMProvider(context) }
    private val llamaCppProvider by lazy { LlamaCppProvider(context) }
    private val openAiProvider by lazy { OpenAiClient(context) }
    private val anthropicProvider by lazy { AnthropicClient(context) }

    override fun runTask(task: ThotTask, callback: (String) -> Unit) {
        when (task.provider) {
            ProviderType.LOCAL -> scope.launch {
                // LocalLLMProvider is synchronous; run on IO to keep main thread free.
                val result = withContext(Dispatchers.IO) {
                    localProvider.handle(task.buildFullPrompt())
                }
                callback(result)
            }
            ProviderType.MEDIAPIPE -> scope.launch {
                val result = withContext(Dispatchers.IO) {
                    mediaPipeProvider.handle(task.buildFullPrompt(), prefs.localModelPath)
                }
                callback(result)
            }
            ProviderType.LLAMACPP -> scope.launch {
                val result = withContext(Dispatchers.IO) {
                    llamaCppProvider.handle(task.buildFullPrompt(), prefs.localModelPath)
                }
                callback(result)
            }
            ProviderType.OPENAI -> {
                // OpenAiClient is already async (OkHttp enqueue) and delivers on main.
                openAiProvider.query(task.buildFullPrompt(), callback)
            }
            ProviderType.ANTHROPIC -> {
                // AnthropicClient is already async (OkHttp enqueue) and delivers on main.
                anthropicProvider.query(task.buildFullPrompt(), callback)
            }
        }
    }

    /** Cancel all pending work (call from onDestroy). */
    fun cancel() {
        scope.cancel()
    }

    /**
     * Discard any cached on-device model instances.
     * Call this from MainActivity after the user saves a new model file path,
     * so the next inference picks up the updated path.
     */
    fun resetLocalProviders() {
        mediaPipeProvider.reset()
        llamaCppProvider.reset()
    }
}
