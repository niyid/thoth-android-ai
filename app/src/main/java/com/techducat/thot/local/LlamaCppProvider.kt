package com.techducat.thot.local

import android.content.Context
import com.techducat.llama.LlamaAndroid

/**
 * On-device inference using llama.cpp compiled from source via NDK.
 *
 * Supports any GGUF-format model (Llama, Mistral, Phi, Qwen, Gemma, etc.).
 * Download models from:
 *   https://huggingface.co/models?library=gguf
 *
 * Recommended starting models (small, capable):
 *   - Llama 3.2 1B Instruct Q4_K_M  (~800 MB)
 *   - Mistral 7B Instruct v0.3 Q4_K_M (~4 GB, better quality)
 *   - Phi-3 Mini 4K Instruct Q4_K_M  (~2 GB)
 *
 * The model is loaded lazily on first use and cached for the lifetime of this
 * instance. If the model path changes (user updates settings), call [reset] to
 * force a reload on the next inference call.
 */
class LlamaCppProvider(@Suppress("unused") private val context: Context) {

    private val llama = LlamaAndroid()
    private var loadedModelPath: String = ""

    /**
     * Run inference on [prompt] synchronously.
     * Called on a background thread by [com.techducat.thot.core.ThotCoreProvider].
     */
    fun handle(prompt: String, modelPath: String): String {
        if (modelPath.isBlank()) {
            return "⚠️ llama.cpp / GGUF\n\n" +
                "No model file path configured.\n\n" +
                "In Thot Settings, enter the full path to your downloaded .gguf file " +
                "(e.g. /sdcard/Download/llama-3.2-1b-instruct-q4_k_m.gguf).\n\n" +
                "Download GGUF models at:\n" +
                "https://huggingface.co/models?library=gguf"
        }

        return try {
            ensureLoaded(modelPath)
            llama.infer(prompt)
        } catch (e: Exception) {
            "⚠️ llama.cpp error: ${e.message}\n\n" +
            "Check that the model file path is correct and the file is a valid GGUF model."
        }
    }

    /** Force the model to be reloaded on next call (e.g. after path change). */
    fun reset() {
        llama.free()
        loadedModelPath = ""
    }

    private fun ensureLoaded(modelPath: String) {
        if (loadedModelPath == modelPath) return
        llama.load(modelPath)
        loadedModelPath = modelPath
    }
}
