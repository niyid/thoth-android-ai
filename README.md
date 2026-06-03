# Thot — Global AI Assistant for Android

Thot is an OS-level AI assistant that draws a floating button over **every app** on Android. Tap it to get context-aware AI responses — explanations, summaries, drafted replies, and custom tasks — all powered by the app currently on your screen.

---

## How it works

```
Any App  →  AccessibilityService reads visible text
         →  Floating overlay button (WindowManager TYPE_APPLICATION_OVERLAY)
         →  User taps Explain / Summarize / Respond / custom
         →  ThotCoreProvider dispatches to LOCAL / MEDIAPIPE / LLAMACPP / OPENAI / ANTHROPIC backend
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
│   ├── OpenAiClient.kt               ← OkHttp → GPT-4o
│   └── AnthropicClient.kt            ← OkHttp → Claude
├── local/
│   ├── LocalLLMProvider.kt           ← Offline rule-based fallback (placeholder)
│   ├── MediaPipeLLMProvider.kt       ← On-device inference via Google AI Edge / Gemma
│   └── LlamaCppProvider.kt           ← On-device inference via llama.cpp / GGUF models
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

### 2. Set your API key(s)

Keys can be entered at runtime or supplied at build time. You only need the key(s) for the provider(s) you intend to use.

#### At runtime (recommended)
Launch the app, select your provider in the spinner, enter your key, and tap **Save**.

#### At build time (CI-safe)
Add one or both keys to `local.properties` (this file is git-ignored and never included in the APK):
```
OPENAI_API_KEY=sk-your-openai-key-here
ANTHROPIC_API_KEY=sk-ant-your-anthropic-key-here
```
Both keys can also be supplied as environment variables with the same names — useful for CI pipelines where `local.properties` is not checked in.

> **Note:** Build-time keys only pre-seed the app on first launch. Any key you enter manually in the app takes permanent precedence and will not be overwritten.

### 3. On-device providers (free, no API key required)

Both on-device providers are fully wired and ready to use — no code changes needed.

#### MediaPipe / Gemma (Google AI Edge)

Runs Google's Gemma models on-device. Good for general-purpose chat and explanation tasks.

1. Download a compatible `.task` model file (e.g. Gemma 3 1B IT INT4, ~600 MB):
   https://ai.google.dev/edge/mediapipe/solutions/genai/llm_inference/android
2. Copy the file to your device (e.g. `/sdcard/Download/gemma3-1b-it-int4.task`)
3. In Thot Settings: select **MediaPipe / Gemma**, enter the full file path, tap Save

#### llama.cpp / GGUF models

Runs any GGUF-format model (Llama, Mistral, Phi, Qwen, Gemma, etc.) via llama.cpp JNI.

1. Download a `.gguf` model from Hugging Face — recommended starting points:
   - Llama 3.2 1B Instruct Q4_K_M (~800 MB, fast, low RAM)
   - Phi-3 Mini 4K Instruct Q4_K_M (~2 GB, good quality)
   - Mistral 7B Instruct v0.3 Q4_K_M (~4 GB, best quality)
   https://huggingface.co/models?library=gguf
2. Copy the file to your device (e.g. `/sdcard/Download/llama-3.2-1b-instruct-q4_k_m.gguf`)
3. In Thot Settings: select **llama.cpp / GGUF**, enter the full file path, tap Save

### 4. Grant permissions (first launch)

The app will prompt for both:

1. **Overlay permission** — allows the floating button to appear over other apps
   - Settings → Apps → Special app access → Display over other apps → Thot → Allow

2. **Accessibility service** — allows Thot to read screen content
   - Settings → Accessibility → Installed apps → Thot AI Assistant → Enable

### 5. Build and run
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

## On-device providers

Thot supports two free, fully offline LLM providers that run entirely on the device — no API key, no internet, no cost.

### MediaPipe / Gemma (.task models)

1. Download a Gemma `.task` model from:
   https://ai.google.dev/edge/mediapipe/solutions/genai/llm_inference/android
   Recommended: **Gemma 3 1B IT INT4** (~600 MB, fast on mid-range phones)

2. Copy the `.task` file to your device (e.g. `/sdcard/Download/gemma3-1b-it-int4.task`).

3. In Thot → Settings, select **MediaPipe (Gemma)** and enter the full file path. Tap **Save**.

### llama.cpp / GGUF models

> **One-time developer step** (only needed when building from source):
> ```bash
> git submodule update --init --recursive
> ```
> This pulls `third_party/llama.cpp`. The NDK build runs automatically after that.
> NDK version **27.2.12479018** must be installed (`sdkmanager "ndk;27.2.12479018"`).

1. Download a GGUF model from:
   https://huggingface.co/models?library=gguf
   Recommended: **Llama 3.2 1B Instruct Q4_K_M** (~800 MB)

2. Copy the `.gguf` file to your device (e.g. `/sdcard/Download/llama-3.2-1b-instruct-q4_k_m.gguf`).

3. In Thot → Settings, select **llama.cpp (GGUF)** and enter the full file path. Tap **Save**.

