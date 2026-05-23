package com.cybersec.smartroute.service;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.cybersec.smartroute.storage.MockLocationSessionStore;

import org.json.JSONObject;

/**
 * If the device reboots while a session was active, restart the foreground
 * service so the safe-zone fallback resumes injection until the user opens
 * the UI to confirm.
 */
public class BootRecoveryReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) return;
        MockLocationSessionStore store = new MockLocationSessionStore(context);
        if (!store.isSessionActive()) return;
        JSONObject session = store.loadSession();
        if (session == null) return;
        MockLocationForegroundService.start(context, session.toString());
    }
}
