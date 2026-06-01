package com.techducat.thot.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivityForResult(intent, REQUEST_OVERLAY)
        } else {
            finish()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        finish()
    }

    companion object {
        private const val REQUEST_OVERLAY = 1001
    }
}
