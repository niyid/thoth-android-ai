# Thot ProGuard rules

# Keep accessibility service
-keep class com.techducat.thot.accessibility.** { *; }

# Keep core provider classes referenced by reflection
-keep class com.techducat.thot.core.** { *; }

# Keep OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }

# Keep JSON
-keep class org.json.** { *; }

# Keep Kotlin coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}

# Firebase Crashlytics
-keepattributes SourceFile,LineNumberTable
-keep public class * extends java.lang.Exception
-keep class com.google.firebase.crashlytics.** { *; }
-dontwarn com.google.firebase.crashlytics.**

# ── R8 missing-class suppressions ─────────────────────────────────────────────
# These are annotation-processor / compile-time-only types that do not exist on
# Android at runtime. R8 sees them referenced in library bytecode and warns;
# suppress them so the release build does not fail.

# javax.lang.model — annotation processing API, JDK-only, not on Android
-dontwarn javax.lang.model.**

# com.google.protobuf annotation types (ProtoField, ProtoPresenceBits, etc.)
# shipped in protobuf-javalite source but stripped from the runtime AAR
-dontwarn com.google.protobuf.Internal$ProtoMethodMayReturnNull
-dontwarn com.google.protobuf.Internal$ProtoNonnullApi
-dontwarn com.google.protobuf.ProtoField
-dontwarn com.google.protobuf.ProtoPresenceBits
-dontwarn com.google.protobuf.ProtoPresenceCheckedField

# autovalue / javapoet shaded inside autovalue — annotation-processor internals
# referenced via reflection; safe to ignore on Android
-dontwarn autovalue.shaded.**

# ── llama-android JNI ─────────────────────────────────────────────────────────
# LlamaAndroid is the JNI bridge class; its native method names must be kept
# verbatim so the linker can resolve them at runtime via JNI_OnLoad / FindClass.
-keep class com.techducat.llama.LlamaAndroid { *; }
-keepclasseswithmembernames class com.techducat.llama.** {
    native <methods>;
}

# ── MediaPipe LLM Inference ───────────────────────────────────────────────────
# The tasks-genai AAR uses reflection internally; keep its public surface.
-keep class com.google.mediapipe.tasks.genai.** { *; }
-dontwarn com.google.mediapipe.**

# ── UI / Service / Receiver classes (manifest-declared) ──────────────────────
-keep class com.techducat.thot.ui.** { *; }
-keep class com.techducat.thot.chrono.** { *; }
-keep class com.techducat.thot.settings.** { *; }
-keep class com.techducat.thot.remote.** { *; }
-keep class com.techducat.thot.local.** { *; }
-keep class com.techducat.thot.ThotApplication { *; }

# ── BuildConfig fields (referenced by flavor-conditional code) ────────────────
-keep class com.techducat.thot.BuildConfig { *; }

# ── Kotlin ────────────────────────────────────────────────────────────────────
-keep class kotlin.Metadata { *; }
-dontwarn kotlin.**
