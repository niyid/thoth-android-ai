package com.techducat.thot.core

import android.content.Context
import android.util.Log
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
 *
 * All provider calls are wrapped in try/catch so that an exception from a
 * local provider (e.g. JNI crash in llama.cpp, MediaPipe model load failure)
 * always delivers an error string to the callback rather than silently dropping
 * it and leaving the UI in a permanent loading state.
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
                    runCatching { localProvider.handle(task.buildFullPrompt()) }
                        .getOrElse { e ->
                            Log.e(TAG, "LOCAL provider error", e)
                            "Error: local provider failed — ${e.message}"
                        }
                }
                callback(result)
            }
            ProviderType.MEDIAPIPE -> scope.launch {
                val result = withContext(Dispatchers.IO) {
                    runCatching { mediaPipeProvider.handle(task.buildFullPrompt(), prefs.localModelPath) }
                        .getOrElse { e ->
                            Log.e(TAG, "MEDIAPIPE provider error", e)
                            "Error: MediaPipe inference failed — ${e.message}"
                        }
                }
                callback(result)
            }
            ProviderType.LLAMACPP -> scope.launch {
                val result = withContext(Dispatchers.IO) {
                    runCatching { llamaCppProvider.handle(task.buildFullPrompt(), prefs.localModelPath) }
                        .getOrElse { e ->
                            Log.e(TAG, "LLAMACPP provider error", e)
                            "Error: llama.cpp inference failed — ${e.message}"
                        }
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
     * [mediaPipeProvider.reset] calls [LlmInference.close] and
     * [llamaCppProvider.reset] calls [nativeFree] — both are blocking JNI /
     * native operations that must NOT run on the main thread.
     * Suspend until both are done so the caller can gate UI on completion.
     */
    suspend fun resetLocalProviders() = withContext(Dispatchers.IO) {
        mediaPipeProvider.reset()
        llamaCppProvider.reset()
    }

    companion object {
        private const val TAG = "ThotCoreProvider"
    }
}
