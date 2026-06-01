package com.techducat.thot.core

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

/**
 * Optional base class for host-app activities that want direct access to Thot.
 * Extend this if you want to call [requestLLMContext] from your own activities.
 *
 * Activities that cannot extend this class can instead create a [ThotCoreProvider]
 * instance directly.
 */
abstract class BaseThotActivity : AppCompatActivity() {

    // Exposed as ThotCoreProvider directly to allow calling cancel() without unsafe cast.
    protected lateinit var thotProvider: ThotCoreProvider
        private set

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        thotProvider = ThotCoreProvider(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        thotProvider.cancel()
    }

    /**
     * Submit a [ThotTask] to the active LLM provider.
     * [callback] is invoked on the main thread with the response string.
     */
    fun requestLLMContext(task: ThotTask, callback: (String) -> Unit) {
        thotProvider.runTask(task, callback)
    }
}
