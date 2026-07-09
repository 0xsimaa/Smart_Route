package com.cybersec.smartroute.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.cybersec.smartroute.R;
import com.cybersec.smartroute.engine.MockLocationEngine;
import com.cybersec.smartroute.engine.MockLocationPermissions;
import com.cybersec.smartroute.engine.SessionAdvancer;
import com.cybersec.smartroute.storage.MockLocationSessionStore;
import com.cybersec.smartroute.storage.SecureStorage;
import com.cybersec.smartroute.ui.MainActivity;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Keeps mock GPS injection alive when the activity is backgrounded.
 * Re-uses {@link SessionAdvancer} so foreground (activity timer) and
 * background (this service) share identical state/logic.
 */
public class MockLocationForegroundService extends Service {

    public static final String CHANNEL_ID = "smart_route_mock_gps";
    public static final int NOTIFICATION_ID = 7710;
    public static final String ACTION_START = "com.cybersec.smartroute.action.START_MOCK";
    public static final String ACTION_STOP = "com.cybersec.smartroute.action.STOP_MOCK";
    public static final String EXTRA_SESSION = "session_json";

    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable tick;
    private long intervalMs = 2000L;

    public static void start(Context context, String sessionJson) {
        Intent intent = new Intent(context, MockLocationForegroundService.class);
        intent.setAction(ACTION_START);
        intent.putExtra(EXTRA_SESSION, sessionJson);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent);
        } else {
            context.startService(intent);
        }
    }

    public static void stop(Context context) {
        Intent intent = new Intent(context, MockLocationForegroundService.class);
        intent.setAction(ACTION_STOP);
        context.startService(intent);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        MockLocationEngine.attach(this);
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(@Nullable Intent intent, int flags, int startId) {
        String action = intent == null ? null : intent.getAction();
        if (ACTION_STOP.equals(action)) {
            stopSession();
            return START_NOT_STICKY;
        }
        String sessionJson = intent == null ? null : intent.getStringExtra(EXTRA_SESSION);
        if (sessionJson != null) {
            try {
                new MockLocationSessionStore(this).saveSession(new JSONObject(sessionJson));
            } catch (JSONException ignored) {
            }
        }
        if (!MockLocationPermissions.isMockLocationEnabled(this)) {
            stopSelf();
            return START_NOT_STICKY;
        }
        if (!MockLocationEngine.initProvider()) {
            stopSelf();
            return START_NOT_STICKY;
        }

        JSONObject session = new MockLocationSessionStore(this).loadSession();
        intervalMs = session == null ? 2000L : session.optLong("updateIntervalMs", 2000L);

        Notification n = buildNotification(getString(R.string.notif_active), 0);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION);
        } else {
            startForeground(NOTIFICATION_ID, n);
        }

        SessionAdvancer.advance(getApplicationContext(), 0.0);
        startTicks();
        return START_STICKY;
    }

    private void updateNotification(SessionAdvancer.AdvanceResult r) {
        if (r == null) return;
        String text = String.format(java.util.Locale.US,
                getString(R.string.notif_progress),
                r.progress * 100, r.lat, r.lon);
        Notification n = buildNotification(text, (int) Math.round(r.progress * 100));
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm != null) nm.notify(NOTIFICATION_ID, n);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        if (tick != null) handler.removeCallbacks(tick);
        super.onDestroy();
    }

    private void startTicks() {
        if (tick != null) handler.removeCallbacks(tick);
        tick = new Runnable() {
            @Override
            public void run() {
                double delta = intervalMs / 1000.0;
                SessionAdvancer.AdvanceResult r =
                        SessionAdvancer.advance(getApplicationContext(), delta);
                if (r != null) updateNotification(r);
                if (r != null && r.done) {
                    stopSession();
                    return;
                }
                handler.postDelayed(this, intervalMs);
            }
        };
        handler.post(tick);
    }

    private void stopSession() {
        if (tick != null) handler.removeCallbacks(tick);
        tick = null;
        new MockLocationSessionStore(this).clearSession();
        new SecureStorage(this).setSessionActive(false);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE);
        } else {
            stopForeground(true);
        }
        stopSelf();
    }

    private Notification buildNotification(String text, int progressPct) {
        Intent launch = new Intent(this, MainActivity.class);
        launch.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent contentIntent = PendingIntent.getActivity(
                this, 0, launch,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Intent stop = new Intent(this, MockLocationForegroundService.class);
        stop.setAction(ACTION_STOP);
        PendingIntent stopIntent = PendingIntent.getService(
                this, 1, stop,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder b = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(getString(R.string.notif_title))
                .setContentText(text)
                .setSmallIcon(android.R.drawable.ic_menu_mylocation)
                .setContentIntent(contentIntent)
                .addAction(android.R.drawable.ic_menu_close_clear_cancel,
                        getString(R.string.notif_action_stop), stopIntent)
                .setOngoing(true)
                .setSilent(true)
                .setPriority(NotificationCompat.PRIORITY_LOW);

        if (progressPct > 0 && progressPct <= 100) {
            b.setProgress(100, progressPct, false);
        }
        return b.build();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.channel_mock_gps),
                    NotificationManager.IMPORTANCE_LOW);
            channel.setDescription(getString(R.string.channel_mock_gps_desc));
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(channel);
        }
    }
}
