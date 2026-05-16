import 'package:flutter_test/flutter_test.dart';
import 'package:smart_route/models/lat_lng.dart';
import 'package:smart_route/models/spoof_config.dart';
import 'package:smart_route/services/path_simulator.dart';

void main() {
  test('Islamabad to Abbottabad progresses without exceeding bounds', () {
    final config = SpoofConfig(
      start: const LatLng(33.6844, 73.0479),
      end: const LatLng(34.1688, 73.2215),
      minSpeedKmh: 50,
      maxSpeedKmh: 50,
      durationMinutes: 30,
    );
    final sim = PathSimulator(config);
    final totalKm = haversineKm(config.start, config.end);

    var ticks = 0;
    while (!sim.isComplete && ticks < 5000) {
      sim.tick(2);
      ticks++;
    }

    expect(sim.isComplete, isTrue);
    expect(totalKm, greaterThan(40));
    final endPos = sim.currentPosition;
    expect(endPos.latitude, closeTo(config.end.latitude, 0.05));
    expect(endPos.longitude, closeTo(config.end.longitude, 0.05));
  });

  test('static mode equivalent: zero-distance route completes immediately', () {
    final config = SpoofConfig(
      start: const LatLng(1, 1),
      end: const LatLng(1, 1),
    );
    final sim = PathSimulator(config)..tick(1);
    expect(sim.isComplete, isTrue);
  });
}
