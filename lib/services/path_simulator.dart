import 'dart:math' as math;

import '../models/lat_lng.dart';
import '../models/spoof_config.dart';

/// Generates smooth position updates along a route using speed and easing.
class PathSimulator {
  PathSimulator(this.config);

  final SpoofConfig config;
  final math.Random _rng = math.Random();

  double _progress = 0;
  double _currentSpeedKmh = 0;
  bool _paused = false;
  int _pauseTicksLeft = 0;

  LatLng get currentPosition => _positionAt(_progress);

  double get progress => _progress;

  bool get isComplete => _progress >= 1.0;

  void reset() {
    _progress = 0;
    _currentSpeedKmh = config.minSpeedKmh;
    _paused = false;
    _pauseTicksLeft = 0;
  }

  /// Advance simulation by [deltaSeconds]. Returns new coordinates.
  LatLng tick(double deltaSeconds) {
    if (isComplete) return _positionAt(1.0);

    if (_pauseTicksLeft > 0) {
      _pauseTicksLeft--;
      return _positionAt(_progress);
    }

    if (config.enablePauses &&
        !_paused &&
        _rng.nextDouble() < config.pauseProbability) {
      _paused = true;
      _pauseTicksLeft = 2 + _rng.nextInt(4);
      return _positionAt(_progress);
    }
    _paused = false;

    final totalKm = _routeLengthKm();
    if (totalKm <= 0) {
      _progress = 1.0;
      return _positionAt(1.0);
    }

    final targetSpeed = _targetSpeedKmh();
    _currentSpeedKmh = _applyAcceleration(_currentSpeedKmh, targetSpeed, deltaSeconds);

    final distanceKm = (_currentSpeedKmh / 3600.0) * deltaSeconds;
    _progress = (_progress + distanceKm / totalKm).clamp(0.0, 1.0);

    return _positionAt(_progress);
  }

  List<LatLng> previewPath({int points = 80}) {
    return List.generate(points + 1, (i) => _positionAt(i / points));
  }

  double _routeLengthKm() {
    final segments = _segments();
    var total = 0.0;
    for (final s in segments) {
      total += haversineKm(s.$1, s.$2);
    }
    return total;
  }

  List<(LatLng, LatLng)> _segments() {
    final pts = [config.start, ...config.waypoints, config.end];
    final out = <(LatLng, LatLng)>[];
    for (var i = 0; i < pts.length - 1; i++) {
      out.add((pts[i], pts[i + 1]));
    }
    return out;
  }

  LatLng _positionAt(double globalT) {
    final segments = _segments();
    if (segments.isEmpty) return config.start;
    final lengths = segments.map((s) => haversineKm(s.$1, s.$2)).toList();
    final total = lengths.fold(0.0, (a, b) => a + b);
    if (total == 0) return config.start;

    var remaining = globalT.clamp(0.0, 1.0) * total;
    for (var i = 0; i < segments.length; i++) {
      final len = lengths[i];
      if (remaining <= len || i == segments.length - 1) {
        final localT = len == 0 ? 1.0 : (remaining / len).clamp(0.0, 1.0);
        return _interpSegment(segments[i].$1, segments[i].$2, localT);
      }
      remaining -= len;
    }
    return config.end;
  }

  LatLng _interpSegment(LatLng a, LatLng b, double t) {
    if (config.pathShape == PathShape.curved) {
      return interpolateCurved(a, b, t);
    }
    return interpolate(a, b, t);
  }

  double _targetSpeedKmh() {
    final mid = 0.5;
    final edge = (_progress - mid).abs() * 2;
    final lerp = config.minSpeedKmh +
        (config.maxSpeedKmh - config.minSpeedKmh) * (1 - edge * 0.3);
    return lerp.clamp(config.minSpeedKmh, config.maxSpeedKmh);
  }

  double _applyAcceleration(double current, double target, double dt) {
    switch (config.acceleration) {
      case AccelerationPattern.constant:
        return target;
      case AccelerationPattern.sudden:
        return (current + (target - current).sign * 25 * dt).clamp(
          config.minSpeedKmh,
          config.maxSpeedKmh,
        );
      case AccelerationPattern.smooth:
        final rate = 8.0;
        return current + (target - current) * (1 - math.exp(-rate * dt));
    }
  }
}
