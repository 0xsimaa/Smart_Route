package com.cybersec.smartroute.model;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Persisted route + behaviour configuration. Immutable; mutate via builder.
 */
public final class SpoofConfig {

    /**
     * Neutral default route: a small ~150m segment at the prime meridian so
     * the simulator has valid math on first launch but the user is forced
     * to pick a real route on the map screen before doing anything useful.
     */
    public static final LatLng DEFAULT_START = new LatLng(0.0, 0.0);
    public static final LatLng DEFAULT_END = new LatLng(0.001, 0.001);

    public final SpoofMode mode;
    public final LatLng start;
    public final LatLng end;
    public final List<LatLng> waypoints;
    public final double minSpeedKmh;
    public final double maxSpeedKmh;
    public final int updateIntervalSeconds;
    public final PathShape pathShape;
    public final AccelerationPattern acceleration;
    public final int durationMinutes;
    public final LatLng safeZone;
    public final Integer autoResetMinutes;
    public final boolean enablePauses;
    public final double pauseProbability;
    /** Road-following polyline from Directions API; empty when not fetched. */
    public final List<LatLng> routePolyline;
    /** Turn-by-turn steps from Directions API. */
    public final List<String> directionSteps;

    private SpoofConfig(Builder b) {
        this.mode = b.mode;
        this.start = b.start;
        this.end = b.end;
        this.waypoints = Collections.unmodifiableList(new ArrayList<>(b.waypoints));
        this.minSpeedKmh = b.minSpeedKmh;
        this.maxSpeedKmh = b.maxSpeedKmh;
        this.updateIntervalSeconds = b.updateIntervalSeconds;
        this.pathShape = b.pathShape;
        this.acceleration = b.acceleration;
        this.durationMinutes = b.durationMinutes;
        this.safeZone = b.safeZone;
        this.autoResetMinutes = b.autoResetMinutes;
        this.enablePauses = b.enablePauses;
        this.pauseProbability = b.pauseProbability;
        this.routePolyline = Collections.unmodifiableList(new ArrayList<>(b.routePolyline));
        this.directionSteps = Collections.unmodifiableList(new ArrayList<>(b.directionSteps));
    }

    public static SpoofConfig defaults() {
        return new Builder().build();
    }

    /**
     * True when start/end are still at the placeholder defaults — the user
     * must pick a real route on the map first.
     */
    public boolean isUsingPlaceholderRoute() {
        return start.equals(DEFAULT_START) && end.equals(DEFAULT_END);
    }

    /** Total route length in metres (road polyline when available). */
    public double routeLengthMeters() {
        if (!routePolyline.isEmpty()) {
            double km = 0;
            for (int i = 0; i < routePolyline.size() - 1; i++) {
                km += LatLng.haversineKm(routePolyline.get(i), routePolyline.get(i + 1));
            }
            return km * 1000.0;
        }
        java.util.List<LatLng> pts = new java.util.ArrayList<>(waypoints.size() + 2);
        pts.add(start);
        pts.addAll(waypoints);
        pts.add(end);
        double km = 0;
        for (int i = 0; i < pts.size() - 1; i++) {
            km += LatLng.haversineKm(pts.get(i), pts.get(i + 1));
        }
        return km * 1000.0;
    }

    public Builder toBuilder() {
        return new Builder()
                .mode(mode)
                .start(start)
                .end(end)
                .waypoints(waypoints)
                .speedRange(minSpeedKmh, maxSpeedKmh)
                .updateIntervalSeconds(updateIntervalSeconds)
                .pathShape(pathShape)
                .acceleration(acceleration)
                .durationMinutes(durationMinutes)
                .safeZone(safeZone)
                .autoResetMinutes(autoResetMinutes)
                .enablePauses(enablePauses)
                .pauseProbability(pauseProbability)
                .routePolyline(routePolyline)
                .directionSteps(directionSteps);
    }

    public JSONObject toJson() {
        JSONObject o = new JSONObject();
        try {
            o.put("mode", mode.wireName());
            o.put("start", start.toJson());
            o.put("end", end.toJson());
            JSONArray arr = new JSONArray();
            for (LatLng w : waypoints) arr.put(w.toJson());
            o.put("waypoints", arr);
            o.put("minSpeedKmh", minSpeedKmh);
            o.put("maxSpeedKmh", maxSpeedKmh);
            o.put("updateIntervalSeconds", updateIntervalSeconds);
            o.put("pathShape", pathShape.name());
            o.put("acceleration", acceleration.wireName());
            o.put("durationMinutes", durationMinutes);
            o.put("safeZone", safeZone.toJson());
            if (autoResetMinutes != null) o.put("autoResetMinutes", autoResetMinutes);
            o.put("enablePauses", enablePauses);
            o.put("pauseProbability", pauseProbability);
            JSONArray poly = new JSONArray();
            for (LatLng p : routePolyline) poly.put(p.toJson());
            o.put("routePolyline", poly);
            JSONArray steps = new JSONArray();
            for (String s : directionSteps) steps.put(s);
            o.put("directionSteps", steps);
        } catch (JSONException ignored) {
        }
        return o;
    }

    public static SpoofConfig fromJson(JSONObject o) {
        Builder b = new Builder();
        b.mode = SpoofMode.fromWire(o.optString("mode", "dynamicPath"));
        b.start = LatLng.fromJson(o.optJSONObject("start") == null
                ? new JSONObject() : o.optJSONObject("start"));
        if (b.start.latitude == 0 && b.start.longitude == 0) b.start = DEFAULT_START;
        b.end = LatLng.fromJson(o.optJSONObject("end") == null
                ? new JSONObject() : o.optJSONObject("end"));
        if (b.end.latitude == 0 && b.end.longitude == 0) b.end = DEFAULT_END;
        JSONArray arr = o.optJSONArray("waypoints");
        b.waypoints = new ArrayList<>();
        if (arr != null) {
            for (int i = 0; i < arr.length(); i++) {
                JSONObject w = arr.optJSONObject(i);
                if (w != null) b.waypoints.add(LatLng.fromJson(w));
            }
        }
        b.minSpeedKmh = o.optDouble("minSpeedKmh", 40);
        b.maxSpeedKmh = o.optDouble("maxSpeedKmh", 80);
        b.updateIntervalSeconds = o.optInt("updateIntervalSeconds", 2);
        try {
            b.pathShape = PathShape.valueOf(o.optString("pathShape", "STRAIGHT"));
        } catch (IllegalArgumentException ex) {
            b.pathShape = PathShape.STRAIGHT;
        }
        b.acceleration = AccelerationPattern.fromWire(o.optString("acceleration", "smooth"));
        b.durationMinutes = o.optInt("durationMinutes", 30);
        b.safeZone = LatLng.fromJson(o.optJSONObject("safeZone") == null
                ? new JSONObject() : o.optJSONObject("safeZone"));
        if (b.safeZone.latitude == 0 && b.safeZone.longitude == 0) b.safeZone = b.start;
        if (o.has("autoResetMinutes") && !o.isNull("autoResetMinutes")) {
            b.autoResetMinutes = o.optInt("autoResetMinutes");
        }
        b.enablePauses = o.optBoolean("enablePauses", false);
        b.pauseProbability = o.optDouble("pauseProbability", 0.02);
        b.routePolyline = new ArrayList<>();
        JSONArray poly = o.optJSONArray("routePolyline");
        if (poly != null) {
            for (int i = 0; i < poly.length(); i++) {
                JSONObject pt = poly.optJSONObject(i);
                if (pt != null) b.routePolyline.add(LatLng.fromJson(pt));
            }
        }
        b.directionSteps = new ArrayList<>();
        JSONArray steps = o.optJSONArray("directionSteps");
        if (steps != null) {
            for (int i = 0; i < steps.length(); i++) {
                b.directionSteps.add(steps.optString(i, ""));
            }
        }
        return b.build();
    }

    /**
     * Compact JSON consumed by {@link com.cybersec.smartroute.engine.NativeRouteSimulator}
     * and the foreground service. Keys mirror the Kotlin layout so existing sessions
     * persist across the migration.
     */
    public JSONObject toEngineJson(double progress) {
        JSONObject o = new JSONObject();
        try {
            o.put("mode", mode.wireName());
            o.put("startLat", start.latitude);
            o.put("startLon", start.longitude);
            o.put("endLat", end.latitude);
            o.put("endLon", end.longitude);
            JSONArray arr = new JSONArray();
            for (LatLng w : waypoints) arr.put(w.toJson());
            o.put("waypoints", arr);
            o.put("safeLat", safeZone.latitude);
            o.put("safeLon", safeZone.longitude);
            o.put("minSpeedKmh", minSpeedKmh);
            o.put("maxSpeedKmh", maxSpeedKmh);
            o.put("updateIntervalMs", updateIntervalSeconds * 1000L);
            o.put("curved", pathShape.isCurved());
            o.put("acceleration", acceleration.wireName());
            o.put("enablePauses", enablePauses);
            o.put("pauseProbability", pauseProbability);
            o.put("progress", progress);
            o.put("durationMinutes", durationMinutes);
            JSONArray poly = new JSONArray();
            for (LatLng p : routePolyline) poly.put(p.toJson());
            o.put("routePolyline", poly);
        } catch (JSONException ignored) {
        }
        return o;
    }

    public static final class Builder {
        SpoofMode mode = SpoofMode.DYNAMIC_PATH;
        LatLng start = DEFAULT_START;
        LatLng end = DEFAULT_END;
        List<LatLng> waypoints = new ArrayList<>();
        double minSpeedKmh = 40;
        double maxSpeedKmh = 80;
        int updateIntervalSeconds = 2;
        PathShape pathShape = PathShape.STRAIGHT;
        AccelerationPattern acceleration = AccelerationPattern.SMOOTH;
        int durationMinutes = 30;
        LatLng safeZone = DEFAULT_START;
        Integer autoResetMinutes = null;
        boolean enablePauses = false;
        double pauseProbability = 0.02;
        List<LatLng> routePolyline = new ArrayList<>();
        List<String> directionSteps = new ArrayList<>();

        public Builder mode(SpoofMode v) { this.mode = v; return this; }
        public Builder start(LatLng v) { this.start = v; return this; }
        public Builder end(LatLng v) { this.end = v; return this; }
        public Builder waypoints(List<LatLng> v) { this.waypoints = new ArrayList<>(v); return this; }
        public Builder speedRange(double min, double max) {
            this.minSpeedKmh = Math.max(1, Math.min(min, max));
            this.maxSpeedKmh = Math.max(this.minSpeedKmh, max);
            return this;
        }
        public Builder updateIntervalSeconds(int v) { this.updateIntervalSeconds = Math.max(1, v); return this; }
        public Builder pathShape(PathShape v) { this.pathShape = v; return this; }
        public Builder acceleration(AccelerationPattern v) { this.acceleration = v; return this; }
        public Builder durationMinutes(int v) { this.durationMinutes = Math.max(1, v); return this; }
        public Builder safeZone(LatLng v) { this.safeZone = v; return this; }
        public Builder autoResetMinutes(Integer v) { this.autoResetMinutes = v; return this; }
        public Builder enablePauses(boolean v) { this.enablePauses = v; return this; }
        public Builder pauseProbability(double v) { this.pauseProbability = Math.max(0, Math.min(1, v)); return this; }
        public Builder routePolyline(List<LatLng> v) { this.routePolyline = new ArrayList<>(v); return this; }
        public Builder directionSteps(List<String> v) { this.directionSteps = new ArrayList<>(v); return this; }

        public SpoofConfig build() { return new SpoofConfig(this); }
    }
}
