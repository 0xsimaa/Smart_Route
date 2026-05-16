import 'dart:io';

import 'package:flutter/material.dart';

import '../services/spoof_controller.dart';

/// One-time device checklist so mock GPS works on a real phone.
class SetupScreen extends StatefulWidget {
  const SetupScreen({super.key, required this.controller});

  final SpoofController controller;

  @override
  State<SetupScreen> createState() => _SetupScreenState();
}

class _SetupScreenState extends State<SetupScreen> {
  bool _checking = false;
  bool _mockReady = false;

  @override
  void initState() {
    super.initState();
    _refresh();
  }

  Future<void> _refresh() async {
    setState(() => _checking = true);
    await widget.controller.requestPermissions();
    _mockReady = await widget.controller.checkMockReady();
    setState(() => _checking = false);
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Device setup')),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          Card(
            child: Padding(
              padding: const EdgeInsets.all(16),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    'Real mock GPS on Android',
                    style: Theme.of(context).textTheme.titleLarge,
                  ),
                  const SizedBox(height: 8),
                  const Text(
                    'Smart Route injects coordinates through Android\'s '
                    'test location provider. Other apps (Maps, messaging test '
                    'builds, etc.) read these as your device position when '
                    'mock mode is configured correctly.',
                  ),
                ],
              ),
            ),
          ),
          const SizedBox(height: 16),
          _StepTile(
            number: 1,
            title: 'Enable Developer options',
            subtitle: 'Settings → About phone → tap Build number 7 times',
            done: true,
          ),
          _StepTile(
            number: 2,
            title: 'Select mock location app',
            subtitle: 'Developer options → Select mock location app → Smart Route',
            done: _mockReady,
            trailing: FilledButton(
              onPressed: widget.controller.openDeveloperSettings,
              child: const Text('Open'),
            ),
          ),
          _StepTile(
            number: 3,
            title: 'Grant location & notifications',
            subtitle: 'Required for foreground service and leak detection',
            done: !_checking,
            trailing: FilledButton.tonal(
              onPressed: () async {
                await widget.controller.requestPermissions();
                await _refresh();
              },
              child: const Text('Grant'),
            ),
          ),
          if (Platform.isAndroid) ...[
            _StepTile(
              number: 4,
              title: 'Google Maps API key',
              subtitle: 'android/app/src/main/res/values/strings.xml',
              done: true,
            ),
          ],
          const SizedBox(height: 24),
          if (_checking)
            const Center(child: CircularProgressIndicator())
          else
            ListTile(
              leading: Icon(
                _mockReady ? Icons.check_circle : Icons.error,
                color: _mockReady ? Colors.green : Colors.red,
              ),
              title: Text(_mockReady ? 'Ready for mock GPS' : 'Mock app not selected'),
            ),
          const SizedBox(height: 12),
          FilledButton.icon(
            onPressed: _refresh,
            icon: const Icon(Icons.refresh),
            label: const Text('Re-check status'),
          ),
          if (_mockReady)
            Padding(
              padding: const EdgeInsets.only(top: 12),
              child: FilledButton(
                onPressed: () => Navigator.pop(context, true),
                child: const Text('Continue to app'),
              ),
            ),
        ],
      ),
    );
  }
}

class _StepTile extends StatelessWidget {
  const _StepTile({
    required this.number,
    required this.title,
    required this.subtitle,
    required this.done,
    this.trailing,
  });

  final int number;
  final String title;
  final String subtitle;
  final bool done;
  final Widget? trailing;

  @override
  Widget build(BuildContext context) {
    return Card(
      margin: const EdgeInsets.only(bottom: 8),
      child: ListTile(
        leading: CircleAvatar(
          backgroundColor: done ? Colors.green : Colors.grey,
          child: Text('$number', style: const TextStyle(color: Colors.white)),
        ),
        title: Text(title),
        subtitle: Text(subtitle),
        trailing: trailing,
      ),
    );
  }
}
