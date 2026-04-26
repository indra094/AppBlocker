package com.indrajeet.appblocker.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Toast.makeText(
                context,
                "AppBlocker restarted. Accessibility enforcement resumes when the service is enabled.",
                Toast.LENGTH_LONG
            ).show()
        }
    }
}

