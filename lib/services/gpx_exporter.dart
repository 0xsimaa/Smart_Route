import 'package:intl/intl.dart';

import '../models/lat_lng.dart';

class GpxExporter {
  static String build({
    required String trackName,
    required List<LatLng> points,
    DateTime? startTime,
  }) {
    final started = startTime ?? DateTime.now().toUtc();
    final fmt = DateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
    final buffer = StringBuffer()
      ..writeln('<?xml version="1.0" encoding="UTF-8"?>')
      ..writeln(
        '<gpx version="1.1" creator="Smart Route v2" xmlns="http://www.topografix.com/GPX/1/1">',
      )
      ..writeln('  <metadata>')
      ..writeln('    <name>$trackName</name>')
      ..writeln('    <time>${fmt.format(started)}</time>')
      ..writeln('  </metadata>')
      ..writeln('  <trk>')
      ..writeln('    <name>$trackName</name>')
      ..writeln('    <trkseg>');

    var t = started;
    for (final p in points) {
      buffer.writeln(
        '      <trkpt lat="${p.latitude}" lon="${p.longitude}">'
        '<time>${fmt.format(t)}</time></trkpt>',
      );
      t = t.add(const Duration(seconds: 2));
    }

    buffer
      ..writeln('    </trkseg>')
      ..writeln('  </trk>')
      ..writeln('</gpx>');
    return buffer.toString();
  }
}
