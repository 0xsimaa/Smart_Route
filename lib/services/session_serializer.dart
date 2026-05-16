import 'dart:convert';

import '../models/spoof_config.dart';

/// JSON payload consumed by the Android foreground service / native simulator.
class SessionSerializer {
  static String toNativeJson(SpoofConfig config, {double progress = 0}) {
    return jsonEncode({
      'mode': config.mode.name,
      'startLat': config.start.latitude,
      'startLon': config.start.longitude,
      'endLat': config.end.latitude,
      'endLon': config.end.longitude,
      'safeLat': config.safeZone.latitude,
      'safeLon': config.safeZone.longitude,
      'minSpeedKmh': config.minSpeedKmh,
      'maxSpeedKmh': config.maxSpeedKmh,
      'updateIntervalMs': config.updateIntervalSeconds * 1000,
      'curved': config.pathShape == PathShape.curved,
      'progress': progress,
      'durationMinutes': config.durationMinutes,
    });
  }
}
