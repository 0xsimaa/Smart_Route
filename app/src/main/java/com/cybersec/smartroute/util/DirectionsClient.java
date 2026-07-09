package com.cybersec.smartroute.util;

import android.util.Log;

import com.cybersec.smartroute.model.LatLng;
import com.google.maps.android.PolyUtil;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Fetches a road-following route from the Google Directions API and decodes
 * the overview polyline into {@link LatLng} points for simulation.
 */
public final class DirectionsClient {

    private static final String TAG = "DirectionsClient";

    private DirectionsClient() {
    }

    public static final class RouteResult {
        public final List<LatLng> polyline;
        public final List<DirectionStep> steps;
        public final double distanceMeters;
        public final long durationSeconds;

        public RouteResult(List<LatLng> polyline, List<DirectionStep> steps,
                           double distanceMeters, long durationSeconds) {
            this.polyline = polyline;
            this.steps = steps;
            this.distanceMeters = distanceMeters;
            this.durationSeconds = durationSeconds;
        }
    }

    public static final class DirectionStep {
        public final String instruction;
        public final String maneuver;
        public final double distanceMeters;

        public DirectionStep(String instruction, String maneuver, double distanceMeters) {
            this.instruction = instruction;
            this.maneuver = maneuver;
            this.distanceMeters = distanceMeters;
        }
    }

    public static RouteResult fetch(LatLng origin, LatLng destination,
                                    List<LatLng> viaWaypoints, String apiKey)
            throws Exception {
        if (apiKey == null || apiKey.isEmpty()
                || "YOUR_GOOGLE_MAPS_API_KEY".equals(apiKey)) {
            throw new IllegalStateException("Maps API key is missing or invalid");
        }

        StringBuilder url = new StringBuilder("https://maps.googleapis.com/maps/api/directions/json?");
        url.append("origin=").append(encode(origin.latitude + "," + origin.longitude));
        url.append("&destination=").append(encode(destination.latitude + "," + destination.longitude));
        if (viaWaypoints != null && !viaWaypoints.isEmpty()) {
            StringBuilder wp = new StringBuilder();
            for (LatLng w : viaWaypoints) {
                if (wp.length() > 0) wp.append('|');
                wp.append(w.latitude).append(',').append(w.longitude);
            }
            url.append("&waypoints=").append(encode(wp.toString()));
        }
        url.append("&mode=driving");
        url.append("&key=").append(encode(apiKey));

        String body = httpGet(url.toString());
        JSONObject json = new JSONObject(body);
        String status = json.optString("status", "UNKNOWN");
        if (!"OK".equals(status)) {
            String err = json.optString("error_message", status);
            throw new IllegalStateException("Directions API: " + err);
        }

        JSONArray routes = json.optJSONArray("routes");
        if (routes == null || routes.length() == 0) {
            throw new IllegalStateException("Directions API returned no routes");
        }
        JSONObject route = routes.getJSONObject(0);

        JSONObject overview = route.getJSONObject("overview_polyline");
        String encoded = overview.getString("points");
        List<com.google.android.gms.maps.model.LatLng> gmsPoints = PolyUtil.decode(encoded);
        List<LatLng> polyline = new ArrayList<>(gmsPoints.size());
        for (com.google.android.gms.maps.model.LatLng p : gmsPoints) {
            polyline.add(new LatLng(p.latitude, p.longitude));
        }
        polyline = downsample(polyline, 500);

        List<DirectionStep> steps = new ArrayList<>();
        JSONArray legs = route.optJSONArray("legs");
        double totalDist = 0;
        long totalDur = 0;
        if (legs != null) {
            for (int i = 0; i < legs.length(); i++) {
                JSONObject leg = legs.getJSONObject(i);
                totalDist += leg.getJSONObject("distance").getDouble("value");
                totalDur += leg.getJSONObject("duration").getLong("value");
                JSONArray legSteps = leg.optJSONArray("steps");
                if (legSteps == null) continue;
                for (int j = 0; j < legSteps.length(); j++) {
                    JSONObject step = legSteps.getJSONObject(j);
                    String html = step.optString("html_instructions", "");
                    String plain = html.replaceAll("<[^>]+>", " ").replaceAll("\\s+", " ").trim();
                    String maneuver = step.optString("maneuver", "straight");
                    double dist = step.getJSONObject("distance").getDouble("value");
                    steps.add(new DirectionStep(plain, maneuver, dist));
                }
            }
        }

        return new RouteResult(polyline, steps, totalDist, totalDur);
    }

    /** Keep routes manageable for the simulator while preserving road shape. */
    static List<LatLng> downsample(List<LatLng> points, int maxPoints) {
        if (points.size() <= maxPoints) return points;
        List<LatLng> out = new ArrayList<>(maxPoints);
        double step = (double) (points.size() - 1) / (maxPoints - 1);
        for (int i = 0; i < maxPoints; i++) {
            int idx = (int) Math.round(i * step);
            idx = Math.min(idx, points.size() - 1);
            out.add(points.get(idx));
        }
        return out;
    }

    private static String httpGet(String urlStr) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(15_000);
        conn.setReadTimeout(15_000);
        try {
            int code = conn.getResponseCode();
            BufferedReader reader = new BufferedReader(new InputStreamReader(
                    code >= 400 ? conn.getErrorStream() : conn.getInputStream(),
                    StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
            reader.close();
            if (code >= 400) {
                Log.w(TAG, "HTTP " + code + ": " + sb);
                throw new IllegalStateException("Directions HTTP " + code);
            }
            return sb.toString();
        } finally {
            conn.disconnect();
        }
    }

    private static String encode(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    public static JSONArray polylineToJson(List<LatLng> points) {
        JSONArray arr = new JSONArray();
        if (points == null) return arr;
        for (LatLng p : points) arr.put(p.toJson());
        return arr;
    }

    public static List<LatLng> polylineFromJson(JSONArray arr) {
        if (arr == null || arr.length() == 0) return Collections.emptyList();
        List<LatLng> out = new ArrayList<>(arr.length());
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o != null) out.add(LatLng.fromJson(o));
        }
        return out;
    }
}
