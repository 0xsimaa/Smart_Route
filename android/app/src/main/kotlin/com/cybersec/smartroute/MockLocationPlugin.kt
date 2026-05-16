package com.cybersec.smartroute

import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import org.json.JSONObject

class MockLocationPlugin : FlutterPlugin, MethodChannel.MethodCallHandler {
    private lateinit var channel: MethodChannel
    private lateinit var binding: FlutterPlugin.FlutterPluginBinding

    override fun onAttachedToEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        this.binding = binding
        MockLocationEngine.attach(binding.applicationContext)
        channel = MethodChannel(binding.binaryMessenger, "com.cybersec.smartroute/mock_location")
        channel.setMethodCallHandler(this)
    }

    override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        channel.setMethodCallHandler(null)
    }

    override fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {
        val ctx = binding.applicationContext
        when (call.method) {
            "isMockEnabled" -> result.success(MockLocationPermissions.isMockLocationEnabled(ctx))
            "initProvider" -> result.success(MockLocationEngine.initProvider())
            "setLocation" -> {
                val lat = call.argument<Double>("latitude")
                val lon = call.argument<Double>("longitude")
                if (lat == null || lon == null) {
                    result.error("INVALID", "latitude/longitude required", null)
                    return
                }
                val accuracy = (call.argument<Double>("accuracy") ?: 3.0).toFloat()
                val altitude = call.argument<Double>("altitude") ?: 0.0
                val bearing = (call.argument<Double>("bearing") ?: 0.0).toFloat()
                val speed = (call.argument<Double>("speed") ?: 0.0).toFloat()
                result.success(
                    MockLocationEngine.setLocation(lat, lon, accuracy, altitude, bearing, speed),
                )
            }
            "removeProvider" -> {
                MockLocationEngine.removeProvider()
                result.success(true)
            }
            "openDeveloperSettings" -> {
                MockLocationPermissions.openDeveloperSettings(ctx)
                result.success(true)
            }
            "openAppSettings" -> {
                MockLocationPermissions.openAppSettings(ctx)
                result.success(true)
            }
            "startForegroundService" -> {
                val session = call.argument<String>("sessionJson")
                if (session == null) {
                    result.error("INVALID", "sessionJson required", null)
                    return
                }
                MockLocationSessionStore(ctx).saveSession(JSONObject(session))
                MockLocationForegroundService.start(ctx, session)
                result.success(true)
            }
            "stopForegroundService" -> {
                MockLocationForegroundService.stop(ctx)
                MockLocationSessionStore(ctx).clearSession()
                result.success(true)
            }
            "syncSessionProgress" -> {
                val session = call.argument<String>("sessionJson")
                if (session != null) {
                    MockLocationSessionStore(ctx).saveSession(JSONObject(session))
                }
                result.success(true)
            }
            "advanceSession" -> {
                val delta = call.argument<Double>("deltaSeconds") ?: 2.0
                val advance = SessionAdvancer.advance(ctx, delta)
                if (advance == null) {
                    result.success(null)
                } else {
                    result.success(
                        mapOf(
                            "lat" to advance.lat,
                            "lon" to advance.lon,
                            "progress" to advance.progress,
                            "done" to advance.done,
                            "ok" to advance.ok,
                        ),
                    )
                }
            }
            "getSessionStatus" -> {
                val session = MockLocationSessionStore(ctx).loadSession()
                if (session == null) {
                    result.success(null)
                } else {
                    result.success(
                        mapOf(
                            "lat" to session.optDouble("lastLat", session.getDouble("startLat")),
                            "lon" to session.optDouble("lastLon", session.getDouble("startLon")),
                            "progress" to session.optDouble("progress", 0.0),
                            "done" to (session.optDouble("progress", 0.0) >= 1.0),
                            "active" to true,
                        ),
                    )
                }
            }
            else -> result.notImplemented()
        }
    }
}
