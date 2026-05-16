import 'package:flutter/material.dart';

import 'screens/home_screen.dart';
import 'services/spoof_controller.dart';

class SmartRouteApp extends StatelessWidget {
  const SmartRouteApp({super.key, required this.controller});

  final SpoofController controller;

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Smart Route v2',
      debugShowCheckedModeBanner: false,
      theme: ThemeData(
        colorScheme: ColorScheme.fromSeed(
          seedColor: const Color(0xFF1B5E20),
          brightness: Brightness.light,
        ),
        useMaterial3: true,
        appBarTheme: const AppBarTheme(centerTitle: true),
      ),
      darkTheme: ThemeData(
        colorScheme: ColorScheme.fromSeed(
          seedColor: const Color(0xFF81C784),
          brightness: Brightness.dark,
        ),
        useMaterial3: true,
      ),
      home: HomeScreen(controller: controller),
    );
  }
}
