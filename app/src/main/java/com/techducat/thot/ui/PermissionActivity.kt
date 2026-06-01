package com.techducat.thot.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

/**
 * Transparent trampoline activity used to request [Settings.ACTION_MANAGE_OVERLAY_PERMISSION]
 * from contexts that don't have an Activity (e.g., from a Service or BroadcastReceiver).
 *
 * Start with:
 *   val intent = Intent(context, PermissionActivity::class.java)
 *   intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
 *   context.startActivity(intent)
 */
class PermissionActivity : AppCompatActivity() {

    private val overlayLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        // Result is irrelevant — check state directly on return
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            overlayLauncher.launch(intent)
        } else {
            finish()
        }
    }
}
