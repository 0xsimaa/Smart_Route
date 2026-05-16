import 'lat_lng.dart';

enum SpoofMode { staticLocation, dynamicPath }

enum PathShape { straight, curved }

enum AccelerationPattern { smooth, constant, sudden }

enum PrivacyStatus { idle, active, fallback, leakWarning, error }

class SpoofConfig {
  SpoofConfig({
    this.mode = SpoofMode.dynamicPath,
    this.start = const LatLng(33.6844, 73.0479),
    this.end = const LatLng(34.1688, 73.2215),
    this.waypoints = const [],
    this.minSpeedKmh = 40,
    this.maxSpeedKmh = 80,
    this.updateIntervalSeconds = 2,
    this.pathShape = PathShape.straight,
    this.acceleration = AccelerationPattern.smooth,
    this.durationMinutes = 30,
    this.safeZone = const LatLng(33.6844, 73.0479),
    this.autoResetMinutes,
    this.enablePauses = false,
    this.pauseProbability = 0.02,
  });

  final SpoofMode mode;
  final LatLng start;
  final LatLng end;
  final List<LatLng> waypoints;
  final double minSpeedKmh;
  final double maxSpeedKmh;
  final int updateIntervalSeconds;
  final PathShape pathShape;
  final AccelerationPattern acceleration;
  final int durationMinutes;
  final LatLng safeZone;
  final int? autoResetMinutes;
  final bool enablePauses;
  final double pauseProbability;

  SpoofConfig copyWith({
    SpoofMode? mode,
    LatLng? start,
    LatLng? end,
    List<LatLng>? waypoints,
    double? minSpeedKmh,
    double? maxSpeedKmh,
    int? updateIntervalSeconds,
    PathShape? pathShape,
    AccelerationPattern? acceleration,
    int? durationMinutes,
    LatLng? safeZone,
    int? autoResetMinutes,
    bool? enablePauses,
    double? pauseProbability,
  }) {
    return SpoofConfig(
      mode: mode ?? this.mode,
      start: start ?? this.start,
      end: end ?? this.end,
      waypoints: waypoints ?? this.waypoints,
      minSpeedKmh: minSpeedKmh ?? this.minSpeedKmh,
      maxSpeedKmh: maxSpeedKmh ?? this.maxSpeedKmh,
      updateIntervalSeconds: updateIntervalSeconds ?? this.updateIntervalSeconds,
      pathShape: pathShape ?? this.pathShape,
      acceleration: acceleration ?? this.acceleration,
      durationMinutes: durationMinutes ?? this.durationMinutes,
      safeZone: safeZone ?? this.safeZone,
      autoResetMinutes: autoResetMinutes ?? this.autoResetMinutes,
      enablePauses: enablePauses ?? this.enablePauses,
      pauseProbability: pauseProbability ?? this.pauseProbability,
    );
  }

  Map<String, dynamic> toJson() => {
        'mode': mode.name,
        'start': start.toJson(),
        'end': end.toJson(),
        'waypoints': waypoints.map((e) => e.toJson()).toList(),
        'minSpeedKmh': minSpeedKmh,
        'maxSpeedKmh': maxSpeedKmh,
        'updateIntervalSeconds': updateIntervalSeconds,
        'pathShape': pathShape.name,
        'acceleration': acceleration.name,
        'durationMinutes': durationMinutes,
        'safeZone': safeZone.toJson(),
        'autoResetMinutes': autoResetMinutes,
        'enablePauses': enablePauses,
        'pauseProbability': pauseProbability,
      };

  factory SpoofConfig.fromJson(Map<String, dynamic> json) => SpoofConfig(
        mode: SpoofMode.values.byName(json['mode'] as String),
        start: LatLng.fromJson(json['start'] as Map<String, dynamic>),
        end: LatLng.fromJson(json['end'] as Map<String, dynamic>),
        waypoints: (json['waypoints'] as List<dynamic>? ?? [])
            .map((e) => LatLng.fromJson(e as Map<String, dynamic>))
            .toList(),
        minSpeedKmh: (json['minSpeedKmh'] as num).toDouble(),
        maxSpeedKmh: (json['maxSpeedKmh'] as num).toDouble(),
        updateIntervalSeconds: json['updateIntervalSeconds'] as int,
        pathShape: PathShape.values.byName(json['pathShape'] as String),
        acceleration:
            AccelerationPattern.values.byName(json['acceleration'] as String),
        durationMinutes: json['durationMinutes'] as int,
        safeZone: LatLng.fromJson(json['safeZone'] as Map<String, dynamic>),
        autoResetMinutes: json['autoResetMinutes'] as int?,
        enablePauses: json['enablePauses'] as bool? ?? false,
        pauseProbability: (json['pauseProbability'] as num?)?.toDouble() ?? 0.02,
      );
}
