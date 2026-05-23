package com.cybersec.smartroute.util;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;

import androidx.core.content.ContextCompat;

import com.cybersec.smartroute.model.LatLng;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.gms.tasks.CancellationTokenSource;

/**
 * Compares the most recent injected position against a real (non-mock) GPS
 * fix. If they diverge by more than {@code thresholdMeters}, the safe-zone
 * fallback engages.
 */
public final class LeakDetector {

    public interface Callback {
        void onResult(boolean leak);
    }

    private LeakDetector() {
    }

    @SuppressLint("MissingPermission")
    public static void check(Context context, LatLng injected,
                             double thresholdMeters, Callback cb) {
        if (injected == null) {
            cb.onResult(false);
            return;
        }
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED
                && ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            cb.onResult(false);
            return;
        }
        try {
            FusedLocationProviderClient client =
                    LocationServices.getFusedLocationProviderClient(context.getApplicationContext());
            CancellationTokenSource cts = new CancellationTokenSource();
            client.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, cts.getToken())
                    .addOnSuccessListener(loc -> {
                        if (loc == null) {
                            cb.onResult(false);
                            return;
                        }
                        // If the system actually returned our own injected mock,
                        // skip the comparison – we'd just be checking against
                        // ourselves.
                        if (isFromMock(loc)) {
                            cb.onResult(false);
                            return;
                        }
                        double meters = haversineMeters(loc, injected);
                        cb.onResult(meters > thresholdMeters);
                    })
                    .addOnFailureListener(e -> cb.onResult(false));
        } catch (Exception e) {
            cb.onResult(false);
        }
    }

    private static boolean isFromMock(Location loc) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            return loc.isMock();
        }
        return loc.isFromMockProvider();
    }

    private static double haversineMeters(Location real, LatLng injected) {
        float[] out = new float[1];
        Location.distanceBetween(real.getLatitude(), real.getLongitude(),
                injected.latitude, injected.longitude, out);
        return out[0];
    }
}
