package com.techducat.thot.chrono

import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * Sends an SMS by launching the system SMS chooser.
 *
 * For background / automated sending without user interaction, you would instead use
 * [android.telephony.SmsManager] and declare SEND_SMS in the manifest. The chooser
 * approach is used here to keep user control and avoid requiring the SEND_SMS permission
 * for typical use cases.
 */
class SmsAction(private val context: Context) {

    /**
     * Open the system SMS app pre-populated with [number] and [message].
     * Must be called from a context with a valid window token (e.g., an Activity or
     * with FLAG_ACTIVITY_NEW_TASK set when called from a Service).
     */
    fun sendSms(number: String, message: String) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse("sms:${number.trim()}")
            putExtra("sms_body", message)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
