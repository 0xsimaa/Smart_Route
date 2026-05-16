import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import 'app.dart';
import 'services/spoof_controller.dart';

void main() async {
  WidgetsFlutterBinding.ensureInitialized();
  await SystemChrome.setPreferredOrientations([
    DeviceOrientation.portraitUp,
    DeviceOrientation.portraitDown,
  ]);

  final controller = SpoofController();
  await controller.loadPersisted();
  await controller.requestPermissions();

  runApp(SmartRouteApp(controller: controller));
}
