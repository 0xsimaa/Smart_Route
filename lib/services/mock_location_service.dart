import 'package:flutter/services.dart';
import 'package:geolocator/geolocator.dart';

import '../models/lat_lng.dart';

/// Bridges Flutter to Android test-provider GPS injection + foreground service.
class MockLocationService {
  static const _channel = MethodChannel('com.cybersec.smartroute/mock_location');

  Future<bool> isMockLocationEnabled() async {
    try {
      return await _channel.invokeMethod<bool>('isMockEnabled') ?? false;
    } on PlatformException {
      return false;
    }
  }

  Future<bool> initializeProvider() async {
    try {
      return await _channel.invokeMethod<bool>('initProvider') ?? false;
    } on PlatformException {
      return false;
    }
  }

  Future<bool> pushLocation(
    LatLng position, {
    double accuracy = 3,
    double bearing = 0,
    double speedMps = 0,
    double altitude = 0,
  }) async {
    try {
      return await _channel.invokeMethod<bool>('setLocation', {
            'latitude': position.latitude,
            'longitude': position.longitude,
            'accuracy': accuracy,
            'altitude': altitude,
            'bearing': bearing,
            'speed': speedMps,
          }) ??
          false;
    } on PlatformException {
      return false;
    }
  }

  Future<void> removeProvider() async {
    try {
      await _channel.invokeMethod<void>('removeProvider');
    } on PlatformException {
      // ignored
    }
  }

  Future<void> openDeveloperSettings() async {
    try {
      await _channel.invokeMethod<void>('openDeveloperSettings');
    } on PlatformException {
      // ignored
    }
  }

  Future<void> openAppSettings() async {
    try {
      await _channel.invokeMethod<void>('openAppSettings');
    } on PlatformException {
      // ignored
    }
  }

  Future<void> startForegroundService(String sessionJson) async {
    await _channel.invokeMethod<void>('startForegroundService', {
      'sessionJson': sessionJson,
    });
  }

  Future<void> stopForegroundService() async {
    await _channel.invokeMethod<void>('stopForegroundService');
  }

  Future<void> syncSessionProgress(String sessionJson) async {
    await _channel.invokeMethod<void>('syncSessionProgress', {
      'sessionJson': sessionJson,
    });
  }

  /// Single native tick: inject GPS + return new coordinates (Android).
  Future<NativeAdvanceResult?> advanceSession(double deltaSeconds) async {
    try {
      final map = await _channel.invokeMethod<Map<Object?, Object?>>(
        'advanceSession',
        {'deltaSeconds': deltaSeconds},
      );
      if (map == null) return null;
      return NativeAdvanceResult(
        lat: (map['lat'] as num).toDouble(),
        lon: (map['lon'] as num).toDouble(),
        progress: (map['progress'] as num).toDouble(),
        done: map['done'] as bool,
        ok: map['ok'] as bool,
      );
    } on PlatformException {
      return null;
    }
  }

  Future<NativeAdvanceResult?> getSessionStatus() async {
    try {
      final map = await _channel.invokeMethod<Map<Object?, Object?>>(
        'getSessionStatus',
      );
      if (map == null) return null;
      return NativeAdvanceResult(
        lat: (map['lat'] as num).toDouble(),
        lon: (map['lon'] as num).toDouble(),
        progress: (map['progress'] as num).toDouble(),
        done: map['done'] as bool? ?? false,
        ok: true,
      );
    } on PlatformException {
      return null;
    }
  }

  Future<bool> detectPossibleLeak(LatLng lastMock, {double thresholdM = 150}) async {
    try {
      final perm = await Geolocator.checkPermission();
      if (perm == LocationPermission.denied ||
          perm == LocationPermission.deniedForever) {
        return false;
      }
      final real = await Geolocator.getCurrentPosition(
        locationSettings: const LocationSettings(
          accuracy: LocationAccuracy.low,
          timeLimit: Duration(seconds: 3),
        ),
      );
      final dist = Geolocator.distanceBetween(
        real.latitude,
        real.longitude,
        lastMock.latitude,
        lastMock.longitude,
      );
      return dist > thresholdM;
    } catch (_) {
      return false;
    }
  }
}

class NativeAdvanceResult {
  const NativeAdvanceResult({
    required this.lat,
    required this.lon,
    required this.progress,
    required this.done,
    required this.ok,
  });

  final double lat;
  final double lon;
  final double progress;
  final bool done;
  final bool ok;
}
