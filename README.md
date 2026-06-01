# Thot — Global AI Assistant for Android

Thot is an OS-level AI assistant that draws a floating button over **every app** on Android. Tap it to get context-aware AI responses — explanations, summaries, drafted replies, and custom tasks — all powered by the app currently on your screen.

---

## How it works

```
Any App  →  AccessibilityService reads visible text
         →  Floating overlay button (WindowManager TYPE_APPLICATION_OVERLAY)
         →  User taps Explain / Summarize / Respond / custom
         →  ThotCoreProvider dispatches to LOCAL or OPENAI backend
         →  Response shown inline in the overlay panel
```

---

## Project structure

```
app/src/main/java/com/techducat/thot/
├── accessibility/
│   ├── ThotAccessibilityService.kt   ← Reads screen text from all apps
│   ├── ThotOverlayService.kt         ← Floating FAB + action panel
│   └── ScreenContext.kt              (inline in ThotAccessibilityService)
├── core/
│   ├── ThotTask.kt                  ← Task data model + ProviderType enum
│   ├── ThotContextProvider.kt       ← Interface
│   ├── ThotCoreProvider.kt          ← Routes tasks to LOCAL / OPENAI
│   └── BaseThotActivity.kt          ← Optional base class for host activities
├── remote/
│   └── OpenAiClient.kt               ← OkHttp → GPT-4o
├── local/
│   └── LocalLLMProvider.kt           ← Offline rule-based fallback
├── chrono/
│   ├── ChronoScript.kt               ← Schedulable automation unit
│   ├── ChronoManager.kt              ← Coroutine-based scheduler
│   ├── EmailAction.kt                ← Launch email chooser
│   └── SmsAction.kt                  ← Launch SMS app
├── settings/
│   └── ThotPreferences.kt            ← SharedPreferences wrapper
└── ui/
    ├── MainActivity.kt               ← Settings screen + permission setup
    └── PermissionActivity.kt         ← Transparent overlay-permission trampoline
```

---

## Setup

### 1. Import into Android Studio
- File → Open → select the project root folder
- Android Studio Hedgehog (2023.1) or newer recommended

### 2. Set your OpenAI API key
Either:
- **At runtime**: Launch the app → enter your `sk-…` key → tap Save
- **At build time** (CI-safe): add to `local.properties`:
  ```
  OPENAI_API_KEY=sk-your-key-here
  ```

### 3. Grant permissions (first launch)
The app will prompt for both:

1. **Overlay permission** — allows the floating button to appear over other apps
   - Settings → Apps → Special app access → Display over other apps → Thot → Allow

2. **Accessibility service** — allows Thot to read screen content
   - Settings → Accessibility → Installed apps → Thot AI Assistant → Enable

### 4. Build and run
```
./gradlew assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

---

## Permissions explained

| Permission | Why |
|---|---|
| `SYSTEM_ALERT_WINDOW` | Draw the floating button over other apps |
| `BIND_ACCESSIBILITY_SERVICE` | Read visible text from the current app's window |
| `INTERNET` | Send prompts to OpenAI's API |
| `VIBRATE` | Subtle haptic feedback when Thot responds |
| `SEND_SMS` | Optional ChronoManager SMS automation |

---

## ChronoManager — Scheduled Automations

```kotlin
val manager = ChronoManager()

// One-shot: send an email 10 minutes from now
manager.addScript(
    ChronoScript(id = "welcome_email", description = "Send welcome email") {
        EmailAction(context).sendEmail("user@example.com", "Hello", "Welcome!")
    },
    delayMillis = 10 * 60 * 1000L
)

// Repeating: check something every hour
manager.addRepeatingScript(
    ChronoScript(id = "hourly_check", description = "Hourly task") {
        // your suspend work here
    },
    intervalMillis = 60 * 60 * 1000L
)

// Cancel one
manager.disableScript("welcome_email")

// Cancel all (call from onDestroy)
manager.cancelAll()
```

---

## Extending Thot from your own Activity

```kotlin
class MyActivity : BaseThotActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val task = ThotTask(
            prompt = "Summarise the payment flow on this screen.",
            screenContext = "Checkout — Total: ₦45,000 — Pay with card",
            provider = ProviderType.OPENAI
        )

        requestLLMContext(task) { response ->
            // delivered on main thread — update UI directly
            myTextView.text = response
        }
    }
}
```

---

## Wiring a real on-device model

Replace the body of `LocalLLMProvider.handle()` with your model's inference call:

```kotlin
// Example with MediaPipe LLM Inference (Google AI Edge)
class LocalLLMProvider {
    private val llmInference: LlmInference = LlmInference.createFromOptions(
        context,
        LlmInference.LlmInferenceOptions.builder()
            .setModelPath("/data/local/tmp/gemma-2b-it-cpu-int4.bin")
            .setMaxTokens(512)
            .build()
    )

    fun handle(prompt: String): String =
        llmInference.generateResponse(prompt)
}
```

---

## Requirements
- Android 8.0+ (API 26+) — required for `TYPE_APPLICATION_OVERLAY`
- Kotlin 1.9+
- Android Studio Hedgehog+
- OpenAI API key (optional — app works offline with local provider)

---

## Firebase / Crashlytics setup

Thot uses Firebase Crashlytics for crash and non-fatal error reporting.

### One-time project setup

1. Create (or open) a project at [console.firebase.google.com](https://console.firebase.google.com).
2. Add an Android app with package name `com.techducat.thot`.
3. Download the generated `google-services.json` and place it at `app/google-services.json`.
4. Sync Gradle — the `google-services` and `firebase-crashlytics` plugins handle the rest.

### What is reported

| Event | Where |
|---|---|
| Unhandled crashes | Automatic (Crashlytics SDK) |
| LLM provider errors (`Error: …` responses) | `MainActivity` & `ThotOverlayService` via `recordException()` |
| App version & provider name | Custom keys attached to every report |

### Disabling collection (GDPR / user opt-out)

```kotlin
FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(false)
```

Call this before `FirebaseApp.initializeApp()` completes, or persist the user's choice in `ThotPreferences` and apply it in `ThotApplication.onCreate()`.
