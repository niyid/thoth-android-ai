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
        get() = prefs.getString(KEY_OPENAI_API_KEY, "") ?: ""
        set(value) = prefs.edit { putString(KEY_OPENAI_API_KEY, value) }

    var anthropicApiKey: String
        get() = prefs.getString(KEY_ANTHROPIC_API_KEY, "") ?: ""
        set(value) = prefs.edit { putString(KEY_ANTHROPIC_API_KEY, value) }

    var provider: ProviderType
        get() = ProviderType.fromString(prefs.getString(KEY_PROVIDER, ProviderType.LOCAL.name) ?: "")
        set(value) = prefs.edit { putString(KEY_PROVIDER, value.name) }

    var overlayEnabled: Boolean
        get() = prefs.getBoolean(KEY_OVERLAY, true)
        set(value) = prefs.edit { putBoolean(KEY_OVERLAY, value) }

    /** FAB saved X position (pixels from left edge after snap). */
    var fabX: Int
        get() = prefs.getInt(KEY_FAB_X, 16)
        set(value) = prefs.edit { putInt(KEY_FAB_X, value) }

    /** FAB saved Y position (pixels from top). */
    var fabY: Int
        get() = prefs.getInt(KEY_FAB_Y, 200)
        set(value) = prefs.edit { putInt(KEY_FAB_Y, value) }

    companion object {
        private const val PREFS_NAME = "thot_settings"
        private const val KEY_OPENAI_API_KEY = "openai_api_key"
        private const val KEY_ANTHROPIC_API_KEY = "anthropic_api_key"
        private const val KEY_PROVIDER = "provider"
        private const val KEY_OVERLAY = "overlay_enabled"
        private const val KEY_FAB_X = "fab_x"
        private const val KEY_FAB_Y = "fab_y"
    }
}
