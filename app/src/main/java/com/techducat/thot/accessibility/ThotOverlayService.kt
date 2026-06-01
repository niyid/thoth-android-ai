package com.techducat.thot.accessibility

import android.app.Service
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import android.view.*
import android.view.inputmethod.EditorInfo
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.techducat.thot.R
import com.techducat.thot.core.ProviderType
import com.techducat.thot.core.ThothCoreProvider
import com.techducat.thot.core.ThothTask
import com.techducat.thot.settings.ThotPreferences

/**
 * Foreground service that draws two overlay windows via [WindowManager]:
 *
 *  1. **FAB button** — a small draggable circle that snaps to screen edges.
 *     Tap to open the action panel; long-press to dismiss.
 *
 *  2. **Action panel** — shows quick-action buttons (Explain, Summarize, Respond, Write)
 *     and a text field for custom tasks. Displays the AI response inline.
 */
class ThotOverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var prefs: ThotPreferences
    private lateinit var thothProvider: ThothCoreProvider

    // Overlay views
    private var fabView: View? = null
    private var panelView: View? = null
    private var isPanelVisible = false

    // FAB drag state
    private var fabInitialX = 0
    private var fabInitialY = 0
    private var touchInitialX = 0f
    private var touchInitialY = 0f

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        prefs = ThotPreferences(this)
        thothProvider = ThothCoreProvider(this)
        addFab()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        removeFab()
        removePanel()
        thothProvider.cancel()
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
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 16
            y = 200
        }

        fabView!!.setOnTouchListener(FabTouchListener(params))
        windowManager.addView(fabView, params)
    }

    private fun removeFab() {
        fabView?.let { windowManager.removeView(it) }
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

        // Custom task via keyboard
        val etCustom = view.findViewById<TextInputEditText>(R.id.etCustomTask)
        etCustom.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                val custom = etCustom.text?.toString()?.trim()
                if (!custom.isNullOrBlank()) runTask(custom)
                true
            } else false
        }

        // Copy button
        view.findViewById<MaterialButton>(R.id.btnCopyResponse).setOnClickListener {
            val text = view.findViewById<TextView>(R.id.tvResponse).text?.toString() ?: return@setOnClickListener
            val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("Thot response", text))
            Toast.makeText(this, "Copied!", Toast.LENGTH_SHORT).show()
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
        }

        windowManager.addView(view, params)
    }

    private fun removePanel() {
        panelView?.let { windowManager.removeView(it) }
        panelView = null
        isPanelVisible = false
    }

    // ── Task runner ──────────────────────────────────────────────────────────

    private fun runTask(userPrompt: String) {
        val panel = panelView ?: return
        val screenCtx = ThotAccessibilityService.latestContext

        val task = ThothTask(
            prompt = userPrompt,
            screenContext = screenCtx?.toPromptString() ?: "",
            provider = prefs.provider
        )

        // Show loading
        panel.findViewById<View>(R.id.layoutLoading).visibility = View.VISIBLE
        panel.findViewById<View>(R.id.scrollResponse).visibility = View.GONE
        panel.findViewById<MaterialButton>(R.id.btnCopyResponse).visibility = View.GONE

        vibrate()

        thothProvider.runTask(task) { result ->
            panel.findViewById<View>(R.id.layoutLoading).visibility = View.GONE
            panel.findViewById<TextView>(R.id.tvResponse).text = result
            panel.findViewById<View>(R.id.scrollResponse).visibility = View.VISIBLE
            panel.findViewById<MaterialButton>(R.id.btnCopyResponse).visibility = View.VISIBLE
        }
    }

    private fun vibrate() {
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                val vm = getSystemService(VibratorManager::class.java)
                vm.defaultVibrator.vibrate(VibrationEffect.createOneShot(30, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                val vibrator = getSystemService(VIBRATOR_SERVICE) as Vibrator
                @Suppress("DEPRECATION")
                vibrator.vibrate(30)
            }
        } catch (e: Exception) {
            Log.w("ThotOverlay", "Vibrate failed: ${e.message}")
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
                    if (Math.abs(dx) > 8 || Math.abs(dy) > 8) hasMoved = true
                    params.x = fabInitialX + dx
                    params.y = fabInitialY + dy
                    windowManager.updateViewLayout(fabView, params)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!hasMoved) {
                        // Tap — toggle panel
                        if (isPanelVisible) removePanel() else showPanel()
                    }
                    // Snap to nearest edge
                    snapToEdge(params)
                    true
                }
                else -> false
            }
        }

        private fun snapToEdge(params: WindowManager.LayoutParams) {
            val display = windowManager.defaultDisplay
            val size = android.graphics.Point()
            @Suppress("DEPRECATION")
            display.getSize(size)
            val screenWidth = size.x
            val midX = screenWidth / 2
            params.x = if (params.x + 28 < midX) 16 else screenWidth - 72
            windowManager.updateViewLayout(fabView, params)
        }
    }
}
