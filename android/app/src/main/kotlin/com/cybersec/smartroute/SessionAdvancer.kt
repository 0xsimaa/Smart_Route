package com.cybersec.smartroute

import org.json.JSONObject

data class AdvanceResult(
    val lat: Double,
    val lon: Double,
    val progress: Double,
    val done: Boolean,
    val ok: Boolean,
)

object SessionAdvancer {
    @Volatile
    private var lastAdvanceMs = 0L

    fun advance(context: android.content.Context, deltaSeconds: Double): AdvanceResult? {
        val store = MockLocationSessionStore(context)
        val session = store.loadSession() ?: return null
        val intervalMs = session.optInt("updateIntervalMs", 2000).toLong()
        val now = System.currentTimeMillis()
        if (now - lastAdvanceMs < (intervalMs * 0.85).toLong()) {
            return statusFromSession(session)
        }
        lastAdvanceMs = now
        val sim = NativeRouteSimulator(session)
        val prevLat = session.optDouble("lastLat", session.getDouble("startLat"))
        val prevLon = session.optDouble("lastLon", session.getDouble("startLon"))

        val (lat, lon, done) = sim.tick(deltaSeconds)
        val bearing = bearing(prevLat, prevLon, lat, lon)
        val speed = speedMps(prevLat, prevLon, lat, lon, deltaSeconds)

        val ok = MockLocationEngine.setLocation(
            lat = lat,
            lon = lon,
            bearing = bearing,
            speed = speed,
        )

        val updated = sim.toJson().apply {
            put("lastLat", lat)
            put("lastLon", lon)
            put("progress", sim.toJson().optDouble("progress", 0.0))
        }
        store.saveSession(updated)

        return AdvanceResult(
            lat = lat,
            lon = lon,
            progress = updated.optDouble("progress", 0.0),
            done = done,
            ok = ok,
        )
    }

    fun statusFromSession(session: JSONObject): AdvanceResult {
        val lat = session.optDouble("lastLat", session.getDouble("startLat"))
        val lon = session.optDouble("lastLon", session.getDouble("startLon"))
        val progress = session.optDouble("progress", 0.0)
        return AdvanceResult(lat, lon, progress, progress >= 1.0, true)
    }

    private fun bearing(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
        val dLon = Math.toRadians(lon2 - lon1)
        val y = kotlin.math.sin(dLon) * kotlin.math.cos(Math.toRadians(lat2))
        val x = kotlin.math.cos(Math.toRadians(lat1)) * kotlin.math.sin(Math.toRadians(lat2)) -
            kotlin.math.sin(Math.toRadians(lat1)) * kotlin.math.cos(Math.toRadians(lat2)) * kotlin.math.cos(dLon)
        return ((Math.toDegrees(kotlin.math.atan2(y, x)) + 360) % 360).toFloat()
    }

    private fun speedMps(
        lat1: Double, lon1: Double, lat2: Double, lon2: Double, dt: Double,
    ): Float {
        if (dt <= 0) return 0f
        val r = 6371000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = kotlin.math.sin(dLat / 2).pow(2) +
            kotlin.math.cos(Math.toRadians(lat1)) * kotlin.math.cos(Math.toRadians(lat2)) *
            kotlin.math.sin(dLon / 2).pow(2)
        val dist = 2 * r * kotlin.math.asin(kotlin.math.sqrt(a))
        return (dist / dt).toFloat()
    }
}
