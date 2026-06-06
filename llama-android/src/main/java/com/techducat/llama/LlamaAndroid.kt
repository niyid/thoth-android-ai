package com.techducat.llama

/**
 * JNI bridge to llama.cpp.
 *
 * Native implementation lives in src/main/cpp/llama_jni.cpp which wraps
 * the llama.cpp C++ API and exposes it to Kotlin via the methods below.
 *
 * All methods are blocking. Call them on a background thread.
 *
 * Model lifecycle:
 *   1. Call [load] once with the path to a GGUF model file.
 *   2. Call [infer] repeatedly for inference.
 *   3. Call [free] when the model is no longer needed to release native memory.
 *
 * Thread safety: this class is NOT thread-safe. Callers must serialize access
 * (e.g., by running all calls on the same IO dispatcher coroutine context).
 */
class LlamaAndroid {

    /**
     * Load a GGUF model from [modelPath].
     * Throws [RuntimeException] if the file is missing or not a valid GGUF model.
     */
    external fun load(modelPath: String)

    /**
     * Run inference on [prompt] and return the generated text.
     * Throws [RuntimeException] if no model is loaded or inference fails.
     */
    external fun infer(prompt: String): String

    /**
     * Release native resources held by the loaded model.
     * After calling this, [load] must be called again before [infer].
     */
    external fun free()

    companion object {
        init {
            System.loadLibrary("llama_android")
        }
    }
}
