/**
 * llama_jni.cpp
 *
 * Minimal JNI bridge between Android/Kotlin and llama.cpp.
 *
 * Exposes three native methods to LlamaAndroid.kt:
 *   nativeLoad(modelPath: String): Long      → returns context pointer
 *   nativeInfer(ctxPtr: Long, prompt: String): String
 *   nativeFree(ctxPtr: Long)
 *
 * All heavy work runs on a background thread via Kotlin coroutines;
 * this code is always called off the main thread.
 */

#include <jni.h>
#include <android/log.h>
#include <string>
#include <vector>

// llama.cpp public header
#include "llama.h"

#define LOG_TAG "LlamaAndroid"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  LOG_TAG, __VA_ARGS__)

// Carrier struct so we don't leak the model handle.
struct LlamaHandle {
    llama_model*   model   = nullptr;
    llama_context* ctx     = nullptr;
};

extern "C" {

// ── Load ─────────────────────────────────────────────────────────────────────
JNIEXPORT jlong JNICALL
Java_com_techducat_llama_LlamaAndroid_nativeLoad(
        JNIEnv* env, jobject /*thiz*/, jstring jModelPath) {

    const char* modelPath = env->GetStringUTFChars(jModelPath, nullptr);

    llama_model_params mparams = llama_model_default_params();
    mparams.n_gpu_layers = 0; // CPU-only; set > 0 when Vulkan is available

    llama_model* model = llama_load_model_from_file(modelPath, mparams);
    env->ReleaseStringUTFChars(jModelPath, modelPath);

    if (!model) {
        LOGE("Failed to load model");
        return 0L;
    }

    llama_context_params cparams = llama_context_default_params();
    cparams.n_ctx     = 2048;
    cparams.n_threads = 4;

    llama_context* ctx = llama_new_context_with_model(model, cparams);
    if (!ctx) {
        llama_free_model(model);
        LOGE("Failed to create context");
        return 0L;
    }

    auto* handle = new LlamaHandle{model, ctx};
    LOGI("Model loaded, handle=%p", handle);
    return reinterpret_cast<jlong>(handle);
}

// ── Infer ─────────────────────────────────────────────────────────────────────
JNIEXPORT jstring JNICALL
Java_com_techducat_llama_LlamaAndroid_nativeInfer(
        JNIEnv* env, jobject /*thiz*/, jlong ctxPtr, jstring jPrompt) {

    if (ctxPtr == 0L) {
        return env->NewStringUTF("⚠️ Model not loaded.");
    }

    auto* handle = reinterpret_cast<LlamaHandle*>(ctxPtr);
    const char* promptCStr = env->GetStringUTFChars(jPrompt, nullptr);
    std::string prompt(promptCStr);
    env->ReleaseStringUTFChars(jPrompt, promptCStr);

    // Tokenise
    const int nPromptTokensMax = 1024;
    std::vector<llama_token> tokens(nPromptTokensMax);
    int nTokens = llama_tokenize(
        llama_get_model(handle->ctx),
        prompt.c_str(), static_cast<int32_t>(prompt.size()),
        tokens.data(), nPromptTokensMax,
        /*add_special=*/true, /*parse_special=*/false
    );
    if (nTokens < 0) {
        return env->NewStringUTF("⚠️ Tokenisation failed — prompt may be too long.");
    }
    tokens.resize(nTokens);

    // Decode prompt
    llama_batch batch = llama_batch_get_one(tokens.data(), static_cast<int32_t>(tokens.size()));
    if (llama_decode(handle->ctx, batch) != 0) {
        return env->NewStringUTF("⚠️ Prompt decode failed.");
    }

    // Generate up to 512 tokens
    std::string output;
    const int maxNewTokens = 512;
    const llama_model* model = llama_get_model(handle->ctx);

    for (int i = 0; i < maxNewTokens; ++i) {
        llama_token newToken = llama_sampler_sample(
            llama_sampler_chain_init(llama_sampler_chain_default_params()),
            handle->ctx, -1
        );

        if (llama_token_is_eog(model, newToken)) break;

        char buf[256];
        int nChars = llama_token_to_piece(model, newToken, buf, sizeof(buf), 0, true);
        if (nChars < 0) break;
        output.append(buf, nChars);

        llama_batch nextBatch = llama_batch_get_one(&newToken, 1);
        if (llama_decode(handle->ctx, nextBatch) != 0) break;
    }

    llama_kv_cache_clear(handle->ctx);
    return env->NewStringUTF(output.c_str());
}

// ── Free ──────────────────────────────────────────────────────────────────────
JNIEXPORT void JNICALL
Java_com_techducat_llama_LlamaAndroid_nativeFree(
        JNIEnv* /*env*/, jobject /*thiz*/, jlong ctxPtr) {

    if (ctxPtr == 0L) return;
    auto* handle = reinterpret_cast<LlamaHandle*>(ctxPtr);
    llama_free(handle->ctx);
    llama_free_model(handle->model);
    delete handle;
    LOGI("Model freed");
}

} // extern "C"
