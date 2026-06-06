package com.techducat.thot

import android.app.Application
import java.util.concurrent.Executors

/**
 * Application entry point.
 *
 * Firebase / Crashlytics initialisation is intentionally deferred to a
 * background thread so it does not contribute to the main-thread
 * installContentProviders budget at cold start (was: 217 ms blocked).
 *
 * FirebaseApp.initializeApp() is thread-safe; Crashlytics configuration
 * methods are also safe to call off-main after initializeApp completes.
 *
 * Firebase dependencies are only on the classpath for the `playstore` flavor.
 * For `fdroid` builds, IS_FDROID_BUILD=true and this block is skipped entirely,
 * so we must not import Firebase classes at the file level — use reflection.
 */
class ThotApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // Firebase is only available in the playstore flavor.
        // F-Droid builds have no Firebase dependency on the classpath at runtime.
        if (!BuildConfig.IS_FDROID_BUILD) {
            Executors.newSingleThreadExecutor().execute {
                try {
                    val firebaseAppClass = Class.forName("com.google.firebase.FirebaseApp")
                    firebaseAppClass.getMethod("initializeApp", android.content.Context::class.java)
                        .invoke(null, this)

                    val crashlyticsClass =
                        Class.forName("com.google.firebase.crashlytics.FirebaseCrashlytics")
                    val instance = crashlyticsClass.getMethod("getInstance").invoke(null)
                    crashlyticsClass.getMethod("setCrashlyticsCollectionEnabled", Boolean::class.java)
                        .invoke(instance, true)
                    crashlyticsClass.getMethod("setCustomKey", String::class.java, String::class.java)
                        .invoke(instance, "version_name", BuildConfig.VERSION_NAME)
                    crashlyticsClass.getMethod("setCustomKey", String::class.java, Int::class.java)
                        .invoke(instance, "version_code", BuildConfig.VERSION_CODE)
                } catch (e: Exception) {
                    android.util.Log.w("ThotApplication", "Firebase init failed: ${e.message}")
                }
            }
        }
    }
}
