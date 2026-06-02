package com.techducat.thot.accessibility

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.util.DisplayMetrics
import android.util.Log
import android.view.*
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.techducat.thot.R
import com.techducat.thot.core.ThotCoreProvider
import com.techducat.thot.core.ThotTask
import com.techducat.thot.settings.ThotPreferences
import com.techducat.thot.ui.MainActivity
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.techducat.thot.BuildConfig

/**
 * Foreground service that draws two overlay windows via [WindowManager]:
 *
 *  1. **FAB button** — a small draggable circle that snaps to screen edges.
 *     Tap to open the action panel; long-press to dismiss.
 *
 *  2. **Action panel** — shows quick-action buttons (Explain, Summarize, Respond, Write)
 *     and a text field for custom tasks. Displays the AI response inline.
 *
 * Must be started as a foreground service (Android 8+) to remain alive while
 * other apps are in the foreground.
 */
class ThotOverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var prefs: ThotPreferences
    private lateinit var thotProvider: ThotCoreProvider

    // Overlay views
    private var fabView: View? = null
    private var panelView: View? = null
    private var isPanelVisible = false

    // FAB drag state
    private var fabInitialX = 0
    private var fabInitialY = 0
    private var touchInitialX = 0f
    private var touchInitialY = 0f

    // Panel WindowManager params — kept as field so we can update flags for keyboard
    private var panelParams: WindowManager.LayoutParams? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        prefs = ThotPreferences(this)
        thotProvider = ThotCoreProvider(this)
        startForegroundWithNotification()

        // FIX (crash 2): TYPE_APPLICATION_OVERLAY requires the SYSTEM_ALERT_WINDOW
        // permission to be granted at runtime.  Calling addView() without it throws
        // WindowManager.BadTokenException and crashes the service.  Stop gracefully
        // so MainActivity can prompt the user to grant the permission instead.
        if (!Settings.canDrawOverlays(this)) {
            Log.w(TAG, "Overlay permission not granted — stopping ThotOverlayService")
            stopSelf()
            return
        }

        addFab()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        removeFab()
        removePanel()
        thotProvider.cancel()
    }

    // ── Foreground notification ──────────────────────────────────────────────

    private fun startForegroundWithNotification() {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        // Create channel (no-op on API < 26, required on 26+)
        val channel = NotificationChannel(
            NOTIF_CHANNEL_ID,
            "Thot Overlay",
            NotificationManager.IMPORTANCE_MIN
        ).apply {
            description = "Keeps the Thot floating button active"
            setShowBadge(false)
        }
        nm.createNotificationChannel(channel)

        val tapIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification: Notification = NotificationCompat.Builder(this, NOTIF_CHANNEL_ID)
            .setContentTitle("Thot is active")
            .setContentText("Tap to open settings")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(tapIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()

        startForeground(NOTIF_ID, notification)
    }

    // ── FAB ─────────────────────────────────────────────────────────────────

    private fun addFab() {
        if (fabView != null) return

        val inflater = LayoutInflater.from(this)
        fabView = inflater.inflate(R.layout.overlay_button, null)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = prefs.fabX
            y = prefs.fabY
        }

        fabView!!.setOnTouchListener(FabTouchListener(params))
        windowManager.addView(fabView, params)
    }

    private fun removeFab() {
        fabView?.let {
            try { windowManager.removeView(it) } catch (_: Exception) {}
        }
        fabView = null
    }

    // ── Panel ────────────────────────────────────────────────────────────────

    private fun showPanel() {
        if (isPanelVisible) return
        isPanelVisible = true

        val inflater = LayoutInflater.from(this)
        val view = inflater.inflate(R.layout.overlay_panel, null)
        panelView = view

        val screenCtx = ThotAccessibilityService.latestContext
        view.findViewById<TextView>(R.id.tvContextLabel).text =
            if (screenCtx != null) "Context: ${screenCtx.appLabel}" else "No screen context captured"

        // Close button
        view.findViewById<TextView>(R.id.tvPanelClose).setOnClickListener { removePanel() }

        // Quick actions
        view.findViewById<MaterialButton>(R.id.btnExplain).setOnClickListener {
            runTask("Explain what I'm looking at on this screen.")
        }
        view.findViewById<MaterialButton>(R.id.btnSummarize).setOnClickListener {
            runTask("Give me a concise summary of the content on this screen.")
        }
        view.findViewById<MaterialButton>(R.id.btnRespond).setOnClickListener {
            runTask("Suggest a thoughtful response to the message or content on this screen.")
        }
        view.findViewById<MaterialButton>(R.id.btnWrite).setOnClickListener {
            runTask("Write a relevant message or content based on what's on this screen.")
        }

        // Custom task — tap the field to allow keyboard input by removing FLAG_NOT_FOCUSABLE
        val etCustom = view.findViewById<TextInputEditText>(R.id.etCustomTask)
        etCustom.setOnClickListener {
            allowKeyboard()
            etCustom.requestFocus()
            val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(etCustom, InputMethodManager.SHOW_IMPLICIT)
        }
        etCustom.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                val custom = etCustom.text?.toString()?.trim()
                if (!custom.isNullOrBlank()) {
                    // Dismiss keyboard and restore overlay focus behaviour
                    val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                    imm.hideSoftInputFromWindow(etCustom.windowToken, 0)
                    etCustom.clearFocus()
                    denyKeyboard()
                    runTask(custom)
                }
                true
            } else false
        }

        // Copy button
        view.findViewById<MaterialButton>(R.id.btnCopyResponse).setOnClickListener {
            val text = view.findViewById<TextView>(R.id.tvResponse).text?.toString()
                ?: return@setOnClickListener
            val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("Thot response", text))
            Toast.makeText(this, "Copied!", Toast.LENGTH_SHORT).show()
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            // NOT_FOCUSABLE initially — updated when user taps the text field
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_DIM_BEHIND,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
            dimAmount = 0.3f
        }
        panelParams = params

        windowManager.addView(view, params)
    }

    /**
     * Remove NOT_FOCUSABLE so the soft keyboard can be raised for the custom task field.
     */
    private fun allowKeyboard() {
        val pv = panelView ?: return
        val pp = panelParams ?: return
        pp.flags = pp.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
        try { windowManager.updateViewLayout(pv, pp) } catch (_: Exception) {}
    }

    /**
     * Restore NOT_FOCUSABLE after the keyboard is dismissed so the overlay doesn't
     * consume back-button / other system events.
     */
    private fun denyKeyboard() {
        val pv = panelView ?: return
        val pp = panelParams ?: return
        pp.flags = pp.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        try { windowManager.updateViewLayout(pv, pp) } catch (_: Exception) {}
    }

    private fun removePanel() {
        panelView?.let {
            try { windowManager.removeView(it) } catch (_: Exception) {}
        }
        panelView = null
        panelParams = null
        isPanelVisible = false
    }

    // ── Task runner ──────────────────────────────────────────────────────────

    private fun runTask(userPrompt: String) {
        val panel = panelView ?: return
        val screenCtx = ThotAccessibilityService.latestContext

        val task = ThotTask(
            prompt = userPrompt,
            screenContext = screenCtx?.toPromptString() ?: "",
            provider = prefs.provider
        )

        panel.findViewById<View>(R.id.layoutLoading).visibility = View.VISIBLE
        panel.findViewById<View>(R.id.scrollResponse).visibility = View.GONE
        panel.findViewById<MaterialButton>(R.id.btnCopyResponse).visibility = View.GONE

        vibrate()

        thotProvider.runTask(task) { result ->
            if (panelView == null) return@runTask  // panel was closed before result arrived
            panel.findViewById<View>(R.id.layoutLoading).visibility = View.GONE
            panel.findViewById<TextView>(R.id.tvResponse).text = result
            panel.findViewById<View>(R.id.scrollResponse).visibility = View.VISIBLE
            panel.findViewById<MaterialButton>(R.id.btnCopyResponse).visibility = View.VISIBLE
            // Report provider errors as non-fatals
            if (result.startsWith("Error:") && !BuildConfig.IS_FDROID_BUILD) {
                FirebaseCrashlytics.getInstance().apply {
                    setCustomKey("provider", prefs.provider.name)
                    setCustomKey("prompt_length", userPrompt.length)
                    recordException(RuntimeException("Overlay LLM call failed: $result"))
                }
            }
        }
    }

    private fun vibrate() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = getSystemService(VibratorManager::class.java)
                vm.defaultVibrator.vibrate(
                    VibrationEffect.createOneShot(30, VibrationEffect.DEFAULT_AMPLITUDE)
                )
            } else {
                @Suppress("DEPRECATION")
                val vibrator = getSystemService(VIBRATOR_SERVICE) as Vibrator
                @Suppress("DEPRECATION")
                vibrator.vibrate(30)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Vibrate failed: ${e.message}")
        }
    }

    // ── Screen width helper ──────────────────────────────────────────────────

    private fun getScreenWidth(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            windowManager.currentWindowMetrics.bounds.width()
        } else {
            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getMetrics(metrics)
            metrics.widthPixels
        }
    }

    // ── Drag + tap handler for the FAB ───────────────────────────────────────

    private inner class FabTouchListener(
        private val params: WindowManager.LayoutParams
    ) : View.OnTouchListener {

        private var hasMoved = false

        override fun onTouch(v: View, event: MotionEvent): Boolean {
            return when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    fabInitialX = params.x
                    fabInitialY = params.y
                    touchInitialX = event.rawX
                    touchInitialY = event.rawY
                    hasMoved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - touchInitialX).toInt()
                    val dy = (event.rawY - touchInitialY).toInt()
                    if (kotlin.math.abs(dx) > 8 || kotlin.math.abs(dy) > 8) hasMoved = true
                    params.x = fabInitialX + dx
                    params.y = fabInitialY + dy
                    try { windowManager.updateViewLayout(fabView, params) } catch (_: Exception) {}
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!hasMoved) {
                        if (isPanelVisible) removePanel() else showPanel()
                    } else {
                        snapToEdge(params)
                    }
                    true
                }
                else -> false
            }
        }

        private fun snapToEdge(params: WindowManager.LayoutParams) {
            val screenWidth = getScreenWidth()
            val density = resources.displayMetrics.density
            val snapLeft = (16 * density).toInt()
            val snapRight = screenWidth - (72 * density).toInt()
            params.x = if (params.x + (28 * density).toInt() < screenWidth / 2) snapLeft
                       else snapRight
            // Clamp Y to screen
            params.y = params.y.coerceAtLeast(0)
            try { windowManager.updateViewLayout(fabView, params) } catch (_: Exception) {}
            // Persist position
            prefs.fabX = params.x
            prefs.fabY = params.y
        }
    }

    companion object {
        private const val TAG = "ThotOverlay"
        private const val NOTIF_CHANNEL_ID = "thot_overlay_channel"
        private const val NOTIF_ID = 1001
    }
}
