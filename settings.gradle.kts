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
        // JitPack removed — llama.cpp is now built from source via :llama-android NDK module
    }
}

rootProject.name = "Thot"
include(":app")
include(":llama-android")
