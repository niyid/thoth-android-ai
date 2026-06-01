package com.techducat.thot.settings

import android.content.Context
import androidx.core.content.edit
import com.techducat.thot.core.ProviderType

/**
 * Single source of truth for all persisted Thot settings.
 */
class ThotPreferences(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var openAiApiKey: String
        get() = prefs.getString(KEY_API_KEY, "") ?: ""
        set(value) = prefs.edit { putString(KEY_API_KEY, value) }

    var provider: ProviderType
        get() = ProviderType.fromString(prefs.getString(KEY_PROVIDER, ProviderType.LOCAL.name) ?: "")
        set(value) = prefs.edit { putString(KEY_PROVIDER, value.name) }

    var overlayEnabled: Boolean
        get() = prefs.getBoolean(KEY_OVERLAY, true)
        set(value) = prefs.edit { putBoolean(KEY_OVERLAY, value) }

    companion object {
        private const val PREFS_NAME = "thot_settings"
        private const val KEY_API_KEY = "openai_api_key"
        private const val KEY_PROVIDER = "provider"
        private const val KEY_OVERLAY = "overlay_enabled"
    }
}
