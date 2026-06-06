package com.techducat.thot.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.os.Build
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * The heart of Thot's OS-level integration.
 *
 * This [AccessibilityService] runs in the background and:
 *  1. Listens for window / content changes across all apps
 *  2. Extracts visible text from the current screen
 *  3. Exposes the latest screen context via [latestContext] (singleton companion)
 *  4. Starts/stops the [ThotOverlayService] floating button
 */
class ThotAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        serviceInfo = serviceInfo.apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                    AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                    AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
            notificationTimeout = 100
        }

        // Launch the overlay floating button as a foreground service.
        // On API 26+ startService() for a foreground service causes an
        // IllegalStateException ("not allowed to start service Intent").
        // startForegroundService() is required.
        startOverlayService()

        instance = this
        Log.d(TAG, "ThotAccessibilityService connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return

        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
        ) {
            val packageName = event.packageName?.toString() ?: return

            // Don't capture our own overlay or the system UI unnecessarily
            if (packageName == "com.techducat.thot" ||
                packageName == "com.techducat.thot.debug" ||
                packageName == "com.techducat.thot.fdroid" ||
                packageName == "com.techducat.thot.fdroid.debug"
            ) return

            val root = rootInActiveWindow ?: return
            val text = extractText(root)
            if (text.isNotBlank()) {
                latestContext = ScreenContext(
                    packageName = packageName,
                    appLabel = resolveAppLabel(packageName),
                    text = text
                )
            }
        }
    }

    override fun onInterrupt() {
        Log.d(TAG, "ThotAccessibilityService interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        stopOverlayService()
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    /**
     * Recursively walk the [AccessibilityNodeInfo] tree and collect all visible text.
     * Limits output to [MAX_CHARS] to keep prompt size manageable.
     */
    private fun extractText(node: AccessibilityNodeInfo, depth: Int = 0): String {
        if (depth > MAX_DEPTH) return ""
        val sb = StringBuilder()

        val text = node.text?.toString()
        val contentDesc = node.contentDescription?.toString()

        if (!text.isNullOrBlank()) sb.append(text).append("\n")
        else if (!contentDesc.isNullOrBlank()) sb.append(contentDesc).append("\n")

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            sb.append(extractText(child, depth + 1))
            if (sb.length > MAX_CHARS) break
        }

        return sb.toString().take(MAX_CHARS)
    }

    private fun resolveAppLabel(packageName: String): String {
        return try {
            val pm = packageManager
            val info = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(info).toString()
        } catch (e: Exception) {
            packageName
        }
    }

    private fun startOverlayService() {
        val intent = Intent(this, ThotOverlayService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun stopOverlayService() {
        val intent = Intent(this, ThotOverlayService::class.java)
        stopService(intent)
    }

    // ── Singleton accessor ───────────────────────────────────────────────────

    companion object {
        private const val TAG = "ThotAccessibility"
        private const val MAX_DEPTH = 12
        private const val MAX_CHARS = 3000

        /** The most recently captured screen content. Thread-safe read for overlay. */
        @Volatile
        var latestContext: ScreenContext? = null
            private set

        /** Live reference to the running service, or null if not enabled. */
        @Volatile
        var instance: ThotAccessibilityService? = null
            private set

        val isRunning: Boolean get() = instance != null
    }
}

/**
 * Snapshot of what was visible on screen at a given moment.
 */
data class ScreenContext(
    val packageName: String,
    val appLabel: String,
    val text: String,
    val timestamp: Long = System.currentTimeMillis()
) {
    /** Formatted representation suitable for inclusion in a prompt. */
    fun toPromptString(): String =
        "App: $appLabel ($packageName)\n\nVisible text:\n$text"
}
