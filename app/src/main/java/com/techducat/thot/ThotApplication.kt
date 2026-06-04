package com.techducat.thot

import android.app.Application
import com.google.firebase.FirebaseApp
import com.google.firebase.crashlytics.FirebaseCrashlytics
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
 */
class ThotApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // Firebase is only available in the playstore flavor.
        // F-Droid builds have no Firebase dependency on the classpath at runtime.
        if (!BuildConfig.IS_FDROID_BUILD) {
            Executors.newSingleThreadExecutor().execute {
                FirebaseApp.initializeApp(this)
                FirebaseCrashlytics.getInstance().apply {
                    setCrashlyticsCollectionEnabled(true)
                    setCustomKey("version_name", BuildConfig.VERSION_NAME)
                    setCustomKey("version_code", BuildConfig.VERSION_CODE)
                }
            }
        }
    }
}
