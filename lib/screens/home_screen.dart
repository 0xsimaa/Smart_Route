import 'dart:io';

import 'package:flutter/material.dart';
import 'package:path_provider/path_provider.dart';

import '../models/spoof_config.dart';
import '../services/gpx_exporter.dart';
import '../services/spoof_controller.dart';
import '../widgets/control_panel.dart';
import '../widgets/privacy_status_banner.dart';
import 'map_screen.dart';
import 'settings_screen.dart';
import 'setup_screen.dart';

class HomeScreen extends StatefulWidget {
  const HomeScreen({super.key, required this.controller});

  final SpoofController controller;

  @override
  State<HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends State<HomeScreen> {
  @override
  void initState() {
    super.initState();
    widget.controller.addListener(_rebuild);
  }

  @override
  void dispose() {
    widget.controller.removeListener(_rebuild);
    super.dispose();
  }

  void _rebuild() {
    if (mounted) setState(() {});
  }

  @override
  Widget build(BuildContext context) {
    final c = widget.controller;
    final pos = c.currentPosition;

    return Scaffold(
      appBar: AppBar(
        title: const Text('Smart Route v2'),
        actions: [
          IconButton(
            tooltip: 'Device setup',
            onPressed: () => Navigator.push(
              context,
              MaterialPageRoute(
                builder: (_) => SetupScreen(controller: c),
              ),
            ),
            icon: Icon(
              c.mockAppReady ? Icons.verified : Icons.settings,
              color: c.mockAppReady ? Colors.green : null,
            ),
          ),
          IconButton(
            tooltip: 'Audit log',
            onPressed: _showAudit,
            icon: const Icon(Icons.history),
          ),
        ],
      ),
      body: Column(
        children: [
          PrivacyStatusBanner(status: c.status, message: c.statusMessage),
          Expanded(
            child: ListView(
              padding: const EdgeInsets.only(bottom: 16),
              children: [
                if (Platform.isAndroid && !c.mockAppReady)
                  Padding(
                    padding: const EdgeInsets.all(12),
                    child: Card(
                      color: Colors.orange.shade50,
                      child: ListTile(
                        leading: const Icon(Icons.warning_amber),
                        title: const Text('Mock location not configured'),
                        subtitle: const Text(
                          'Complete setup so other apps receive injected GPS.',
                        ),
                        trailing: FilledButton(
                          onPressed: () => Navigator.push(
                            context,
                            MaterialPageRoute(
                              builder: (_) => SetupScreen(controller: c),
                            ),
                          ),
                          child: const Text('Setup'),
                        ),
                      ),
                    ),
                  ),
                if (!Platform.isAndroid)
                  const Padding(
                    padding: EdgeInsets.all(12),
                    child: Card(
                      child: ListTile(
                        leading: Icon(Icons.info_outline),
                        title: Text('Android required for mock GPS'),
                        subtitle: Text(
                          'Map planning and GPX export work on all platforms. '
                          'System mock location uses Android test providers.',
                        ),
                      ),
                    ),
                  ),
                Padding(
                  padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
                  child: Card(
                    child: ListTile(
                      leading: const Icon(Icons.my_location),
                      title: const Text('Injected GPS (system test provider)'),
                      subtitle: Text(
                        pos == null
                            ? 'Not broadcasting'
                            : '${pos.latitude.toStringAsFixed(5)}, ${pos.longitude.toStringAsFixed(5)}',
                      ),
                    ),
                  ),
                ),
                ControlPanel(
                  controller: c,
                  onOpenMap: () => Navigator.push(
                    context,
                    MaterialPageRoute(
                      builder: (_) => MapScreen(controller: c),
                    ),
                  ),
                  onOpenSettings: () => Navigator.push(
                    context,
                    MaterialPageRoute(
                      builder: (_) => SettingsScreen(controller: c),
                    ),
                  ),
                  onExportGpx: _exportGpx,
                ),
                Padding(
                  padding: const EdgeInsets.symmetric(horizontal: 16),
                  child: Text(
                    'Default test route: Islamabad → Abbottabad\n'
                    'Enable Settings → Developer options → Select mock location app → Smart Route',
                    style: Theme.of(context).textTheme.bodySmall,
                    textAlign: TextAlign.center,
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }

  Future<void> _exportGpx() async {
    final points = widget.controller.trajectory;
    if (points.isEmpty) return;

    final gpx = GpxExporter.build(
      trackName: 'smart_route_${DateTime.now().millisecondsSinceEpoch}',
      points: points,
    );
    final dir = await getApplicationDocumentsDirectory();
    final file = File(
      '${dir.path}/smart_route_${DateTime.now().millisecondsSinceEpoch}.gpx',
    );
    await file.writeAsString(gpx);

    if (!mounted) return;
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(content: Text('GPX saved: ${file.path}')),
    );
  }

  Future<void> _showAudit() async {
    final logs = await widget.controller.readAuditLog();
    if (!mounted) return;
    showDialog(
      context: context,
      builder: (_) => AlertDialog(
        title: const Text('Session audit'),
        content: SizedBox(
          width: 320,
          height: 280,
          child: logs.isEmpty
              ? const Text('No audit entries yet.')
              : ListView.builder(
                  itemCount: logs.length,
                  itemBuilder: (_, i) => Padding(
                    padding: const EdgeInsets.only(bottom: 6),
                    child: Text(logs[logs.length - 1 - i], style: const TextStyle(fontSize: 12)),
                  ),
                ),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context),
            child: const Text('Close'),
          ),
        ],
      ),
    );
  }
}
