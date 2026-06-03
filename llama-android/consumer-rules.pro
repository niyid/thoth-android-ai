# Keep the JNI wrapper class so its native methods survive R8/ProGuard.
-keep class com.techducat.llama.LlamaAndroid { *; }
