package com.cybersec.smartroute.engine;

import com.cybersec.smartroute.model.LatLng;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Pure-Java route interpolator. Operates on a JSON session blob so it can
 * be driven by either {@link com.cybersec.smartroute.service.SpoofController}
 * (foreground) or the foreground service (background) using identical state.
 */
public final class NativeRouteSimulator {

    private final JSONObject session;
    private final String mode;
    private final List<LatLng> waypoints;
    private final LatLng start;
    private final LatLng end;
    private final boolean curved;
    private final double minSpeedKmh;
    private final double maxSpeedKmh;
    private final boolean enablePauses;
    private final double pauseProbability;
    private final String acceleration;

    private double progress;
    private double currentSpeedKmh;
    private int pauseTicksLeft;
    private boolean inPause;
    private final Random rng = new Random();

    public NativeRouteSimulator(JSONObject session) {
        this.session = session;
        this.mode = session.optString("mode", "dynamicPath");
        this.start = new LatLng(session.optDouble("startLat", 0), session.optDouble("startLon", 0));
        this.end = new LatLng(session.optDouble("endLat", 0), session.optDouble("endLon", 0));
        this.curved = session.optBoolean("curved", false);
        this.minSpeedKmh = session.optDouble("minSpeedKmh", 40);
        this.maxSpeedKmh = session.optDouble("maxSpeedKmh", 80);
        this.enablePauses = session.optBoolean("enablePauses", false);
        this.pauseProbability = session.optDouble("pauseProbability", 0.02);
        this.acceleration = session.optString("acceleration", "smooth");
        this.progress = session.optDouble("progress", 0);
        this.currentSpeedKmh = session.optDouble("currentSpeedKmh", minSpeedKmh);
        this.waypoints = parseWaypoints(session);
    }

    private static List<LatLng> parseWaypoints(JSONObject session) {
        List<LatLng> list = new ArrayList<>();
        JSONArray arr = session.optJSONArray("waypoints");
        if (arr != null) {
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o != null) list.add(LatLng.fromJson(o));
            }
        }
        return list;
    }

    public boolean isComplete() {
        return progress >= 1.0;
    }

    /** Returns [lat, lon, done]. */
    public TickResult tick(double deltaSeconds) {
        if ("staticLocation".equals(mode)) {
            return new TickResult(start.latitude, start.longitude, false);
        }
        if (isComplete()) {
            return new TickResult(end.latitude, end.longitude, true);
        }
        if (pauseTicksLeft > 0) {
            pauseTicksLeft--;
            LatLng cur = positionAt(progress);
            return new TickResult(cur.latitude, cur.longitude, false);
        }
        if (enablePauses && !inPause && rng.nextDouble() < pauseProbability) {
            inPause = true;
            pauseTicksLeft = 2 + rng.nextInt(4);
            LatLng cur = positionAt(progress);
            return new TickResult(cur.latitude, cur.longitude, false);
        }
        inPause = false;

        double totalKm = totalRouteKm();
        if (totalKm <= 0) {
            progress = 1.0;
            return new TickResult(end.latitude, end.longitude, true);
        }

        double targetSpeed = targetSpeedKmh();
        currentSpeedKmh = applyAcceleration(currentSpeedKmh, targetSpeed, deltaSeconds);

        double distanceKm = (currentSpeedKmh / 3600.0) * deltaSeconds;
        progress = Math.max(0, Math.min(1, progress + distanceKm / totalKm));

        LatLng pos = positionAt(progress);
        return new TickResult(pos.latitude, pos.longitude, progress >= 1.0);
    }

    public JSONObject toJson() {
        JSONObject copy = new JSONObject();
        try {
            // Shallow clone via re-serialisation is sufficient since the
            // input object only carries primitives + an array.
            copy = new JSONObject(session.toString());
            copy.put("progress", progress);
            copy.put("currentSpeedKmh", currentSpeedKmh);
        } catch (JSONException ignored) {
        }
        return copy;
    }

    // ---------- helpers ----------

    private double totalRouteKm() {
        List<LatLng> pts = new ArrayList<>(waypoints.size() + 2);
        pts.add(start);
        pts.addAll(waypoints);
        pts.add(end);
        double total = 0;
        for (int i = 0; i < pts.size() - 1; i++) {
            total += LatLng.haversineKm(pts.get(i), pts.get(i + 1));
        }
        return total;
    }

    private LatLng positionAt(double globalT) {
        List<LatLng> pts = new ArrayList<>(waypoints.size() + 2);
        pts.add(start);
        pts.addAll(waypoints);
        pts.add(end);
        if (pts.size() < 2) return start;
        double[] lengths = new double[pts.size() - 1];
        double total = 0;
        for (int i = 0; i < lengths.length; i++) {
            lengths[i] = LatLng.haversineKm(pts.get(i), pts.get(i + 1));
            total += lengths[i];
        }
        if (total == 0) return start;
        double remaining = Math.max(0, Math.min(1, globalT)) * total;
        for (int i = 0; i < lengths.length; i++) {
            double len = lengths[i];
            if (remaining <= len || i == lengths.length - 1) {
                double localT = len == 0 ? 1.0 : Math.max(0, Math.min(1, remaining / len));
                LatLng a = pts.get(i);
                LatLng b = pts.get(i + 1);
                return curved ? LatLng.curved(a, b, localT, 0.15) : LatLng.linear(a, b, localT);
            }
            remaining -= len;
        }
        return end;
    }

    private double targetSpeedKmh() {
        double mid = 0.5;
        double edge = Math.abs(progress - mid) * 2;
        double lerp = minSpeedKmh + (maxSpeedKmh - minSpeedKmh) * (1 - edge * 0.3);
        return Math.max(minSpeedKmh, Math.min(maxSpeedKmh, lerp));
    }

    private double applyAcceleration(double current, double target, double dt) {
        switch (acceleration) {
            case "constant":
                return target;
            case "sudden":
                double sign = target == current ? 0 : (target > current ? 1 : -1);
                return Math.max(minSpeedKmh, Math.min(maxSpeedKmh, current + sign * 25 * dt));
            case "smooth":
            default:
                double rate = 8.0;
                return current + (target - current) * (1 - Math.exp(-rate * dt));
        }
    }

    public static final class TickResult {
        public final double lat;
        public final double lon;
        public final boolean done;

        public TickResult(double lat, double lon, boolean done) {
            this.lat = lat;
            this.lon = lon;
            this.done = done;
        }
    }
}
