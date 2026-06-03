pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // JitPack — required for llama.cpp_Android (on-device GGUF inference)
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "Thot"
include(":app")
