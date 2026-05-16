import 'package:flutter/material.dart';

import '../models/spoof_config.dart';

class PrivacyStatusBanner extends StatelessWidget {
  const PrivacyStatusBanner({
    super.key,
    required this.status,
    this.message,
  });

  final PrivacyStatus status;
  final String? message;

  @override
  Widget build(BuildContext context) {
    final (color, icon, label) = switch (status) {
      PrivacyStatus.idle => (
          Colors.blueGrey,
          Icons.location_disabled,
          'Idle — mock location off',
        ),
      PrivacyStatus.active => (
          Colors.green,
          Icons.verified_user,
          'Mock active — test provider engaged',
        ),
      PrivacyStatus.fallback => (
          Colors.orange,
          Icons.shield,
          'Safe zone fallback',
        ),
      PrivacyStatus.leakWarning => (
          Colors.red,
          Icons.warning_amber,
          'Divergence warning',
        ),
      PrivacyStatus.error => (
          Colors.red.shade900,
          Icons.error,
          'Error',
        ),
    };

    return Material(
      elevation: 2,
      color: color.withValues(alpha: 0.15),
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
        child: Row(
          children: [
            Icon(icon, color: color),
            const SizedBox(width: 10),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    label,
                    style: TextStyle(
                      fontWeight: FontWeight.w600,
                      color: color,
                    ),
                  ),
                  if (message != null)
                    Text(
                      message!,
                      style: Theme.of(context).textTheme.bodySmall,
                    ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }
}
