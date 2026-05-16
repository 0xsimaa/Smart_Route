import 'package:flutter/material.dart';
import 'package:google_maps_flutter/google_maps_flutter.dart';

import '../models/lat_lng.dart' as model;
import '../models/spoof_config.dart';
import '../services/path_simulator.dart';
import '../services/spoof_controller.dart';

class MapScreen extends StatefulWidget {
  const MapScreen({super.key, required this.controller});

  final SpoofController controller;

  @override
  State<MapScreen> createState() => _MapScreenState();
}

class _MapScreenState extends State<MapScreen> {
  GoogleMapController? _mapController;

  @override
  Widget build(BuildContext context) {
    return ListenableBuilder(
      listenable: widget.controller,
      builder: (context, _) {
        final config = widget.controller.config;
        final preview = PathSimulator(config).previewPath();
        final polyline = Polyline(
          polylineId: const PolylineId('route'),
          points: preview
              .map((p) => LatLng(p.latitude, p.longitude))
              .toList(),
          color: Colors.blue,
          width: 4,
        );
        final live = widget.controller.trajectory
            .map((p) => LatLng(p.latitude, p.longitude))
            .toList();

        return Scaffold(
          appBar: AppBar(
            title: const Text('Route map'),
            actions: [
              if (config.waypoints.isNotEmpty)
                IconButton(
                  tooltip: 'Clear waypoints',
                  onPressed: () => _update(
                    config.copyWith(waypoints: []),
                  ),
                  icon: const Icon(Icons.clear_all),
                ),
              IconButton(
                tooltip: 'Add waypoint at center',
                onPressed: _addWaypointAtCenter,
                icon: const Icon(Icons.add_location_alt),
              ),
            ],
          ),
          body: GoogleMap(
            initialCameraPosition: CameraPosition(
              target: LatLng(config.start.latitude, config.start.longitude),
              zoom: 10,
            ),
            onMapCreated: (c) => _mapController = c,
            markers: _buildMarkers(config),
            polylines: {
              polyline,
              if (live.length > 1)
                Polyline(
                  polylineId: const PolylineId('live'),
                  points: live,
                  color: Colors.green,
                  width: 3,
                ),
            },
            myLocationEnabled: false,
            myLocationButtonEnabled: false,
          ),
          floatingActionButton: FloatingActionButton.extended(
            onPressed: () => Navigator.pop(context),
            label: const Text('Save route'),
            icon: const Icon(Icons.check),
          ),
        );
      },
    );
  }

  Set<Marker> _buildMarkers(SpoofConfig config) {
    final markers = <Marker>{
      Marker(
        markerId: const MarkerId('start'),
        position: LatLng(config.start.latitude, config.start.longitude),
        draggable: true,
        icon: BitmapDescriptor.defaultMarkerWithHue(BitmapDescriptor.hueGreen),
        infoWindow: const InfoWindow(title: 'Start'),
        onDragEnd: (p) => _update(
          config.copyWith(
            start: model.LatLng(p.latitude, p.longitude),
          ),
        ),
      ),
      Marker(
        markerId: const MarkerId('end'),
        position: LatLng(config.end.latitude, config.end.longitude),
        draggable: true,
        icon: BitmapDescriptor.defaultMarkerWithHue(BitmapDescriptor.hueRed),
        infoWindow: const InfoWindow(title: 'End'),
        onDragEnd: (p) => _update(
          config.copyWith(end: model.LatLng(p.latitude, p.longitude)),
        ),
      ),
    };

    for (var i = 0; i < config.waypoints.length; i++) {
      final w = config.waypoints[i];
      markers.add(
        Marker(
          markerId: MarkerId('wp_$i'),
          position: LatLng(w.latitude, w.longitude),
          draggable: true,
          icon: BitmapDescriptor.defaultMarkerWithHue(BitmapDescriptor.hueAzure),
          infoWindow: InfoWindow(title: 'Waypoint ${i + 1}'),
          onDragEnd: (p) {
            final list = List<model.LatLng>.from(config.waypoints);
            list[i] = model.LatLng(p.latitude, p.longitude);
            _update(config.copyWith(waypoints: list));
          },
        ),
      );
    }

    final cur = widget.controller.currentPosition;
    if (cur != null && widget.controller.isActive) {
      markers.add(
        Marker(
          markerId: const MarkerId('current'),
          position: LatLng(cur.latitude, cur.longitude),
          icon: BitmapDescriptor.defaultMarkerWithHue(BitmapDescriptor.hueOrange),
          infoWindow: const InfoWindow(title: 'Mock position'),
        ),
      );
    }
    return markers;
  }

  Future<void> _addWaypointAtCenter() async {
    final config = widget.controller.config;
    final c = _mapController;
    if (c == null) return;
    final bounds = await c.getVisibleRegion();
    final lat = (bounds.northeast.latitude + bounds.southwest.latitude) / 2;
    final lng = (bounds.northeast.longitude + bounds.southwest.longitude) / 2;
    final waypoints = [...config.waypoints, model.LatLng(lat, lng)];
    _update(config.copyWith(waypoints: waypoints));
  }

  void _update(SpoofConfig config) {
    widget.controller.updateConfig(config);
    setState(() {});
  }
}
