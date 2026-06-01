package com.techducat.thot.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.techducat.thot.R
import com.techducat.thot.accessibility.ThotAccessibilityService
import com.techducat.thot.core.ProviderType
import com.techducat.thot.core.ThothCoreProvider
import com.techducat.thot.core.ThothTask
import com.techducat.thot.databinding.ActivityMainBinding
import com.techducat.thot.settings.ThotPreferences
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: ThotPreferences
    private lateinit var thothProvider: ThothCoreProvider

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = ThotPreferences(this)
        thothProvider = ThothCoreProvider(this)

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
        thothProvider.cancel()
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
        binding.spinnerProvider.setSelection(
            if (prefs.provider == ProviderType.LOCAL) 0 else 1
        )
    }

    private fun setupListeners() {
        binding.btnGrantOverlay.setOnClickListener { requestOverlayPermission() }
        binding.btnOpenAccessibility.setOnClickListener { openAccessibilitySettings() }
        binding.btnSave.setOnClickListener { saveSettings() }
        binding.btnTest.setOnClickListener { runTest() }
    }

    // ── Permissions ──────────────────────────────────────────────────────────

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

    private fun openAccessibilitySettings() {
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    // ── Settings ─────────────────────────────────────────────────────────────

    private fun saveSettings() {
        prefs.openAiApiKey = binding.etApiKey.text?.toString()?.trim() ?: ""
        prefs.provider = if (binding.spinnerProvider.selectedItemPosition == 0)
            ProviderType.LOCAL else ProviderType.OPENAI

        Toast.makeText(this, getString(R.string.toast_settings_saved), Toast.LENGTH_SHORT).show()
    }

    // ── Test ─────────────────────────────────────────────────────────────────

    private fun runTest() {
        saveSettings()

        binding.cardTestResult.visibility = View.VISIBLE
        binding.tvTestResult.text = getString(R.string.status_thinking)
        binding.btnTest.isEnabled = false

        val task = ThothTask(
            prompt = "Introduce yourself in one sentence. You are Thot, a mobile AI assistant.",
            provider = prefs.provider
        )

        thothProvider.runTask(task) { result ->
            binding.tvTestResult.text = result
            binding.btnTest.isEnabled = true
        }
    }
}
