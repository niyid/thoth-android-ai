package com.techducat.thot.core

import android.content.Context
import com.techducat.thot.local.LocalLLMProvider
import com.techducat.thot.remote.OpenAiClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * The central dispatcher that routes [ThothTask]s to the correct LLM backend.
 *
 * All callbacks are delivered on the Android main thread so callers can update
 * the UI directly without an extra `runOnUiThread` call.
 */
class ThothCoreProvider(private val context: Context) : ThothContextProvider {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val localProvider by lazy { LocalLLMProvider() }
    private val remoteProvider by lazy { OpenAiClient(context) }

    override fun runTask(task: ThothTask, callback: (String) -> Unit) {
        when (task.provider) {
            ProviderType.LOCAL -> scope.launch {
                // LocalLLMProvider is synchronous; run on IO to keep main thread free.
                val result = kotlinx.coroutines.withContext(Dispatchers.IO) {
                    localProvider.handle(task.buildFullPrompt())
                }
                callback(result)
            }
            ProviderType.OPENAI -> {
                // OpenAiClient is already async (OkHttp enqueue) and delivers on main.
                remoteProvider.query(task.buildFullPrompt(), callback)
            }
        }
    }

    /** Cancel all pending work (call from onDestroy). */
    fun cancel() {
        scope.coroutineContext[SupervisorJob]?.cancel()
    }
}
