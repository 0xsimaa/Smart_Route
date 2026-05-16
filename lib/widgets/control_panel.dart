import 'package:flutter/material.dart';

import '../models/spoof_config.dart';
import '../services/spoof_controller.dart';

class ControlPanel extends StatelessWidget {
  const ControlPanel({
    super.key,
    required this.controller,
    required this.onOpenMap,
    required this.onOpenSettings,
    required this.onExportGpx,
  });

  final SpoofController controller;
  final VoidCallback onOpenMap;
  final VoidCallback onOpenSettings;
  final VoidCallback onExportGpx;

  @override
  Widget build(BuildContext context) {
    final config = controller.config;
    final progress = controller.progress;

    return Card(
      margin: const EdgeInsets.all(12),
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Text(
              'Route controls',
              style: Theme.of(context).textTheme.titleMedium,
            ),
            const SizedBox(height: 8),
            SegmentedButton<SpoofMode>(
              segments: const [
                ButtonSegment(
                  value: SpoofMode.staticLocation,
                  label: Text('Static'),
                  icon: Icon(Icons.place),
                ),
                ButtonSegment(
                  value: SpoofMode.dynamicPath,
                  label: Text('Dynamic'),
                  icon: Icon(Icons.route),
                ),
              ],
              selected: {config.mode},
              onSelectionChanged: controller.isActive
                  ? null
                  : (s) {
                      controller.updateConfig(config.copyWith(mode: s.first));
                    },
            ),
            const SizedBox(height: 12),
            if (config.mode == SpoofMode.dynamicPath) ...[
              LinearProgressIndicator(value: controller.isActive ? progress : 0),
              const SizedBox(height: 4),
              Text(
                controller.isActive
                    ? 'Progress ${(progress * 100).toStringAsFixed(1)}%'
                    : 'Duration: ${config.durationMinutes} min',
                style: Theme.of(context).textTheme.bodySmall,
              ),
              if (controller.remaining != null)
                Text(
                  'Remaining: ${controller.remaining!.inMinutes}m ${controller.remaining!.inSeconds % 60}s',
                  style: Theme.of(context).textTheme.bodySmall,
                ),
            ],
            const SizedBox(height: 12),
            Wrap(
              spacing: 8,
              runSpacing: 8,
              alignment: WrapAlignment.center,
              children: [
                FilledButton.icon(
                  onPressed: controller.isActive
                      ? null
                      : () => controller.start(),
                  icon: const Icon(Icons.play_arrow),
                  label: const Text('Start'),
                ),
                FilledButton.tonalIcon(
                  onPressed: !controller.isActive
                      ? null
                      : () => controller.isPaused
                          ? controller.resume()
                          : controller.pause(),
                  icon: Icon(controller.isPaused ? Icons.play_arrow : Icons.pause),
                  label: Text(controller.isPaused ? 'Resume' : 'Pause'),
                ),
                FilledButton.tonalIcon(
                  onPressed: !controller.isActive ? null : () => controller.stop(),
                  icon: const Icon(Icons.stop),
                  label: const Text('Stop'),
                  style: FilledButton.styleFrom(
                    backgroundColor: Colors.red.shade100,
                  ),
                ),
              ],
            ),
            const SizedBox(height: 8),
            Row(
              children: [
                Expanded(
                  child: OutlinedButton.icon(
                    onPressed: onOpenMap,
                    icon: const Icon(Icons.map),
                    label: const Text('Map'),
                  ),
                ),
                const SizedBox(width: 8),
                Expanded(
                  child: OutlinedButton.icon(
                    onPressed: onOpenSettings,
                    icon: const Icon(Icons.tune),
                    label: const Text('Settings'),
                  ),
                ),
              ],
            ),
            TextButton.icon(
              onPressed: controller.trajectory.isEmpty ? null : onExportGpx,
              icon: const Icon(Icons.download),
              label: const Text('Export GPX'),
            ),
          ],
        ),
      ),
    );
  }
}
