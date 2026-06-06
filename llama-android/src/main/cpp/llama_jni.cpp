/**
 * llama_jni.cpp — JNI bridge between LlamaAndroid.kt and llama.cpp.
 *
 * STUB: This file is a placeholder. To use real llama.cpp inference:
 *   1. Add llama.cpp sources to this directory (git submodule recommended).
 *   2. Add the llama subdirectory to CMakeLists.txt:
 *        add_subdirectory(llama.cpp)
 *        target_link_libraries(llama_android llama ${log-lib})
 *   3. Replace the stub implementations below with real llama.cpp calls.
 *
 * Until then, all three methods throw a descriptive Java exception so the
 * Kotlin caller receives an error string rather than a native crash.
 */
#include <jni.h>
#include <string>
#include <android/log.h>

#define LOG_TAG "LlamaJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

static void throwRuntimeException(JNIEnv* env, const char* message) {
    jclass clazz = env->FindClass("java/lang/RuntimeException");
    if (clazz != nullptr) {
        env->ThrowNew(clazz, message);
    }
}

extern "C" {

JNIEXPORT void JNICALL
Java_com_techducat_llama_LlamaAndroid_load(JNIEnv* env, jobject /* thiz */, jstring modelPath) {
    const char* path = env->GetStringUTFChars(modelPath, nullptr);
    LOGI("load() called with path: %s", path ? path : "(null)");
    env->ReleaseStringUTFChars(modelPath, path);
    // STUB: throw until real llama.cpp sources are added
    throwRuntimeException(env,
        "llama.cpp sources not yet integrated. "
        "See llama-android/src/main/cpp/llama_jni.cpp for setup instructions.");
}

JNIEXPORT jstring JNICALL
Java_com_techducat_llama_LlamaAndroid_infer(JNIEnv* env, jobject /* thiz */, jstring prompt) {
    const char* p = env->GetStringUTFChars(prompt, nullptr);
    LOGI("infer() called, prompt length: %zu", p ? strlen(p) : 0);
    env->ReleaseStringUTFChars(prompt, p);
    throwRuntimeException(env,
        "llama.cpp sources not yet integrated. "
        "See llama-android/src/main/cpp/llama_jni.cpp for setup instructions.");
    return env->NewStringUTF(""); // unreachable after throw, but required for type
}

JNIEXPORT void JNICALL
Java_com_techducat_llama_LlamaAndroid_free(JNIEnv* env, jobject /* thiz */) {
    LOGI("free() called");
    // STUB: no-op until real llama.cpp is integrated
}

} // extern "C"
