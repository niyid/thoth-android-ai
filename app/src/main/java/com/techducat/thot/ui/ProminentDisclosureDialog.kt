package com.techducat.thot.ui

import android.content.Context
import android.os.Build
import android.text.Html
import android.text.Spanned
import android.view.LayoutInflater
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.techducat.thot.R
import com.techducat.thot.databinding.DialogProminentDisclosureBinding

/**
 * ProminentDisclosureDialog
 * ─────────────────────────
 * Displays a mandatory, prominent disclosure to the user BEFORE they are
 * directed to enable the Accessibility Service in system settings.
 *
 * Google Play policy (User Data policy / AccessibilityService requirements)
 * mandates that apps:
 *   1. Prominently disclose WHY the Accessibility Service is needed.
 *   2. Declare WHAT personal/sensitive data is collected through it.
 *   3. Explain HOW that data is used and shared.
 *   4. Obtain explicit user acknowledgement BEFORE enabling the service.
 *
 * Usage:
 *   ProminentDisclosureDialog.show(context) {
 *       // called only when the user taps "I Understand — Enable"
 *       openAccessibilitySettings()
 *   }
 *
 * The user's acceptance is persisted in SharedPreferences so the dialog is
 * only shown once (re-shown if preferences are cleared / app is reinstalled).
 */
object ProminentDisclosureDialog {

    private const val PREFS_NAME  = "thot_disclosure_prefs"
    private const val KEY_ACCEPTED = "prominent_disclosure_accepted"

    /**
     * Returns true if the user has already accepted the disclosure in a
     * previous session. Use this to skip showing the dialog on subsequent
     * launches while still gating the accessibility settings redirect.
     */
    fun hasBeenAccepted(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_ACCEPTED, false)
    }

    /**
     * Show the disclosure dialog.
     *
     * @param context       An Activity or Application context.
     * @param onAccepted    Lambda invoked when the user taps "I Understand — Enable".
     *                      Open Accessibility Settings inside this lambda.
     */
    fun show(context: Context, onAccepted: () -> Unit) {
        val inflater = LayoutInflater.from(context)
        val binding  = DialogProminentDisclosureBinding.inflate(inflater)

        // Render HTML-formatted body text
        binding.tvDisclosureBody.text = fromHtml(
            context.getString(R.string.disclosure_body)
        )

        val dialog = MaterialAlertDialogBuilder(context)
            .setView(binding.root)
            // Prevent accidental dismissal by tapping outside
            .setCancelable(false)
            .create()

        binding.btnDisclosureProceed.setOnClickListener {
            // Persist acceptance so we don't nag the user again
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_ACCEPTED, true)
                .apply()

            dialog.dismiss()
            onAccepted()
        }

        binding.btnDisclosureCancel.setOnClickListener {
            dialog.dismiss()
            // User declined — do not open accessibility settings
        }

        dialog.show()
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    @Suppress("DEPRECATION")
    private fun fromHtml(source: String): Spanned =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
            Html.fromHtml(source, Html.FROM_HTML_MODE_LEGACY)
        else
            Html.fromHtml(source)
}
