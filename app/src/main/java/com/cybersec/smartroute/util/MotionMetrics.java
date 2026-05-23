package com.cybersec.smartroute.util;

import com.cybersec.smartroute.model.LatLng;

public final class MotionMetrics {

    private MotionMetrics() {
    }

    public static double bearingDegrees(LatLng a, LatLng b) {
        double dLon = Math.toRadians(b.longitude - a.longitude);
        double y = Math.sin(dLon) * Math.cos(Math.toRadians(b.latitude));
        double x = Math.cos(Math.toRadians(a.latitude)) * Math.sin(Math.toRadians(b.latitude))
                - Math.sin(Math.toRadians(a.latitude))
                * Math.cos(Math.toRadians(b.latitude)) * Math.cos(dLon);
        return (Math.toDegrees(Math.atan2(y, x)) + 360) % 360;
    }

    public static double speedMps(LatLng a, LatLng b, double dt) {
        if (dt <= 0) return 0;
        double km = LatLng.haversineKm(a, b);
        return (km * 1000.0) / dt;
    }
}
