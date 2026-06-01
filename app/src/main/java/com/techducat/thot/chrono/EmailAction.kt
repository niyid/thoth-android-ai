package com.techducat.thot.chrono

import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * Launches the system email chooser pre-populated with recipient, subject, and body.
 * (Filename was previously "EmailAcion.kt" — missing 't'.)
 */
class EmailAction(private val context: Context) {

    /**
     * Open a mail client chooser.
     *
     * @param to      Recipient address, e.g. "user@example.com"
     * @param subject Email subject line
     * @param body    Email body text
     */
    fun sendEmail(to: String, subject: String, body: String) {
        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:${to.trim()}")
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, body)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(Intent.createChooser(intent, "Send Email").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }
}
