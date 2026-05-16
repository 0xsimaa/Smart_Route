package com.cybersec.smartroute

import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.SystemClock

/**
 * Injects GPS fixes through Android's test location provider.
 * Used by the Flutter UI layer and the foreground service.
 */
object MockLocationEngine {
    const val PROVIDER = "smart_route_gps"

    private var locationManager: LocationManager? = null

    fun attach(context: Context) {
        if (locationManager == null) {
            locationManager =
                context.applicationContext.getSystemService(Context.LOCATION_SERVICE)
                    as LocationManager
        }
    }

    fun initProvider(): Boolean {
        val lm = locationManager ?: return false
        return try {
            removeProvider()
            lm.addTestProvider(
                PROVIDER,
                false,
                false,
                false,
                false,
                true,
                true,
                true,
                android.location.Criteria.POWER_LOW,
                android.location.Criteria.ACCURACY_FINE,
            )
            lm.setTestProviderEnabled(PROVIDER, true)
            true
        } catch (_: SecurityException) {
            false
        } catch (_: IllegalArgumentException) {
            try {
                lm.setTestProviderEnabled(PROVIDER, true)
                true
            } catch (_: Exception) {
                false
            }
        }
    }

    fun setLocation(
        lat: Double,
        lon: Double,
        accuracy: Float = 3f,
        altitude: Double = 0.0,
        bearing: Float = 0f,
        speed: Float = 0f,
    ): Boolean {
        val lm = locationManager ?: return false
        return try {
            if (!lm.allProviders.contains(PROVIDER) || !lm.isProviderEnabled(PROVIDER)) {
                if (!initProvider()) return false
            }
            val location = Location(PROVIDER).apply {
                latitude = lat
                longitude = lon
                this.accuracy = accuracy
                this.altitude = altitude
                this.bearing = bearing
                this.speed = speed
                time = System.currentTimeMillis()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                    elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
                }
            }
            lm.setTestProviderLocation(PROVIDER, location)
            true
        } catch (_: SecurityException) {
            false
        }
    }

    fun removeProvider() {
        val lm = locationManager ?: return
        try {
            if (lm.allProviders.contains(PROVIDER)) {
                lm.setTestProviderEnabled(PROVIDER, false)
                lm.removeTestProvider(PROVIDER)
            }
        } catch (_: Exception) {
        }
    }
}
