package com.techducat.thot.core

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

/**
 * Optional base class for host-app activities that want direct access to Thot.
 * Extend this if you want to call [requestLLMContext] from your own activities.
 *
 * Activities that cannot extend this class can instead create a [ThothCoreProvider]
 * instance directly.
 */
abstract class BaseThothActivity : AppCompatActivity() {

    protected lateinit var thothProvider: ThothContextProvider
        private set

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        thothProvider = ThothCoreProvider(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        (thothProvider as? ThothCoreProvider)?.cancel()
    }

    /**
     * Submit a [ThothTask] to the active LLM provider.
     * [callback] is invoked on the main thread with the response string.
     */
    fun requestLLMContext(task: ThothTask, callback: (String) -> Unit) {
        thothProvider.runTask(task, callback)
    }
}
