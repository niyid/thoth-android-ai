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

        FirebaseApp.initializeApp(this)

        // Enable crash reporting in release; keep it on in debug too so you
        // can verify the integration, but you can flip this to BuildConfig.DEBUG
        // == false if you prefer a clean Logcat during development.
        FirebaseCrashlytics.getInstance().apply {
            setCrashlyticsCollectionEnabled(true)
            // Tag every report with the app version so you can filter by release
            setCustomKey("version_name", BuildConfig.VERSION_NAME)
            setCustomKey("version_code", BuildConfig.VERSION_CODE)
        }
    }
}
