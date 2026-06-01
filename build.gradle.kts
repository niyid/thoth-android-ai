// ============================================================
//  thot-android-ai  —  build.gradle.kts  (root)
//
//  Migrated from Groovy build.gradle → Kotlin DSL (.kts)
//  Multi-platform flavor system borrowed from kabu-kabu-p2p:
//    "playstore"  → com.techducat.thot          (Google Play)
//    "fdroid"     → com.techducat.thot.fdroid    (F-Droid / sideload)
//  Firebase/GMS plugins are applied only for the playstore flavor.
// ============================================================

plugins {
    id("com.android.application") version "8.10.1" apply false
    id("org.jetbrains.kotlin.android") version "2.1.21" apply false
    id("com.google.gms.google-services") version "4.4.2" apply false
    id("com.google.firebase.crashlytics") version "3.0.3" apply false
}
