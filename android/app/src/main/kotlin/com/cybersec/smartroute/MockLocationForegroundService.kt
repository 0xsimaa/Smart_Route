package com.cybersec.smartroute

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import org.json.JSONObject

/**
 * Advances mock GPS session while the app is backgrounded.
 * When the Flutter UI is in foreground, it drives advancement via [SessionAdvancer] too —
 * both read/write the same persisted session (idempotent ticks are avoided by single timer:
 * service runs only while foreground; UI defers to service when backgrounded).
 */
class MockLocationForegroundService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private var tickRunnable: Runnable? = null
    private var intervalMs = 2000L

    override fun onCreate() {
        super.onCreate()
        MockLocationEngine.attach(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSession()
                return START_NOT_STICKY
            }
            ACTION_START, null -> {
                val sessionJson = intent?.getStringExtra(EXTRA_SESSION)
                if (sessionJson != null) {
                    MockLocationSessionStore(this).saveSession(JSONObject(sessionJson))
                }
                if (!MockLocationPermissions.isMockLocationEnabled(this)) {
                    stopSelf()
                    return START_NOT_STICKY
                }
                if (!MockLocationEngine.initProvider()) {
                    stopSelf()
                    return START_NOT_STICKY
                }
                val session = MockLocationSessionStore(this).loadSession()
                intervalMs = session?.optInt("updateIntervalMs", 2000)?.toLong() ?: 2000L
                startForeground(NOTIFICATION_ID, buildNotification("Mock GPS active"))
                SessionAdvancer.advance(applicationContext, 0.0)
                startTicks()
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        tickRunnable?.let { handler.removeCallbacks(it) }
        super.onDestroy()
    }

    private fun startTicks() {
        tickRunnable?.let { handler.removeCallbacks(it) }
        tickRunnable = object : Runnable {
            override fun run() {
                val delta = intervalMs / 1000.0
                SessionAdvancer.advance(applicationContext, delta)
                handler.postDelayed(this, intervalMs)
            }
        }
        handler.post(tickRunnable!!)
    }

    private fun stopSession() {
        tickRunnable?.let { handler.removeCallbacks(it) }
        tickRunnable = null
        MockLocationSessionStore(this).clearSession()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun buildNotification(text: String): Notification {
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
        val pending = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Smart Route — Mock GPS")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentIntent(pending)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Mock location service",
                NotificationManager.IMPORTANCE_LOW,
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    companion object {
        const val CHANNEL_ID = "smart_route_mock_gps"
        const val NOTIFICATION_ID = 7710
        const val ACTION_START = "com.cybersec.smartroute.action.START_MOCK"
        const val ACTION_STOP = "com.cybersec.smartroute.action.STOP_MOCK"
        const val EXTRA_SESSION = "session_json"

        fun start(context: Context, sessionJson: String) {
            val intent = Intent(context, MockLocationForegroundService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_SESSION, sessionJson)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, MockLocationForegroundService::class.java).apply {
                    action = ACTION_STOP
                },
            )
        }
    }
}
