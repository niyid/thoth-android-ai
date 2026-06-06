// ============================================================
//  llama-android — NDK module
//
//  Builds llama.cpp from source and exposes a Kotlin JNI
//  wrapper class (LlamaAndroid) for use by :app.
//
//  Prerequisites:
//    • NDK installed in Android Studio (SDK Manager → NDK)
//    • llama.cpp sources at src/main/cpp/llama.cpp (and headers)
//    • CMakeLists.txt at src/main/cpp/CMakeLists.txt
//
//  To add llama.cpp sources:
//    git submodule add https://github.com/ggml-org/llama.cpp \
//        llama-android/src/main/cpp/llama.cpp
//  or copy the files manually.
// ============================================================

plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace  = "com.techducat.llama"
    compileSdk = 36
    ndkVersion = "29.0.13599879"

    defaultConfig {
        minSdk = 26

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        externalNativeBuild {
            cmake {
                cppFlags += listOf("-O3", "-DNDEBUG")
                arguments += listOf(
                    "-DLLAMA_BUILD_TESTS=OFF",
                    "-DLLAMA_BUILD_EXAMPLES=OFF",
                    "-DLLAMA_BUILD_SERVER=OFF"
                )
            }
        }

        ndk {
            // Only build for 64-bit ABIs; 32-bit devices lack the RAM for LLMs.
            abiFilters += listOf("arm64-v8a", "x86_64")
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation("androidx.core:core-ktx:1.17.0")
}
