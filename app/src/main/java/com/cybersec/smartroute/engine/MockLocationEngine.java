package com.cybersec.smartroute.engine;

import android.content.Context;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationManager;
import android.os.Build;
import android.os.SystemClock;
import android.util.Log;

/**
 * Real mock-GPS injector. Registers a test provider with the system
 * {@link LocationManager} and pushes coordinates that any other app reading
 * GPS will receive (when the device has Smart Route selected as the mock
 * location app under Developer options).
 */
public final class MockLocationEngine {

    private static final String TAG = "MockLocationEngine";
    public static final String PROVIDER = "smart_route_gps";

    private static volatile LocationManager locationManager;

    private MockLocationEngine() {
    }

    public static void attach(Context context) {
        if (locationManager == null) {
            synchronized (MockLocationEngine.class) {
                if (locationManager == null) {
                    locationManager = (LocationManager) context.getApplicationContext()
                            .getSystemService(Context.LOCATION_SERVICE);
                }
            }
        }
    }

    public static boolean initProvider() {
        LocationManager lm = locationManager;
        if (lm == null) return false;
        try {
            removeProvider();
            lm.addTestProvider(
                    PROVIDER,
                    /* requiresNetwork */ false,
                    /* requiresSatellite */ false,
                    /* requiresCell */ false,
                    /* hasMonetaryCost */ false,
                    /* supportsAltitude */ true,
                    /* supportsSpeed */ true,
                    /* supportsBearing */ true,
                    Criteria.POWER_LOW,
                    Criteria.ACCURACY_FINE);
            lm.setTestProviderEnabled(PROVIDER, true);
            return true;
        } catch (SecurityException se) {
            Log.w(TAG, "Mock provider registration denied: " + se.getMessage());
            return false;
        } catch (IllegalArgumentException iae) {
            try {
                lm.setTestProviderEnabled(PROVIDER, true);
                return true;
            } catch (Exception ex) {
                Log.w(TAG, "Provider re-enable failed: " + ex.getMessage());
                return false;
            }
        } catch (Exception ex) {
            Log.e(TAG, "initProvider failed", ex);
            return false;
        }
    }

    public static boolean setLocation(double lat, double lon) {
        return setLocation(lat, lon, 3f, 0d, 0f, 0f);
    }

    public static boolean setLocation(double lat, double lon, float accuracy,
                                      double altitude, float bearing, float speed) {
        LocationManager lm = locationManager;
        if (lm == null) return false;
        try {
            if (!lm.getAllProviders().contains(PROVIDER) || !lm.isProviderEnabled(PROVIDER)) {
                if (!initProvider()) return false;
            }
            Location location = new Location(PROVIDER);
            location.setLatitude(lat);
            location.setLongitude(lon);
            location.setAccuracy(accuracy);
            location.setAltitude(altitude);
            location.setBearing(bearing);
            location.setSpeed(speed);
            location.setTime(System.currentTimeMillis());
            location.setElapsedRealtimeNanos(SystemClock.elapsedRealtimeNanos());
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                location.setBearingAccuracyDegrees(0.5f);
                location.setSpeedAccuracyMetersPerSecond(0.5f);
                location.setVerticalAccuracyMeters(2f);
            }
            lm.setTestProviderLocation(PROVIDER, location);
            return true;
        } catch (SecurityException se) {
            Log.w(TAG, "setTestProviderLocation denied: " + se.getMessage());
            return false;
        } catch (Exception ex) {
            Log.e(TAG, "setLocation failed", ex);
            return false;
        }
    }

    public static void removeProvider() {
        LocationManager lm = locationManager;
        if (lm == null) return;
        try {
            if (lm.getAllProviders().contains(PROVIDER)) {
                lm.setTestProviderEnabled(PROVIDER, false);
                lm.removeTestProvider(PROVIDER);
            }
        } catch (Exception ignored) {
        }
    }
}
