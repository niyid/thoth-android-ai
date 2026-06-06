package com.techducat.thot.local

import android.content.Context
import android.util.Log
import com.google.mediapipe.tasks.genai.llminference.LlmInference

/**
 * On-device inference using the Google AI Edge / MediaPipe LLM Inference API.
 *
 * Supports any .task model file (e.g. Gemma 3 1B IT INT4).
 * Download models from:
 *   https://ai.google.dev/edge/mediapipe/solutions/genai/llm_inference/android
 *
 * The model is loaded lazily on first use and cached for the lifetime of this
 * instance. If the model path changes (user updates settings), call [reset] to
 * force a reload on the next inference call.
 */
class MediaPipeLLMProvider(private val context: Context) {

    private var llm: LlmInference? = null
    private var loadedModelPath: String = ""

    /**
     * Run inference on [prompt] synchronously.
     * Called on a background thread by [com.techducat.thot.core.ThotCoreProvider].
     */
    fun handle(prompt: String, modelPath: String): String {
        if (modelPath.isBlank()) {
            return "⚠️ MediaPipe / Gemma\n\n" +
                "No model file path configured.\n\n" +
                "In Thot Settings, enter the full path to your downloaded .task file " +
                "(e.g. /sdcard/Download/gemma3-1b-it-int4.task).\n\n" +
                "Download models at:\n" +
                "https://ai.google.dev/edge/mediapipe/solutions/genai/llm_inference/android"
        }

        return try {
            ensureLoaded(modelPath)
            llm!!.generateResponse(prompt)
        } catch (e: Exception) {
            Log.e(TAG, "MediaPipe inference error", e)
            "⚠️ MediaPipe error: ${e.message}\n\n" +
            "Check that the model file path is correct and the file is not corrupted."
        }
    }

    /** Force the model to be reloaded on next call (e.g. after path change). */
    fun reset() {
        try {
            llm?.close()
        } catch (e: Exception) {
            Log.w(TAG, "MediaPipe LlmInference close() error (ignored): ${e.message}")
        }
        llm = null
        loadedModelPath = ""
    }

    private fun ensureLoaded(modelPath: String) {
        if (llm != null && loadedModelPath == modelPath) return
        // Close existing instance before loading a new one to free GPU/NPU memory
        try {
            llm?.close()
        } catch (e: Exception) {
            Log.w(TAG, "MediaPipe close() before reload (ignored): ${e.message}")
        }
        val options = LlmInference.LlmInferenceOptions.builder()
            .setModelPath(modelPath)
            .setMaxTokens(1024)
            .build()
        llm = LlmInference.createFromOptions(context, options)
        loadedModelPath = modelPath
    }

    companion object {
        private const val TAG = "MediaPipeLLMProvider"
    }
}
