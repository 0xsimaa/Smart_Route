package com.cybersec.smartroute.service;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.MainThread;
import androidx.annotation.Nullable;

import com.cybersec.smartroute.engine.MockLocationEngine;
import com.cybersec.smartroute.engine.MockLocationPermissions;
import com.cybersec.smartroute.engine.SessionAdvancer;
import com.cybersec.smartroute.model.LatLng;
import com.cybersec.smartroute.model.PrivacyStatus;
import com.cybersec.smartroute.model.SpoofConfig;
import com.cybersec.smartroute.model.SpoofMode;
import com.cybersec.smartroute.storage.MockLocationSessionStore;
import com.cybersec.smartroute.storage.SecureStorage;
import com.cybersec.smartroute.util.LeakDetector;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Singleton orchestrator: state machine, persistence, foreground-service
 * lifecycle, leak detection, and observable state for the UI.
 */
public final class SpoofController {

    public interface Listener {
        void onStateChanged(SpoofController c);
    }

    private static volatile SpoofController instance;

    public static SpoofController get(Context context) {
        if (instance == null) {
            synchronized (SpoofController.class) {
                if (instance == null) instance = new SpoofController(context.getApplicationContext());
            }
        }
        return instance;
    }

    private final Context appContext;
    private final SecureStorage storage;
    private final MockLocationSessionStore sessionStore;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Set<Listener> listeners = Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<>());
    private final List<LatLng> trajectory = new ArrayList<>();

    private SpoofConfig config = SpoofConfig.defaults();
    private boolean active;
    private boolean paused;
    private boolean mockAppReady;
    private PrivacyStatus status = PrivacyStatus.IDLE;
    private String statusMessage;
    private LatLng lastPushed;
    private double progress;
    private long sessionStartMs;
    private Long autoResetAtMs;
    private Runnable tick;
    private double currentSpeedMps;
    private double currentBearingDeg;
    private double distanceTraveledKm;

    private SpoofController(Context appContext) {
        this.appContext = appContext;
        this.storage = new SecureStorage(appContext);
        this.sessionStore = new MockLocationSessionStore(appContext);
        MockLocationEngine.attach(appContext);
        loadPersisted();
    }

    // ---------- listeners ----------

    public void addListener(Listener l) {
        listeners.add(l);
    }

    public void removeListener(Listener l) {
        listeners.remove(l);
    }

    private void notifyListeners() {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            for (Listener l : new LinkedHashSet<>(listeners)) l.onStateChanged(this);
        } else {
            mainHandler.post(this::notifyListeners);
        }
    }

    // ---------- state ----------

    public SpoofConfig getConfig() { return config; }
    public boolean isActive() { return active; }
    public boolean isPaused() { return paused; }
    public boolean isMockAppReady() { return mockAppReady; }
    public PrivacyStatus getStatus() { return status; }
    public String getStatusMessage() { return statusMessage; }
    @Nullable public LatLng getCurrentPosition() { return lastPushed; }
    public double getProgress() { return progress; }
    public List<LatLng> getTrajectory() {
        synchronized (trajectory) {
            return Collections.unmodifiableList(new ArrayList<>(trajectory));
        }
    }
    public long getSessionStartMs() { return sessionStartMs; }
    public double getCurrentSpeedMps() { return currentSpeedMps; }
    public double getCurrentBearing() { return currentBearingDeg; }
    public double getDistanceTraveledKm() { return distanceTraveledKm; }

    public Long getRemainingMs() {
        if (!active || sessionStartMs == 0) return null;
        long total = config.durationMinutes * 60_000L;
        long left = total - (System.currentTimeMillis() - sessionStartMs);
        return Math.max(0, left);
    }

    // ---------- config ----------

    public void updateConfig(SpoofConfig newConfig) {
        this.config = newConfig;
        storage.saveConfig(newConfig);
        notifyListeners();
    }

    public List<String> readAuditLog() {
        return storage.readAudit();
    }

    public boolean checkMockReady() {
        mockAppReady = MockLocationPermissions.isMockLocationEnabled(appContext);
        notifyListeners();
        return mockAppReady;
    }

    public void openDeveloperSettings() {
        MockLocationPermissions.openDeveloperSettings(appContext);
    }

    public void openAppSettings() {
        MockLocationPermissions.openAppSettings(appContext);
    }

    /** Force UI sync from the active session store (e.g. after returning from background). */
    public void refreshUiFromSession() {
        if (!active || config.mode == SpoofMode.STATIC_LOCATION) {
            notifyListeners();
            return;
        }
        syncUiFromSession();
    }

    private void loadPersisted() {
        SpoofConfig saved = storage.loadConfig();
        if (saved != null) config = saved;
        mockAppReady = MockLocationPermissions.isMockLocationEnabled(appContext);
        recoverFromCrashIfNeeded();
        notifyListeners();
    }

    private void recoverFromCrashIfNeeded() {
        if (!storage.wasSessionActive()) return;
        if (!sessionStore.isSessionActive()) {
            storage.setSessionActive(false);
            return;
        }
        JSONObject session = sessionStore.loadSession();
        if (session == null) {
            storage.setSessionActive(false);
            return;
        }
        if (!MockLocationEngine.initProvider()) {
            status = PrivacyStatus.ERROR;
            statusMessage = "Recovery failed — set Smart Route as the mock location app";
            storage.setSessionActive(false);
            sessionStore.clearSession();
            return;
        }
        active = true;
        paused = false;
        sessionStartMs = System.currentTimeMillis();
        status = PrivacyStatus.FALLBACK;
        statusMessage = "Session recovered — resuming mock GPS";
        MockLocationForegroundService.start(appContext, session.toString());
        startTimer();
        storage.appendAudit("Crash/boot recovery");
    }

    // ---------- lifecycle ----------

    @MainThread
    public boolean start() {
        if (active) return true;
        mockAppReady = MockLocationPermissions.isMockLocationEnabled(appContext);
        if (!mockAppReady) {
            status = PrivacyStatus.ERROR;
            statusMessage = "Select Smart Route as mock location app in Developer options.";
            notifyListeners();
            return false;
        }
        if (!MockLocationEngine.initProvider()) {
            status = PrivacyStatus.ERROR;
            statusMessage = "Could not register GPS test provider.";
            notifyListeners();
            return false;
        }

        synchronized (trajectory) { trajectory.clear(); }
        progress = 0;
        lastPushed = null;
        currentSpeedMps = 0;
        currentBearingDeg = 0;
        distanceTraveledKm = 0;
        sessionStartMs = System.currentTimeMillis();
        autoResetAtMs = config.autoResetMinutes == null
                ? null
                : sessionStartMs + config.autoResetMinutes * 60_000L;
        active = true;
        paused = false;
        status = PrivacyStatus.ACTIVE;
        statusMessage = "Injecting mock GPS via Android test provider";
        storage.setSessionActive(true);
        storage.appendAudit("Session started (" + config.mode.wireName() + ")");

        SessionAdvancer.resetDebounce();
        JSONObject engineSession = config.toEngineJson(0);
        sessionStore.saveSession(engineSession);
        MockLocationForegroundService.start(appContext, engineSession.toString());

        startTimer();
        syncUiFromSession();
        notifyListeners();
        return true;
    }

    @MainThread
    public void pause() {
        if (!active) return;
        paused = true;
        statusMessage = "Paused — coordinates hold at last injection";
        storage.appendAudit("Paused");
        notifyListeners();
    }

    @MainThread
    public void resume() {
        if (!active) return;
        paused = false;
        statusMessage = "Mock GPS resumed";
        storage.appendAudit("Resumed");
        notifyListeners();
    }

    @MainThread
    public void stop(boolean userInitiated) {
        if (tick != null) {
            mainHandler.removeCallbacks(tick);
            tick = null;
        }
        active = false;
        paused = false;
        progress = 0;
        currentSpeedMps = 0;
        currentBearingDeg = 0;
        status = PrivacyStatus.IDLE;
        statusMessage = userInitiated ? "Mock GPS stopped" : "Session ended";
        MockLocationForegroundService.stop(appContext);
        sessionStore.clearSession();
        MockLocationEngine.removeProvider();
        storage.setSessionActive(false);
        storage.appendAudit("Stopped (user=" + userInitiated + ")");
        notifyListeners();
    }

    // ---------- internal ticking ----------

    private void startTimer() {
        if (tick != null) mainHandler.removeCallbacks(tick);
        long intervalMs = config.updateIntervalSeconds * 1000L;
        tick = new Runnable() {
            @Override
            public void run() {
                if (!active) return;
                if (!paused) advance(intervalMs / 1000.0);
                if (active) mainHandler.postDelayed(this, intervalMs);
            }
        };
        mainHandler.postDelayed(tick, intervalMs);
    }

    private void advance(double deltaSeconds) {
        if (autoResetAtMs != null && System.currentTimeMillis() > autoResetAtMs) {
            stop(false);
            statusMessage = "Auto-reset after configured window";
            notifyListeners();
            return;
        }
        if (config.mode == SpoofMode.STATIC_LOCATION) {
            applyLocation(config.start, false, deltaSeconds, true);
            checkLeak();
            return;
        }

        SessionAdvancer.AdvanceResult r = SessionAdvancer.pollUiState(appContext);
        if (r == null) {
            if (active) stop(false);
            return;
        }
        applyAdvanceResult(r, deltaSeconds);
        checkLeak();
    }

    private void syncUiFromSession() {
        SessionAdvancer.AdvanceResult r = SessionAdvancer.pollUiState(appContext);
        if (r == null) {
            mainHandler.postDelayed(this::syncUiFromSession, 150);
            return;
        }
        applyAdvanceResult(r, 0.0);
    }

    private void applyAdvanceResult(SessionAdvancer.AdvanceResult r, double deltaSeconds) {
        if (r == null) return;
        LatLng pos = new LatLng(r.lat, r.lon);
        boolean moved = lastPushed == null || LatLng.haversineKm(lastPushed, pos) > 0.00001;
        if (moved && lastPushed != null) {
            distanceTraveledKm += LatLng.haversineKm(lastPushed, pos);
        }
        currentSpeedMps = r.speedMps;
        currentBearingDeg = r.bearing;
        progress = r.progress;
        lastPushed = pos;
        if (moved) {
            synchronized (trajectory) {
                trajectory.add(pos);
                if (trajectory.size() > 5000) trajectory.remove(0);
            }
        }
        if (!r.ok) {
            status = PrivacyStatus.FALLBACK;
            applyLocation(config.safeZone, true, deltaSeconds, false);
        } else if (status == PrivacyStatus.FALLBACK) {
            status = PrivacyStatus.ACTIVE;
        }
        if (System.currentTimeMillis() - sessionStartMs >= config.durationMinutes * 60_000L) {
            stop(false);
            statusMessage = "Duration limit reached";
            notifyListeners();
            return;
        }
        if (r.done) {
            stop(false);
            statusMessage = "Route complete";
            notifyListeners();
            return;
        }
        notifyListeners();
    }

    private void applyLocation(LatLng pos, boolean fallback, double dt, boolean recordHistory) {
        float bearing = 0f;
        float speed = 0f;
        if (lastPushed != null) {
            bearing = (float) com.cybersec.smartroute.util.MotionMetrics.bearingDegrees(lastPushed, pos);
            speed = (float) com.cybersec.smartroute.util.MotionMetrics.speedMps(lastPushed, pos, dt);
            distanceTraveledKm += LatLng.haversineKm(lastPushed, pos);
        }
        currentBearingDeg = bearing;
        currentSpeedMps = speed;
        boolean ok = MockLocationEngine.setLocation(
                pos.latitude, pos.longitude, 3f, 0d, bearing, speed);
        if (!ok && !fallback) {
            status = PrivacyStatus.FALLBACK;
            ok = MockLocationEngine.setLocation(
                    config.safeZone.latitude, config.safeZone.longitude);
            statusMessage = ok ? "Injection failed — safe zone applied" : "Mock injection failed";
            storage.appendAudit("Fallback to safe zone");
            pos = config.safeZone;
        }
        if (ok) {
            lastPushed = pos;
            if (recordHistory) {
                synchronized (trajectory) {
                    trajectory.add(pos);
                    if (trajectory.size() > 5000) trajectory.remove(0);
                }
            }
            try {
                JSONObject session = sessionStore.loadSession();
                if (session != null) {
                    session.put("lastLat", pos.latitude);
                    session.put("lastLon", pos.longitude);
                    sessionStore.saveSession(session);
                }
            } catch (Exception ignored) {
            }
        } else {
            status = PrivacyStatus.ERROR;
        }
        notifyListeners();
    }

    private void checkLeak() {
        final LatLng injected = lastPushed;
        if (injected == null) return;
        LeakDetector.check(appContext, injected, 150.0, leak -> {
            if (leak) {
                status = PrivacyStatus.LEAK_WARNING;
                statusMessage = "GPS divergence — safe zone applied";
                storage.appendAudit("Leak detected — fallback");
                applyLocation(config.safeZone, true, 1.0, false);
            } else if (status == PrivacyStatus.LEAK_WARNING) {
                status = PrivacyStatus.ACTIVE;
                notifyListeners();
            }
        });
    }
}
