import 'dart:convert';

import 'package:flutter_secure_storage/flutter_secure_storage.dart';

import '../models/spoof_config.dart';

/// Persists route configuration using encrypted platform storage.
class SecureStorageService {
  SecureStorageService() : _storage = const FlutterSecureStorage();

  static const _configKey = 'spoof_config_v2';
  static const _auditKey = 'audit_log_v2';
  static const _sessionActiveKey = 'session_active_v2';

  final FlutterSecureStorage _storage;

  Future<void> saveConfig(SpoofConfig config) async {
    await _storage.write(key: _configKey, value: jsonEncode(config.toJson()));
  }

  Future<SpoofConfig?> loadConfig() async {
    final raw = await _storage.read(key: _configKey);
    if (raw == null) return null;
    return SpoofConfig.fromJson(jsonDecode(raw) as Map<String, dynamic>);
  }

  Future<void> appendAudit(String message) async {
    final existing = await _storage.read(key: _auditKey);
    final list = existing == null
        ? <String>[]
        : List<String>.from(jsonDecode(existing) as List<dynamic>);
    final entry =
        '${DateTime.now().toIso8601String()} | $message';
    list.add(entry);
    while (list.length > 200) {
      list.removeAt(0);
    }
    await _storage.write(key: _auditKey, value: jsonEncode(list));
  }

  Future<List<String>> readAudit() async {
    final raw = await _storage.read(key: _auditKey);
    if (raw == null) return [];
    return List<String>.from(jsonDecode(raw) as List<dynamic>);
  }

  Future<void> setSessionActive(bool active) async {
    await _storage.write(key: _sessionActiveKey, value: active.toString());
  }

  Future<bool> wasSessionActive() async {
    final v = await _storage.read(key: _sessionActiveKey);
    return v == 'true';
  }
}
