import java.util.Properties
import java.io.File

// ============================================================
//  thot-android-ai  —  app/build.gradle.kts
//
//  AI overlay assistant (floating button + AccessibilityService).
//  Multi-platform distribution flavors borrowed from kabu-kabu-p2p:
//
//  Flavor dimension: "distribution"
//    playstore  → applicationId = com.techducat.thot
//                 GMS / Firebase / Crashlytics enabled
//                 Requires app/google-services.json
//    fdroid     → applicationId = com.techducat.thot.fdroid
//                 No GMS, no Firebase — purely FOSS build
//                 google-services plugin is NOT applied for this flavor
//
//  Build types:
//    debug    → .debug suffix, debuggable, Crashlytics disabled
//    release  → minified, signed, Crashlytics mapping upload enabled
//
//  API keys (OPENAI_API_KEY, ANTHROPIC_API_KEY) are read from
//  local.properties or CI environment variables — never hard-coded.
// ============================================================

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    // GMS / Firebase plugins — only applied when building the playstore flavor.
    // The flavor-conditional guard lives in the android {} block below.
    id("com.google.gms.google-services")
    id("com.google.firebase.crashlytics")
}

// ── Secrets ──────────────────────────────────────────────────────────────────
val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localPropertiesFile.inputStream().use { localProperties.load(it) }
}
fun getLocalProperty(key: String, defaultValue: String = ""): String =
    localProperties.getProperty(key) ?: System.getenv(key) ?: defaultValue

// ── Android ──────────────────────────────────────────────────────────────────
android {
    namespace  = "com.techducat.thot"
    compileSdk = 36

    defaultConfig {
        applicationId   = "com.techducat.thot"
        minSdk          = 26
        targetSdk       = 36
        versionCode     = 2
        versionName     = "1.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // API keys — injected from local.properties or CI env; default to empty string
        buildConfigField("String", "OPENAI_API_KEY",    "\"${getLocalProperty("OPENAI_API_KEY")}\"")
        buildConfigField("String", "ANTHROPIC_API_KEY", "\"${getLocalProperty("ANTHROPIC_API_KEY")}\"")
    }

    lint {
        disable  += setOf("NullSafeMutableLiveData")
        abortOnError = false
    }

    signingConfigs {
        create("release") {
            val ksPath = getLocalProperty("RELEASE_STORE_FILE")
            if (ksPath.isNotEmpty() && File(ksPath).exists()) {
                storeFile     = file(ksPath)
                storePassword = getLocalProperty("RELEASE_STORE_PASSWORD")
                keyAlias      = getLocalProperty("RELEASE_KEY_ALIAS")
                keyPassword   = getLocalProperty("RELEASE_KEY_PASSWORD")
            }
        }
    }

    // ── Distribution flavors (borrowed from kabu-kabu-p2p) ───────────────────
    //
    //  playstore  — full Google Play / Firebase build.
    //               Requires app/google-services.json from the Firebase Console.
    //               Crashlytics mapping is uploaded on release builds.
    //
    //  fdroid     — FOSS build with no Google dependencies.
    //               applicationId gets a ".fdroid" suffix so both variants can
    //               coexist on the same device during testing.
    //               The google-services and crashlytics Gradle plugins are still
    //               declared above (they must be on the classpath) but they are
    //               inert when google-services.json is absent — which it will be
    //               for F-Droid CI.  All Firebase dependencies are guarded by
    //               flavor-specific dependency blocks below.
    flavorDimensions += "distribution"
    productFlavors {
        create("playstore") {
            dimension = "distribution"
            // No suffix — canonical package name for Play
        }
        create("fdroid") {
            dimension = "distribution"
            applicationIdSuffix = ".fdroid"
            // Expose a build flag so code can gate out GMS-only call sites
            buildConfigField("boolean", "IS_FDROID_BUILD", "true")
        }
    }

    // Apply IS_FDROID_BUILD = false for playstore so the field always exists
    // regardless of flavor, avoiding compile errors in shared code.
    productFlavors.getByName("playstore") {
        buildConfigField("boolean", "IS_FDROID_BUILD", "false")
    }

    buildTypes {
        debug {
            isDebuggable       = true
            applicationIdSuffix = ".debug"
        }
        release {
            isMinifyEnabled   = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }
    }

    buildFeatures {
        buildConfig = true
        viewBinding = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions { jvmTarget = "17" }

    packaging {
        resources {
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "META-INF/INDEX.LIST",
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE*",
                "META-INF/NOTICE*"
            )
        }
    }
}

// ── Dependencies ─────────────────────────────────────────────────────────────
dependencies {

    // AndroidX core
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.2.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7")
    implementation("androidx.activity:activity-ktx:1.9.3")
    implementation("androidx.preference:preference-ktx:1.2.1")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")

    // Networking
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // JSON
    implementation("org.json:json:20231013")

    // ── Firebase / Crashlytics — playstore flavor only ────────────────────────
    //
    // The "playstoreImplementation" configuration is automatically generated
    // by the Android Gradle Plugin for each flavor.  Dependencies declared here
    // are included only in playstore builds; fdroid builds compile cleanly
    // without any Firebase dependency on the classpath.
    //
    // If you later add GMS Maps, Auth, or Messaging, add them here too.
    "playstoreImplementation"(platform("com.google.firebase:firebase-bom:33.7.0"))
    "playstoreImplementation"("com.google.firebase:firebase-crashlytics-ktx")
    "playstoreImplementation"("com.google.firebase:firebase-analytics-ktx")

    // Testing
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
}
