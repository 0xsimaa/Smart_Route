package com.cybersec.smartroute.model;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Plain lat/lon pair used throughout the app. Distinct from
 * {@link com.google.android.gms.maps.model.LatLng} so the engine layer
 * has no UI dependency.
 */
public final class LatLng {

    public final double latitude;
    public final double longitude;

    public LatLng(double latitude, double longitude) {
        this.latitude = latitude;
        this.longitude = longitude;
    }

    public JSONObject toJson() {
        JSONObject o = new JSONObject();
        try {
            o.put("latitude", latitude);
            o.put("longitude", longitude);
        } catch (JSONException ignored) {
        }
        return o;
    }

    public static LatLng fromJson(JSONObject o) {
        return new LatLng(o.optDouble("latitude", 0d), o.optDouble("longitude", 0d));
    }

    public com.google.android.gms.maps.model.LatLng toGms() {
        return new com.google.android.gms.maps.model.LatLng(latitude, longitude);
    }

    public static LatLng fromGms(com.google.android.gms.maps.model.LatLng p) {
        return new LatLng(p.latitude, p.longitude);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof LatLng)) return false;
        LatLng that = (LatLng) o;
        return Double.compare(that.latitude, latitude) == 0
                && Double.compare(that.longitude, longitude) == 0;
    }

    @Override
    public int hashCode() {
        return Double.hashCode(latitude) * 31 + Double.hashCode(longitude);
    }

    @Override
    public String toString() {
        return "LatLng(" + latitude + ", " + longitude + ")";
    }

    public static double haversineKm(LatLng a, LatLng b) {
        double r = 6371.0;
        double dLat = Math.toRadians(b.latitude - a.latitude);
        double dLon = Math.toRadians(b.longitude - a.longitude);
        double lat1 = Math.toRadians(a.latitude);
        double lat2 = Math.toRadians(b.latitude);
        double h = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(lat1) * Math.cos(lat2)
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 2 * r * Math.asin(Math.sqrt(h));
    }

    public static LatLng linear(LatLng start, LatLng end, double t) {
        double clamped = Math.max(0, Math.min(1, t));
        return new LatLng(
                start.latitude + (end.latitude - start.latitude) * clamped,
                start.longitude + (end.longitude - start.longitude) * clamped);
    }

    public static LatLng curved(LatLng start, LatLng end, double t, double curve) {
        double dx = end.longitude - start.longitude;
        double dy = end.latitude - start.latitude;
        double midLat = (start.latitude + end.latitude) / 2.0 + dy * curve;
        double midLon = (start.longitude + end.longitude) / 2.0 - dx * curve;
        double u = 1 - Math.max(0, Math.min(1, t));
        double v = 1 - u;
        return new LatLng(
                u * u * start.latitude + 2 * u * v * midLat + v * v * end.latitude,
                u * u * start.longitude + 2 * u * v * midLon + v * v * end.longitude);
    }
}
