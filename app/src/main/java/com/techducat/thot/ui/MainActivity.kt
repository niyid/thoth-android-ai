package com.techducat.thot.ui

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.techducat.thot.R
import com.techducat.thot.BuildConfig
import com.techducat.thot.accessibility.ThotAccessibilityService
import com.techducat.thot.core.ProviderType
import com.techducat.thot.core.ThotCoreProvider
import com.techducat.thot.core.ThotTask
import com.techducat.thot.databinding.ActivityMainBinding
import com.techducat.thot.settings.ThotPreferences
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.techducat.thot.ui.ProminentDisclosureDialog

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: ThotPreferences
    private lateinit var thotProvider: ThotCoreProvider

    // Launcher for POST_NOTIFICATIONS permission (Android 13+)
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* result noted; overlay service notification will show if granted */ }

    // Launcher for picking a GGUF / .task model file from device storage
    private val modelFilePickerLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@registerForActivityResult
        // Persist read permission so the path survives reboots
        contentResolver.takePersistableUriPermission(
            uri,
            android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
        )
        binding.etLocalModelPath.setText(uri.toString())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = ThotPreferences(this)
        thotProvider = ThotCoreProvider(this)

        requestNotificationPermissionIfNeeded()
        setupProviderSpinner()
        loadSettings()
        setupListeners()
    }

    override fun onResume() {
        super.onResume()
        updatePermissionStatus()
    }

    override fun onDestroy() {
        super.onDestroy()
        thotProvider.cancel()
    }

    // ── Setup ────────────────────────────────────────────────────────────────

    private fun setupProviderSpinner() {
        val options = resources.getStringArray(R.array.provider_options)
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, options)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerProvider.adapter = adapter
    }

    private fun loadSettings() {
        binding.etApiKey.setText(prefs.openAiApiKey)
        binding.etAnthropicApiKey.setText(prefs.anthropicApiKey)
        binding.etLocalModelPath.setText(prefs.localModelPath)
        binding.switchOverlay.isChecked = prefs.overlayEnabled
        binding.spinnerProvider.setSelection(
            when (prefs.provider) {
                ProviderType.LOCAL -> 0
                ProviderType.OPENAI -> 1
                ProviderType.ANTHROPIC -> 2
                ProviderType.MEDIAPIPE -> 3
                ProviderType.LLAMACPP -> 4
            }
        )
        updateApiKeyVisibility(binding.spinnerProvider.selectedItemPosition)
    }

    private fun setupListeners() {
        binding.btnGrantOverlay.setOnClickListener { requestOverlayPermission() }
        binding.btnOpenAccessibility.setOnClickListener { showAccessibilityDisclosure() }
        binding.btnSave.setOnClickListener { saveSettings() }
        binding.btnTest.setOnClickListener { runTest() }
        binding.btnBrowseModel.setOnClickListener {
            // Open the system file picker; accept any file type so the user
            // can navigate to .gguf or .task files regardless of MIME type.
            modelFilePickerLauncher.launch(arrayOf("*/*"))
        }
        binding.btnDownloadLlama1b.setOnClickListener {
            openUrl(getString(R.string.url_dl_llama_1b))
        }
        binding.btnDownloadLlama3b.setOnClickListener {
            openUrl(getString(R.string.url_dl_llama_3b))
        }
        binding.btnDownloadPhi3.setOnClickListener {
            openUrl(getString(R.string.url_dl_phi3))
        }

        binding.spinnerProvider.onItemSelectedListener =
            object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: android.widget.AdapterView<*>?,
                    view: View?,
                    position: Int,
                    id: Long
                ) {
                    updateApiKeyVisibility(position)
                }
                override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
            }
    }

    private fun updateApiKeyVisibility(position: Int) {
        binding.tilApiKey.visibility = if (position == 1) View.VISIBLE else View.GONE
        binding.tilAnthropicApiKey.visibility = if (position == 2) View.VISIBLE else View.GONE
        val showLocalPath = position == 3 || position == 4
        binding.tilLocalModelPath.visibility = if (showLocalPath) View.VISIBLE else View.GONE
        binding.btnBrowseModel.visibility = if (showLocalPath) View.VISIBLE else View.GONE
        // Show download shortcuts only for llama.cpp (position 4), where a manual
        // model file is required. MediaPipe uses .task files which aren't on HF.
        val showDownloads = position == 4
        binding.tvDownloadLabel.visibility = if (showDownloads) View.VISIBLE else View.GONE
        binding.btnDownloadLlama1b.visibility = if (showDownloads) View.VISIBLE else View.GONE
        binding.btnDownloadLlama3b.visibility = if (showDownloads) View.VISIBLE else View.GONE
        binding.btnDownloadPhi3.visibility = if (showDownloads) View.VISIBLE else View.GONE
    }

    // ── Permissions ──────────────────────────────────────────────────────────

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val permission = android.Manifest.permission.POST_NOTIFICATIONS
            if (ContextCompat.checkSelfPermission(this, permission)
                != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(permission)
            }
        }
    }

    private fun updatePermissionStatus() {
        val overlayOk = Settings.canDrawOverlays(this)
        val accessibilityOk = ThotAccessibilityService.isRunning

        binding.tvStatusOverlay.text =
            if (overlayOk) "✅ Overlay: granted" else "❌ Overlay: not granted"
        binding.tvStatusAccessibility.text =
            if (accessibilityOk) "✅ Accessibility: enabled" else "❌ Accessibility: not enabled"

        binding.btnGrantOverlay.isEnabled = !overlayOk
        binding.btnOpenAccessibility.isEnabled = !accessibilityOk
    }

    private fun requestOverlayPermission() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        )
        startActivity(intent)
    }

    /**
     * Gate for enabling the Accessibility Service.
     *
     * Google Play requires a prominent disclosure to be shown BEFORE the user
     * is sent to system Accessibility Settings. The dialog explains:
     *   • Why the AccessibilityService API is used (screen reading for AI context).
     *   • What data is collected (visible text + foreground app name).
     *   • How the data is used (sent to the selected AI provider on demand).
     * Only after the user explicitly accepts are they directed to system settings.
     */
    private fun showAccessibilityDisclosure() {
        if (ProminentDisclosureDialog.hasBeenAccepted(this)) {
            // User already accepted in a previous session — go straight to settings
            openAccessibilitySettings()
        } else {
            ProminentDisclosureDialog.show(this) {
                openAccessibilitySettings()
            }
        }
    }

    private fun openAccessibilitySettings() {
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    // ── Settings ─────────────────────────────────────────────────────────────

    private fun saveSettings() {
        prefs.openAiApiKey = binding.etApiKey.text?.toString()?.trim() ?: ""
        prefs.anthropicApiKey = binding.etAnthropicApiKey.text?.toString()?.trim() ?: ""
        prefs.localModelPath = binding.etLocalModelPath.text?.toString()?.trim() ?: ""
        prefs.provider = when (binding.spinnerProvider.selectedItemPosition) {
            1 -> ProviderType.OPENAI
            2 -> ProviderType.ANTHROPIC
            3 -> ProviderType.MEDIAPIPE
            4 -> ProviderType.LLAMACPP
            else -> ProviderType.LOCAL
        }
        val overlayEnabled = binding.switchOverlay.isChecked
        prefs.overlayEnabled = overlayEnabled

        // Start or stop the overlay service based on the toggle.
        // FIX (crash 2 companion): only start the service when the overlay
        // permission is already granted; otherwise the service crashes trying
        // to call WindowManager.addView() with TYPE_APPLICATION_OVERLAY.
        val overlayIntent = Intent(this, com.techducat.thot.accessibility.ThotOverlayService::class.java)
        if (overlayEnabled) {
            if (android.provider.Settings.canDrawOverlays(this)) {
                startService(overlayIntent)
            } else {
                Toast.makeText(
                    this,
                    "Please grant the \"Display over other apps\" permission first.",
                    Toast.LENGTH_LONG
                ).show()
                binding.switchOverlay.isChecked = false
                prefs.overlayEnabled = false
            }
        } else {
            stopService(overlayIntent)
        }

        Toast.makeText(this, getString(R.string.toast_settings_saved), Toast.LENGTH_SHORT).show()
        // Discard any cached on-device model so the next inference reloads from the new path.
        thotProvider.resetLocalProviders()
    }

    // ── Downloads ────────────────────────────────────────────────────────────

    /** Open [url] in the device browser so the user can download a model file. */
    private fun openUrl(url: String) {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }

    // ── Test ─────────────────────────────────────────────────────────────────

    private fun runTest() {
        saveSettings()

        binding.cardTestResult.visibility = View.VISIBLE
        binding.tvTestResult.text = getString(R.string.status_thinking)
        binding.btnTest.isEnabled = false

        val task = ThotTask(
            prompt = "Introduce yourself in one sentence. You are Thot, a mobile AI assistant.",
            provider = prefs.provider
        )

        thotProvider.runTask(task) { result ->
            binding.tvTestResult.text = result
            binding.btnTest.isEnabled = true
            // Report provider errors as non-fatals so they appear in Crashlytics
            if (result.startsWith("Error:") && !BuildConfig.IS_FDROID_BUILD) {
                FirebaseCrashlytics.getInstance().apply {
                    setCustomKey("provider", prefs.provider.name)
                    recordException(RuntimeException("Test LLM call failed: $result"))
                }
            }
        }
    }
}
