import 'dart:math' as math;

class LatLng {
  const LatLng(this.latitude, this.longitude);

  final double latitude;
  final double longitude;

  Map<String, dynamic> toJson() => {
        'latitude': latitude,
        'longitude': longitude,
      };

  factory LatLng.fromJson(Map<String, dynamic> json) => LatLng(
        (json['latitude'] as num).toDouble(),
        (json['longitude'] as num).toDouble(),
      );

  @override
  String toString() => 'LatLng($latitude, $longitude)';

  @override
  bool operator ==(Object other) =>
      other is LatLng &&
      other.latitude == latitude &&
      other.longitude == longitude;

  @override
  int get hashCode => Object.hash(latitude, longitude);
}

double haversineKm(LatLng a, LatLng b) {
  const earthRadiusKm = 6371.0;
  final dLat = _toRad(b.latitude - a.latitude);
  final dLon = _toRad(b.longitude - a.longitude);
  final lat1 = _toRad(a.latitude);
  final lat2 = _toRad(b.latitude);
  final h = math.sin(dLat / 2) * math.sin(dLat / 2) +
      math.cos(lat1) * math.cos(lat2) * math.sin(dLon / 2) * math.sin(dLon / 2);
  return 2 * earthRadiusKm * math.asin(math.sqrt(h));
}

double _toRad(double deg) => deg * math.pi / 180.0;

LatLng interpolate(LatLng start, LatLng end, double t) {
  final clamped = t.clamp(0.0, 1.0);
  return LatLng(
    start.latitude + (end.latitude - start.latitude) * clamped,
    start.longitude + (end.longitude - start.longitude) * clamped,
  );
}

/// Quadratic bezier for curved routes (control point offset from midpoint).
LatLng interpolateCurved(LatLng start, LatLng end, double t, {double curve = 0.15}) {
  final mid = interpolate(start, end, 0.5);
  final dx = end.longitude - start.longitude;
  final dy = end.latitude - start.latitude;
  final control = LatLng(
    mid.latitude + dy * curve,
    mid.longitude - dx * curve,
  );
  final u = 1 - t;
  return LatLng(
    u * u * start.latitude + 2 * u * t * control.latitude + t * t * end.latitude,
    u * u * start.longitude + 2 * u * t * control.longitude + t * t * end.longitude,
  );
}
