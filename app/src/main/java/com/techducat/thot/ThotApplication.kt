package com.techducat.thot

import android.app.Application
import com.google.firebase.FirebaseApp
import com.google.firebase.crashlytics.FirebaseCrashlytics

/**
 * Application entry point.
 *
 * Initialises Firebase / Crashlytics once at process start so that crashes
 * are captured from the very first activity, service, or broadcast receiver.
 *
 * Registration: add  android:name=".ThotApplication"  to <application> in
 * AndroidManifest.xml (already done).
 */
class ThotApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // Firebase is only available in the playstore flavor.
        // F-Droid builds have no Firebase dependency on the classpath at runtime.
        if (!BuildConfig.IS_FDROID_BUILD) {
            FirebaseApp.initializeApp(this)
            FirebaseCrashlytics.getInstance().apply {
                setCrashlyticsCollectionEnabled(true)
                // Tag every report with the app version so you can filter by release
                setCustomKey("version_name", BuildConfig.VERSION_NAME)
                setCustomKey("version_code", BuildConfig.VERSION_CODE)
            }
        }
    }
}
