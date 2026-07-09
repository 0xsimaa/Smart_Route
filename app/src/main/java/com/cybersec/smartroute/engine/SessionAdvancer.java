package com.cybersec.smartroute.engine;

import android.content.Context;

import com.cybersec.smartroute.storage.MockLocationSessionStore;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Single tick of the active session: load → advance → push → persist.
 * The foreground service calls {@link #advance}; the UI calls
 * {@link #pollUiState} to read the latest session without double-ticking.
 */
public final class SessionAdvancer {

    private static volatile long lastAdvanceMs = 0L;

    private SessionAdvancer() {
    }

    /** Reset debounce gate when a new session starts. */
    public static void resetDebounce() {
        lastAdvanceMs = 0L;
    }

    /**
     * Advance simulation and inject the next GPS fix. Called only from
     * {@link com.cybersec.smartroute.service.MockLocationForegroundService}.
     */
    public static AdvanceResult advance(Context context, double deltaSeconds) {
        MockLocationSessionStore store = new MockLocationSessionStore(context);
        JSONObject session = store.loadSession();
        if (session == null) return null;

        long intervalMs = session.optLong("updateIntervalMs", 2000L);
        long now = System.currentTimeMillis();
        if (now - lastAdvanceMs < (long) (intervalMs * 0.85)) {
            return statusFromSession(session);
        }
        lastAdvanceMs = now;

        NativeRouteSimulator sim = new NativeRouteSimulator(session);
        double prevLat = session.optDouble("lastLat", session.optDouble("startLat", 0));
        double prevLon = session.optDouble("lastLon", session.optDouble("startLon", 0));

        NativeRouteSimulator.TickResult tick = sim.tick(deltaSeconds);
        float bearing = (float) computeBearing(prevLat, prevLon, tick.lat, tick.lon);
        float speed = (float) speedMps(prevLat, prevLon, tick.lat, tick.lon, deltaSeconds);

        boolean ok = MockLocationEngine.setLocation(
                tick.lat, tick.lon, 3f, 0d, bearing, speed);

        JSONObject updated = sim.toJson();
        try {
            updated.put("lastLat", tick.lat);
            updated.put("lastLon", tick.lon);
            updated.put("lastBearing", bearing);
            updated.put("lastSpeedMps", speed);
        } catch (JSONException ignored) {
        }
        store.saveSession(updated);

        double progress = updated.optDouble("progress", 0);
        return new AdvanceResult(tick.lat, tick.lon, progress, tick.done, ok, bearing, speed);
    }

    /** Read current session state for UI refresh — does not advance or inject. */
    public static AdvanceResult pollUiState(Context context) {
        MockLocationSessionStore store = new MockLocationSessionStore(context);
        JSONObject session = store.loadSession();
        if (session == null) return null;
        return statusFromSession(session);
    }

    public static AdvanceResult statusFromSession(JSONObject session) {
        double lat = session.optDouble("lastLat", session.optDouble("startLat", 0));
        double lon = session.optDouble("lastLon", session.optDouble("startLon", 0));
        double progress = session.optDouble("progress", 0);
        float bearing = (float) session.optDouble("lastBearing", 0);
        float speed = (float) session.optDouble("lastSpeedMps", 0);
        return new AdvanceResult(lat, lon, progress, progress >= 1.0, true, bearing, speed);
    }

    public static double computeBearing(double lat1, double lon1, double lat2, double lon2) {
        double dLon = Math.toRadians(lon2 - lon1);
        double y = Math.sin(dLon) * Math.cos(Math.toRadians(lat2));
        double x = Math.cos(Math.toRadians(lat1)) * Math.sin(Math.toRadians(lat2))
                - Math.sin(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.cos(dLon);
        return (Math.toDegrees(Math.atan2(y, x)) + 360) % 360;
    }

    public static double speedMps(double lat1, double lon1,
                                  double lat2, double lon2, double dt) {
        if (dt <= 0) return 0;
        double r = 6371000.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double dist = 2 * r * Math.asin(Math.sqrt(a));
        return dist / dt;
    }

    public static final class AdvanceResult {
        public final double lat;
        public final double lon;
        public final double progress;
        public final boolean done;
        public final boolean ok;
        public final float bearing;
        public final float speedMps;

        public AdvanceResult(double lat, double lon, double progress,
                             boolean done, boolean ok, float bearing, float speedMps) {
            this.lat = lat;
            this.lon = lon;
            this.progress = progress;
            this.done = done;
            this.ok = ok;
            this.bearing = bearing;
            this.speedMps = speedMps;
        }
    }
}
