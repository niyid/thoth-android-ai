# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase

# Keep Thot core public API
-keep class com.techducat.thot.core.** { *; }
-keep class com.techducat.thot.accessibility.ThotAccessibilityService { *; }
-keep class com.techducat.thot.accessibility.ThotOverlayService { *; }

# Keep Kotlin coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
