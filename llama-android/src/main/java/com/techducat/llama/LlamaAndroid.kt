package com.techducat.llama

/**
 * Kotlin wrapper around the llama.cpp JNI bridge (llama_android.so).
 *
 * Usage:
 *   val llama = LlamaAndroid()
 *   llama.load("/sdcard/Download/llama-3.2-1b-instruct-q4_k_m.gguf")
 *   val reply = llama.infer("Explain transformers in one paragraph.")
 *   llama.free()
 *
 * All three operations are blocking — call them from a background thread
 * (e.g. via Dispatchers.IO in a coroutine).
 */
class LlamaAndroid {

    private var nativeHandle: Long = 0L

    /**
     * Load a GGUF model from [modelPath].
     * Throws [IllegalStateException] if the model cannot be loaded.
     */
    fun load(modelPath: String) {
        free() // release any previously loaded model
        nativeHandle = nativeLoad(modelPath)
        if (nativeHandle == 0L) {
            throw IllegalStateException(
                "Failed to load llama.cpp model from: $modelPath\n" +
                "Verify the file exists, is a valid GGUF, and the device has enough RAM."
            )
        }
    }

    /**
     * Run inference and return the generated text.
     * Requires a prior call to [load].
     */
    fun infer(prompt: String): String {
        check(nativeHandle != 0L) { "Model not loaded — call load() first." }
        return nativeInfer(nativeHandle, prompt)
    }

    /**
     * Release the model from memory.
     * Safe to call even if [load] was never called or already freed.
     */
    fun free() {
        if (nativeHandle != 0L) {
            nativeFree(nativeHandle)
            nativeHandle = 0L
        }
    }

    // ── JNI declarations ────────────────────────────────────────────────────
    private external fun nativeLoad(modelPath: String): Long
    private external fun nativeInfer(ctxPtr: Long, prompt: String): String
    private external fun nativeFree(ctxPtr: Long)

    companion object {
        init {
            System.loadLibrary("llama_android")
        }
    }
}
