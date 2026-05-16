package com.cybersec.smartroute

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Restarts mock session after reboot if one was active (safe-zone first).
 */
class BootRecoveryReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return
        val store = MockLocationSessionStore(context)
        val session = store.loadSession() ?: return
        if (!store.isSessionActive()) return
        MockLocationForegroundService.start(context, session.toString())
    }
}
