// ============================================================
//  :llama-android
//
//  Android library module that builds llama.cpp from source
//  via the NDK and exposes a minimal Kotlin API.
//
//  Prerequisites:
//    1. Run:  git submodule update --init --recursive
//       This pulls third_party/llama.cpp (the official repo).
//    2. Android NDK must be installed (SDK Manager → SDK Tools → NDK).
//       The version declared in ndkVersion below is enforced; install it
//       via Android Studio or:
//         sdkmanager "ndk;29.0.13113456"
//
//  After the submodule is present, Gradle builds the native .so files
//  automatically as part of the normal assemble task.
// ============================================================

plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace  = "com.techducat.llama"
    compileSdk = 36

    ndkVersion = "29.0.13113456"

    defaultConfig {
        minSdk = 26

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")

        externalNativeBuild {
            cmake {
                // Build for all common ABIs.  Remove armeabi-v7a / x86 to shrink the APK.
                abiFilters += setOf("arm64-v8a", "armeabi-v7a", "x86_64")
                // Pass the llama.cpp source root to CMake.
                arguments(
                    "-DLLAMA_SOURCE_DIR=${rootProject.projectDir}/third_party/llama.cpp"
                )
                cppFlags("-std=c++17")
            }
        }
    }

    externalNativeBuild {
        cmake {
            path   = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions { jvmTarget = "17" }

    buildFeatures { buildConfig = false }
}

dependencies {
    implementation("androidx.core:core-ktx:1.17.0")
}
