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
    // GMS / Firebase plugins — only applied when google-services.json is present
    // (i.e., playstore builds). F-Droid builds omit google-services.json so these
    // plugins skip processing and produce no output. The plugins must still be
    // declared here (not in root) because the google-services plugin processes
    // resources during the variant-aware configuration phase.
    id("com.google.gms.google-services")
    id("com.google.firebase.crashlytics")
}

// Guard: skip google-services / crashlytics processing entirely if
// google-services.json is absent (fdroid CI, contributors without Firebase access).
// The plugin declarations above are needed so the plugin classes are on the
// classpath; this suppresses their active processing when the config file is missing.
val googleServicesJsonFile = file("google-services.json")
if (!googleServicesJsonFile.exists()) {
    // Disable Crashlytics mapping upload tasks — they will fail without the JSON.
    tasks.configureEach {
        if (name.contains("uploadCrashlyticsMappingFile", ignoreCase = true) ||
            name.contains("injectCrashlyticsMapping", ignoreCase = true)) {
            enabled = false
        }
    }
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
    ndkVersion = "29.0.13113456"

    defaultConfig {
        applicationId   = "com.techducat.thot"
        minSdk          = 26
        targetSdk       = 36
        versionCode     = 10
        versionName     = "0.1.0"

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
        jniLibs {
            // Required for 16KB page-size alignment to take effect at install time.
            // false = .so files stored uncompressed in the APK so the OS can mmap them
            // directly at the correct alignment boundary. true (legacy) would compress
            // them, defeating the ELF p_align patch entirely.
            useLegacyPackaging = false
        }
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

// ── 16KB ELF Alignment ───────────────────────────────────────────────────────
//
// WHY: Android devices with 16KB page sizes (Pixel 9+, future ARM SoCs) require
// ELF PT_LOAD segments to be aligned to 16384 bytes. Google Play will flag APKs
// whose .so files are not aligned and will block them from 16KB-page devices.
//
// HOW: align_elf.py (from ~/git/server_extras/) patches PT_LOAD p_align to 16384.
//      Hooks into stripDebugSymbols so patching happens after stripping, before
//      both APK packaging AND AAB bundling.
//
// NOTE: useLegacyPackaging = false (set above in jniLibs) is equally required.
// The ELF p_align patch is useless if .so files are compressed in the APK/AAB,
// because the OS cannot mmap them directly — it extracts them first, losing alignment.
// Both fixes together = correct 16KB support.
//
// APPLIES TO: llama_android.so (built by :llama-android NDK module)
//             and any MediaPipe .so files bundled via tasks-genai AAR.

val alignElfPy = "${System.getProperty("user.home")}/git/server_extras/align_elf.py"

val archsToProcess = listOf("arm64-v8a", "x86_64")

tasks.whenTaskAdded {
    if (name.startsWith("strip") && name.contains("DebugSymbol")) {
        doLast {
            println("=== Checking and realigning native libraries for 16KB page size ===")

            val alignScript = File(alignElfPy)
            if (!alignScript.exists()) {
                println("ERROR: align_elf.py not found at $alignElfPy")
                println("Please ensure align_elf.py exists in ~/git/server_extras/")
                return@doLast
            }

            val strippedLibsDir = File(project.layout.buildDirectory.get().asFile, "intermediates/stripped_native_libs")

            var filesChecked = 0
            var filesAligned = 0
            var filesSkipped = 0

            fun readElfLoadAlignment(file: File): Long {
                try {
                    val bytes = file.readBytes()
                    if (bytes.size < 64 || bytes[0] != 0x7F.toByte() ||
                        bytes[1] != 0x45.toByte() || bytes[2] != 0x4C.toByte() || bytes[3] != 0x46.toByte()) {
                        return -1L
                    }
                    val is64bit = bytes[4] == 0x02.toByte()
                    val isLE    = bytes[5] == 0x01.toByte()

                    fun readU16(offset: Int): Int {
                        val a = bytes[offset].toInt() and 0xFF
                        val b = bytes[offset + 1].toInt() and 0xFF
                        return if (isLE) a or (b shl 8) else (a shl 8) or b
                    }
                    fun readU32(offset: Int): Long {
                        var v = 0L
                        for (i in 0..3) {
                            val b = bytes[offset + i].toLong() and 0xFF
                            v = if (isLE) v or (b shl (i * 8)) else (v shl 8) or b
                        }
                        return v
                    }
                    fun readU64(offset: Int): Long {
                        var v = 0L
                        for (i in 0..7) {
                            val b = bytes[offset + i].toLong() and 0xFF
                            v = if (isLE) v or (b shl (i * 8)) else (v shl 8) or b
                        }
                        return v
                    }

                    val phoff     = if (is64bit) readU64(32).toInt() else readU32(28).toInt()
                    val phentsize = readU16(if (is64bit) 54 else 42)
                    val phnum     = readU16(if (is64bit) 56 else 44)

                    val PT_LOAD = 1L
                    for (i in 0 until phnum) {
                        val phBase = phoff + i * phentsize
                        if (phBase + phentsize > bytes.size) break
                        val pType = readU32(phBase)
                        if (pType == PT_LOAD) {
                            return if (is64bit) readU64(phBase + 48) else readU32(phBase + 28)
                        }
                    }
                } catch (e: Exception) {
                    // Not readable as ELF
                }
                return -1L
            }

            if (strippedLibsDir.exists()) {
                project.fileTree(strippedLibsDir) {
                    include("**/*.so")
                }.forEach { file ->
                    if (archsToProcess.any { file.path.contains("/$it/") }) {
                        filesChecked++
                        val currentAlign = readElfLoadAlignment(file)
                        when {
                            currentAlign == -1L -> {
                                println("  ⚠ Skipping (not ELF): ${file.name} (${file.parentFile.name})")
                                filesSkipped++
                            }
                            currentAlign >= 16384L -> {
                                println("  ✓ Already aligned ($currentAlign): ${file.name} (${file.parentFile.name})")
                                filesSkipped++
                            }
                            else -> {
                                println("  ↻ Needs alignment ($currentAlign → 16384): ${file.name} (${file.parentFile.name})")
                                try {
                                    val tempFile = File("${file.absolutePath}.tmp")
                                    val processBuilder = ProcessBuilder(
                                        "python3",
                                        alignScript.absolutePath,
                                        file.absolutePath,
                                        tempFile.absolutePath
                                    )
                                    val process = processBuilder.start()
                                    val exitCode = process.waitFor()

                                    if (exitCode == 0 && tempFile.exists()) {
                                        file.delete()
                                        tempFile.renameTo(file)
                                        println("    ✓ Aligned successfully")
                                        filesAligned++
                                    } else {
                                        println("    ✗ Alignment failed")
                                        if (tempFile.exists()) tempFile.delete()
                                    }
                                } catch (e: Exception) {
                                    println("    ✗ Error: ${e.message}")
                                }
                            }
                        }
                    }
                }
            }

            println("=== Realignment complete: $filesChecked checked, $filesAligned patched, $filesSkipped skipped ===")
        }
    }
}
// ── END 16KB ELF Alignment ────────────────────────────────────────────────────

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

    // ── On-device LLM providers ───────────────────────────────────────────────
    // MediaPipe LLM Inference (Google AI Edge / Gemma .task models)
    implementation("com.google.mediapipe:tasks-genai:0.10.22")
    // llama.cpp built from source via NDK — see :llama-android module
    implementation(project(":llama-android"))
    // ─────────────────────────────────────────────────────────────────────────

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
    "playstoreImplementation"(platform("com.google.firebase:firebase-bom:33.14.0"))
    "playstoreImplementation"("com.google.firebase:firebase-crashlytics-ktx")
    "playstoreImplementation"("com.google.firebase:firebase-analytics-ktx")

    // Testing
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
}
