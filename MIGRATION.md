# Thot — Multi-Platform Build Migration

Borrowed the distribution-flavor pattern from **kabu-kabu-p2p** so Thot
can target Google Play and alternative stores (F-Droid, sideload APK,
future stores) from a single codebase.

---

## Files changed

| File | Change |
|---|---|
| `build.gradle` → `build.gradle.kts` | Groovy → Kotlin DSL; plugin versions pinned |
| `settings.gradle` → `settings.gradle.kts` | Groovy → Kotlin DSL; `pluginManagement` + `dependencyResolutionManagement` blocks |
| `gradle.properties` | Raised JVM heap to 4 GB; enabled configuration cache; dropped Jetifier (not needed for pure AndroidX project) |
| `app/build.gradle` → `app/build.gradle.kts` | Full KTS migration + all changes below |

---

## What was added to `app/build.gradle.kts`

### 1. Secrets helper (from Kabu-Kabu)
```kotlin
val localProperties = Properties()
...
fun getLocalProperty(key: String, defaultValue: String = ""): String =
    localProperties.getProperty(key) ?: System.getenv(key) ?: defaultValue
```
API keys (`OPENAI_API_KEY`, `ANTHROPIC_API_KEY`) and signing credentials
are now read from `local.properties` **or** CI environment variables —
not from `project.findProperty(...)` which silently returns null in some
Gradle configurations.

### 2. Release signing config (from Kabu-Kabu)
```kotlin
signingConfigs {
    create("release") { ... }
}
```
Set `RELEASE_STORE_FILE`, `RELEASE_STORE_PASSWORD`, `RELEASE_KEY_ALIAS`,
`RELEASE_KEY_PASSWORD` in `local.properties` (never commit this file) or
in your CI secrets.

### 3. Distribution flavor dimension (from Kabu-Kabu)
```kotlin
flavorDimensions += "distribution"
productFlavors {
    create("playstore") { dimension = "distribution" }
    create("fdroid")    { dimension = "distribution"; applicationIdSuffix = ".fdroid" }
}
```

This creates four build variants:
| Variant | applicationId | Firebase |
|---|---|---|
| `playstoreDebug` | `com.techducat.thot.debug` | ✗ |
| `playstoreRelease` | `com.techducat.thot` | ✓ |
| `fdroidDebug` | `com.techducat.thot.fdroid.debug` | ✗ |
| `fdroidRelease` | `com.techducat.thot.fdroid` | ✗ |

### 4. `IS_FDROID_BUILD` build config field
Both flavors expose `BuildConfig.IS_FDROID_BUILD` (boolean), so you can
gate out GMS-only code at compile time:
```kotlin
if (!BuildConfig.IS_FDROID_BUILD) {
    // e.g. log to Firebase Crashlytics
}
```

### 5. Flavor-scoped Firebase dependencies
```kotlin
"playstoreImplementation"(platform("com.google.firebase:firebase-bom:33.7.0"))
"playstoreImplementation"("com.google.firebase:firebase-crashlytics-ktx")
"playstoreImplementation"("com.google.firebase:firebase-analytics-ktx")
```
Firebase is **not** on the compile classpath for `fdroid` builds.
The `google-services` and `crashlytics` Gradle plugins are still declared
(they must be on the classpath), but they are inert when
`app/google-services.json` is absent, which is the expected state for
F-Droid CI.

### 6. SDK / dependency bumps
| Dependency | Before | After |
|---|---|---|
| `compileSdk` / `targetSdk` | 34 | 36 |
| `firebase-bom` | 32.7.4 | 33.7.0 |
| `core-ktx` | 1.12.0 | 1.17.0 |
| `appcompat` | 1.6.1 | 1.7.1 |
| `material` | 1.11.0 | 1.12.0 |
| `constraintlayout` | 2.1.4 | 2.2.0 |
| `lifecycle-*` | 2.7.0 | 2.8.7 |
| `activity-ktx` | 1.8.2 | 1.9.3 |
| `coroutines-android` | 1.7.3 | 1.10.2 |

---

## Drop-in instructions

1. **Replace files:**
   ```
   build.gradle          → build.gradle.kts        (root)
   settings.gradle       → settings.gradle.kts
   gradle.properties     → gradle.properties        (overwrite)
   app/build.gradle      → app/build.gradle.kts
   ```

2. **Rename** `app/build.gradle` to `app/build.gradle.kts` (or delete the
   old `.gradle` file — Gradle will pick up the `.kts` version).

3. **Create `local.properties`** (root of project, never committed):
   ```properties
   OPENAI_API_KEY=sk-...
   ANTHROPIC_API_KEY=sk-ant-...
   RELEASE_STORE_FILE=/path/to/thot-release.jks
   RELEASE_STORE_PASSWORD=...
   RELEASE_KEY_ALIAS=thot
   RELEASE_KEY_PASSWORD=...
   ```

4. **Google Play builds** still require `app/google-services.json` from
   the Firebase Console. F-Droid builds do not need it.

5. **Build commands:**
   ```bash
   # Play Store release APK
   ./gradlew assemblePlaystoreRelease

   # Play Store AAB (for Play Console upload)
   ./gradlew bundlePlaystoreRelease

   # F-Droid release APK
   ./gradlew assembleFdroidRelease

   # All debug variants
   ./gradlew assembleDebug
   ```

---

## Adding more stores later

The pattern extends cleanly. To add an Amazon Appstore flavor:
```kotlin
create("amazon") {
    dimension = "distribution"
    applicationIdSuffix = ".amazon"
    buildConfigField("boolean", "IS_FDROID_BUILD", "false")
    // Add any Amazon-specific SDK dependency with "amazonImplementation(...)"
}
```
