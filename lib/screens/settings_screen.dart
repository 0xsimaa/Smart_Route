import 'package:flutter/material.dart';
import 'package:google_maps_flutter/google_maps_flutter.dart';

import '../models/lat_lng.dart' as model;
import '../models/spoof_config.dart';
import '../services/spoof_controller.dart';

class SettingsScreen extends StatefulWidget {
  const SettingsScreen({super.key, required this.controller});

  final SpoofController controller;

  @override
  State<SettingsScreen> createState() => _SettingsScreenState();
}

class _SettingsScreenState extends State<SettingsScreen> {
  late double _minSpeed;
  late double _maxSpeed;
  late int _interval;
  late int _duration;
  late int? _autoReset;
  late PathShape _shape;
  late AccelerationPattern _accel;
  late bool _pauses;

  @override
  void initState() {
    super.initState();
    _loadFromConfig(widget.controller.config);
  }

  void _loadFromConfig(SpoofConfig c) {
    _minSpeed = c.minSpeedKmh;
    _maxSpeed = c.maxSpeedKmh;
    _interval = c.updateIntervalSeconds;
    _duration = c.durationMinutes;
    _autoReset = c.autoResetMinutes;
    _shape = c.pathShape;
    _accel = c.acceleration;
    _pauses = c.enablePauses;
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Simulation settings')),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          Text('Speed (km/h)', style: Theme.of(context).textTheme.titleSmall),
          RangeSlider(
            values: RangeValues(_minSpeed, _maxSpeed),
            min: 10,
            max: 120,
            divisions: 22,
            labels: RangeLabels(
              _minSpeed.round().toString(),
              _maxSpeed.round().toString(),
            ),
            onChanged: (v) => setState(() {
              _minSpeed = v.start;
              _maxSpeed = v.end;
            }),
          ),
          Text('Update interval: $_interval s'),
          Slider(
            value: _interval.toDouble(),
            min: 1,
            max: 5,
            divisions: 4,
            label: '$_interval s',
            onChanged: (v) => setState(() => _interval = v.round()),
          ),
          Text('Journey duration: $_duration min'),
          Slider(
            value: _duration.toDouble(),
            min: 5,
            max: 120,
            divisions: 23,
            label: '$_duration min',
            onChanged: (v) => setState(() => _duration = v.round()),
          ),
          SwitchListTile(
            title: const Text('Natural pauses'),
            subtitle: const Text('Brief stops along the route'),
            value: _pauses,
            onChanged: (v) => setState(() => _pauses = v),
          ),
          const Divider(),
          Text('Path shape', style: Theme.of(context).textTheme.titleSmall),
          SegmentedButton<PathShape>(
            segments: const [
              ButtonSegment(value: PathShape.straight, label: Text('Straight')),
              ButtonSegment(value: PathShape.curved, label: Text('Curved')),
            ],
            selected: {_shape},
            onSelectionChanged: (s) => setState(() => _shape = s.first),
          ),
          const SizedBox(height: 12),
          Text('Acceleration', style: Theme.of(context).textTheme.titleSmall),
          DropdownButton<AccelerationPattern>(
            isExpanded: true,
            value: _accel,
            items: AccelerationPattern.values
                .map(
                  (e) => DropdownMenuItem(
                    value: e,
                    child: Text(e.name),
                  ),
                )
                .toList(),
            onChanged: (v) => setState(() => _accel = v!),
          ),
          const SizedBox(height: 12),
          Text('Auto-reset window (optional)'),
          Slider(
            value: (_autoReset ?? 0).toDouble(),
            min: 0,
            max: 180,
            divisions: 18,
            label: _autoReset == null || _autoReset == 0
                ? 'Off'
                : '$_autoReset min',
            onChanged: (v) => setState(() {
              _autoReset = v.round() == 0 ? null : v.round();
            }),
          ),
          const Divider(),
          ListTile(
            title: const Text('Safe zone fallback'),
            subtitle: Text(
              '${widget.controller.config.safeZone.latitude.toStringAsFixed(4)}, '
              '${widget.controller.config.safeZone.longitude.toStringAsFixed(4)}',
            ),
            trailing: const Icon(Icons.shield),
            onTap: _pickSafeZone,
          ),
          const SizedBox(height: 24),
          FilledButton(
            onPressed: _save,
            child: const Text('Apply settings'),
          ),
        ],
      ),
    );
  }

  Future<void> _pickSafeZone() async {
    final c = widget.controller.config.safeZone;
    if (!mounted) return;
    await Navigator.push(
      context,
      MaterialPageRoute(
        builder: (_) => _SafeZonePicker(
          initial: LatLng(c.latitude, c.longitude),
          onPicked: (p) {
            widget.controller.updateConfig(
              widget.controller.config.copyWith(
                safeZone: model.LatLng(p.latitude, p.longitude),
              ),
            );
          },
        ),
      ),
    );
    setState(() {});
  }

  void _save() {
    final c = widget.controller.config;
    widget.controller.updateConfig(
      c.copyWith(
        minSpeedKmh: _minSpeed,
        maxSpeedKmh: _maxSpeed,
        updateIntervalSeconds: _interval,
        durationMinutes: _duration,
        autoResetMinutes: _autoReset,
        pathShape: _shape,
        acceleration: _accel,
        enablePauses: _pauses,
      ),
    );
    Navigator.pop(context);
  }
}

class _SafeZonePicker extends StatefulWidget {
  const _SafeZonePicker({required this.initial, required this.onPicked});

  final LatLng initial;
  final void Function(LatLng) onPicked;

  @override
  State<_SafeZonePicker> createState() => _SafeZonePickerState();
}

class _SafeZonePickerState extends State<_SafeZonePicker> {
  late LatLng _pos;

  @override
  void initState() {
    super.initState();
    _pos = widget.initial;
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Pick safe zone')),
      body: GoogleMap(
        initialCameraPosition: CameraPosition(target: _pos, zoom: 12),
        markers: {
          Marker(
            markerId: const MarkerId('safe'),
            position: _pos,
            draggable: true,
            onDragEnd: (p) => setState(() => _pos = p),
          ),
        },
        onTap: (p) => setState(() => _pos = p),
      ),
      floatingActionButton: FloatingActionButton(
        onPressed: () {
          widget.onPicked(_pos);
          Navigator.pop(context);
        },
        child: const Icon(Icons.check),
      ),
    );
  }
}
