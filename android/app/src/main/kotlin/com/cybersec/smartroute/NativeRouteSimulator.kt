package com.cybersec.smartroute

import org.json.JSONObject
import kotlin.math.*

/**
 * Native route interpolation — keeps mock GPS running if the Flutter isolate pauses.
 */
class NativeRouteSimulator(private val session: JSONObject) {
    private var progress = session.optDouble("progress", 0.0)
    private var currentSpeedKmh = session.optDouble("minSpeedKmh", 40.0)

    private val mode: String = session.optString("mode", "dynamicPath")
    private val startLat = session.getDouble("startLat")
    private val startLon = session.getDouble("startLon")
    private val endLat = session.getDouble("endLat")
    private val endLon = session.getDouble("endLon")
    private val safeLat = session.optDouble("safeLat", startLat)
    private val safeLon = session.optDouble("safeLon", startLon)
    private val minSpeed = session.optDouble("minSpeedKmh", 40.0)
    private val maxSpeed = session.optDouble("maxSpeedKmh", 80.0)
    private val curved = session.optBoolean("curved", false)

    val isComplete: Boolean get() = progress >= 1.0

    fun tick(deltaSeconds: Double): Triple<Double, Double, Boolean> {
        if (mode == "staticLocation") {
            return Triple(startLat, startLon, false)
        }
        if (isComplete) return Triple(endLat, endLon, true)

        val totalKm = haversineKm(startLat, startLon, endLat, endLon)
        if (totalKm <= 0.0) {
            progress = 1.0
            return Triple(endLat, endLon, true)
        }

        val target = minSpeed + (maxSpeed - minSpeed) * 0.5
        currentSpeedKmh += (target - currentSpeedKmh) * (1 - exp(-8.0 * deltaSeconds))

        val distanceKm = (currentSpeedKmh / 3600.0) * deltaSeconds
        progress = (progress + distanceKm / totalKm).coerceIn(0.0, 1.0)

        val t = progress
        val (lat, lon) = if (curved) {
            bezier(startLat, startLon, endLat, endLon, t)
        } else {
            linear(startLat, startLon, endLat, endLon, t)
        }
        return Triple(lat, lon, progress >= 1.0)
    }

    fun safeZone(): Pair<Double, Double> = safeLat to safeLon

    fun toJson(): JSONObject {
        val copy = JSONObject(session.toString())
        copy.put("progress", progress)
        return copy
    }

    private fun linear(
        sLat: Double, sLon: Double, eLat: Double, eLon: Double, t: Double,
    ): Pair<Double, Double> {
        val u = t.coerceIn(0.0, 1.0)
        return (sLat + (eLat - sLat) * u) to (sLon + (eLon - sLon) * u)
    }

    private fun bezier(
        sLat: Double, sLon: Double, eLat: Double, eLon: Double, t: Double,
    ): Pair<Double, Double> {
        val u = t.coerceIn(0.0, 1.0)
        val midLat = (sLat + eLat) / 2
        val midLon = (sLon + eLon) / 2
        val dy = eLat - sLat
        val dx = eLon - sLon
        val cLat = midLat + dy * 0.15
        val cLon = midLon - dx * 0.15
        val lat = (1 - u) * (1 - u) * sLat + 2 * (1 - u) * u * cLat + u * u * eLat
        val lon = (1 - u) * (1 - u) * sLon + 2 * (1 - u) * u * cLon + u * u * eLon
        return lat to lon
    }

    private fun haversineKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        return 2 * r * asin(sqrt(a))
    }
}
