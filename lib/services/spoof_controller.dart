import 'dart:async';
import 'dart:io';

import 'package:flutter/foundation.dart';
import 'package:permission_handler/permission_handler.dart';

import '../models/lat_lng.dart';
import '../models/spoof_config.dart';
import '../utils/motion_metrics.dart';
import 'mock_location_service.dart';
import 'path_simulator.dart';
import 'secure_storage_service.dart';
import 'session_serializer.dart';

class SpoofController extends ChangeNotifier {
  SpoofController({
    MockLocationService? mockLocation,
    SecureStorageService? storage,
  })  : _mock = mockLocation ?? MockLocationService(),
        _storage = storage ?? SecureStorageService();

  final MockLocationService _mock;
  final SecureStorageService _storage;

  SpoofConfig _config = SpoofConfig();
  PathSimulator? _simulator;
  Timer? _timer;
  DateTime? _sessionStart;
  DateTime? _autoResetAt;

  bool _active = false;
  bool _paused = false;
  bool _mockAppReady = false;
  PrivacyStatus _status = PrivacyStatus.idle;
  LatLng? _lastPushed;
  LatLng? _previousPushed;
  String? _statusMessage;
  final List<LatLng> _trajectory = [];
  double _nativeProgress = 0;

  SpoofConfig get config => _config;
  bool get isActive => _active;
  bool get isPaused => _paused;
  bool get mockAppReady => _mockAppReady;
  PrivacyStatus get status => _status;
  LatLng? get currentPosition => _lastPushed ?? _simulator?.currentPosition;
  List<LatLng> get trajectory => List.unmodifiable(_trajectory);
  String? get statusMessage => _statusMessage;
  double get progress =>
      Platform.isAndroid && _active ? _nativeProgress : (_simulator?.progress ?? 0);
  Duration? get remaining {
    if (_sessionStart == null || !_active) return null;
    final total = Duration(minutes: _config.durationMinutes);
    final elapsed = DateTime.now().difference(_sessionStart!);
    final left = total - elapsed;
    return left.isNegative ? Duration.zero : left;
  }

  Future<void> loadPersisted() async {
    final saved = await _storage.loadConfig();
    if (saved != null) _config = saved;
    _mockAppReady = await _mock.isMockLocationEnabled();
    notifyListeners();
    await _recoverFromCrash();
  }

  Future<bool> checkMockReady() async {
    _mockAppReady = await _mock.isMockLocationEnabled();
    notifyListeners();
    return _mockAppReady;
  }

  Future<void> requestPermissions() async {
    await Permission.notification.request();
    await Permission.location.request();
  }

  Future<void> openDeveloperSettings() => _mock.openDeveloperSettings();

  Future<void> openAppSettings() => _mock.openAppSettings();

  Future<List<String>> readAuditLog() => _storage.readAudit();

  Future<void> _recoverFromCrash() async {
    final wasActive = await _storage.wasSessionActive();
    if (!wasActive) return;

    final ready = await _mock.initializeProvider();
    if (!ready) {
      _status = PrivacyStatus.error;
      _statusMessage = 'Recovery failed — set Smart Route as mock location app';
      notifyListeners();
      return;
    }

    if (Platform.isAndroid) {
      await _mock.startForegroundService(
        SessionSerializer.toNativeJson(_config, progress: 0),
      );
    }

    _active = true;
    _status = PrivacyStatus.fallback;
    _statusMessage =
        'Session recovered — safe zone until you tap Stop or Start';
    await _applyLocation(_config.safeZone, recordHistory: false);
    await _storage.appendAudit('Crash/boot recovery');
    notifyListeners();
  }

  Future<void> updateConfig(SpoofConfig config) async {
    _config = config;
    await _storage.saveConfig(config);
    notifyListeners();
  }

  Future<void> start() async {
    if (_active) return;

    await requestPermissions();
    _mockAppReady = await _mock.isMockLocationEnabled();
    if (!_mockAppReady) {
      _status = PrivacyStatus.error;
      _statusMessage =
          'Select Smart Route as mock location app in Developer options.';
      notifyListeners();
      return;
    }

    final ready = await _mock.initializeProvider();
    if (!ready) {
      _status = PrivacyStatus.error;
      _statusMessage = 'Could not register GPS test provider.';
      notifyListeners();
      return;
    }

    _simulator = PathSimulator(_config)..reset();
    _trajectory.clear();
    _previousPushed = null;
    _nativeProgress = 0;
    _sessionStart = DateTime.now();
    _autoResetAt = _config.autoResetMinutes != null
        ? DateTime.now().add(Duration(minutes: _config.autoResetMinutes!))
        : null;
    _active = true;
    _paused = false;
    _status = PrivacyStatus.active;
    _statusMessage =
        'Injecting mock GPS system-wide via Android test provider';

    await _storage.setSessionActive(true);
    await _storage.appendAudit('Session started (${_config.mode.name})');

    if (Platform.isAndroid) {
      await _mock.startForegroundService(
        SessionSerializer.toNativeJson(_config, progress: 0),
      );
      _startTimer(androidNative: true);
    } else {
      await _pushCurrent();
      _startTimer(androidNative: false);
    }
    notifyListeners();
  }

  Future<void> pause() async {
    if (!_active) return;
    _paused = true;
    _statusMessage = 'Paused — coordinates hold at last injection';
    await _storage.appendAudit('Paused');
    notifyListeners();
  }

  Future<void> resume() async {
    if (!_active) return;
    _paused = false;
    _statusMessage = 'Mock GPS resumed';
    await _storage.appendAudit('Resumed');
    notifyListeners();
  }

  Future<void> stop({bool userInitiated = true}) async {
    _timer?.cancel();
    _timer = null;
    _active = false;
    _paused = false;
    _simulator = null;
    _nativeProgress = 0;
    _status = PrivacyStatus.idle;
    _statusMessage = userInitiated ? 'Mock GPS stopped' : 'Session ended';

    if (Platform.isAndroid) {
      await _mock.stopForegroundService();
    }
    await _mock.removeProvider();
    await _storage.setSessionActive(false);
    await _storage.appendAudit('Stopped (user=$userInitiated)');
    notifyListeners();
  }

  void _startTimer({required bool androidNative}) {
    _timer?.cancel();
    final interval = Duration(seconds: _config.updateIntervalSeconds);
    _timer = Timer.periodic(interval, (_) {
      _tick(interval.inMilliseconds / 1000.0, androidNative: androidNative);
    });
  }

  Future<void> _tick(double deltaSeconds, {required bool androidNative}) async {
    if (!_active || _paused) return;

    if (_autoResetAt != null && DateTime.now().isAfter(_autoResetAt!)) {
      await stop(userInitiated: false);
      _statusMessage = 'Auto-reset after configured time window';
      return;
    }

    if (androidNative) {
      await _tickAndroid(deltaSeconds);
    } else {
      await _tickFlutter(deltaSeconds);
    }

    final elapsed = DateTime.now().difference(_sessionStart!);
    if (elapsed.inMinutes >= _config.durationMinutes) {
      await stop(userInitiated: false);
      _statusMessage = 'Duration limit reached';
    }
  }

  Future<void> _tickAndroid(double deltaSeconds) async {
    final status = await _mock.getSessionStatus();
    if (status != null) {
      _updateFromNative(status);
      if (status.done) {
        await stop(userInitiated: false);
        _statusMessage = 'Route complete';
      }
      return;
    }
    final advance = await _mock.advanceSession(deltaSeconds);
    if (advance == null) return;
    _updateFromNative(advance);
    if (!advance.ok) {
      _status = PrivacyStatus.fallback;
      await _applyLocation(_config.safeZone);
    }
    if (advance.done) {
      await stop(userInitiated: false);
      _statusMessage = 'Route complete';
    }
  }

  void _updateFromNative(NativeAdvanceResult r) {
    final pos = LatLng(r.lat, r.lon);
    _previousPushed = _lastPushed;
    _lastPushed = pos;
    _nativeProgress = r.progress;
    _trajectory.add(pos);
    if (_trajectory.length > 5000) _trajectory.removeAt(0);
    notifyListeners();
  }

  Future<void> _tickFlutter(double deltaSeconds) async {
    LatLng next;
    if (_config.mode == SpoofMode.staticLocation) {
      next = _config.start;
    } else {
      final sim = _simulator!;
      next = sim.tick(deltaSeconds);
      if (sim.isComplete) {
        await _applyLocation(_config.end, deltaSeconds: deltaSeconds);
        await stop(userInitiated: false);
        _statusMessage = 'Route complete';
        return;
      }
    }
    await _applyLocation(next, deltaSeconds: deltaSeconds);

    if (_lastPushed != null) {
      final leak = await _mock.detectPossibleLeak(_lastPushed!);
      if (leak) {
        _status = PrivacyStatus.leakWarning;
        _statusMessage = 'GPS divergence — safe zone applied';
        await _applyLocation(_config.safeZone, forceFallback: true);
      } else if (_status == PrivacyStatus.leakWarning) {
        _status = PrivacyStatus.active;
      }
    }
  }

  Future<void> _pushCurrent() async {
    final pos = _config.mode == SpoofMode.staticLocation
        ? _config.start
        : (_simulator?.currentPosition ?? _config.start);
    await _applyLocation(pos);
  }

  Future<void> _applyLocation(
    LatLng pos, {
    bool forceFallback = false,
    double deltaSeconds = 1,
    bool recordHistory = true,
  }) async {
    var target = pos;
    var bearing = 0.0;
    var speed = 0.0;
    if (_previousPushed != null) {
      bearing = MotionMetrics.bearingDegrees(_previousPushed!, target);
      speed = MotionMetrics.speedMps(_previousPushed!, target, deltaSeconds);
    }

    var ok = await _mock.pushLocation(
      target,
      bearing: bearing,
      speedMps: speed,
    );

    if (!ok) {
      _status = PrivacyStatus.fallback;
      target = _config.safeZone;
      ok = await _mock.pushLocation(target);
      _statusMessage = ok
          ? 'Injection failed — safe zone applied'
          : 'Mock injection failed';
      await _storage.appendAudit('Fallback to safe zone');
    } else if (!forceFallback && _status == PrivacyStatus.fallback) {
      _status = PrivacyStatus.active;
    }

    if (ok) {
      _previousPushed = _lastPushed;
      _lastPushed = target;
      if (recordHistory) {
        _trajectory.add(target);
        if (_trajectory.length > 5000) _trajectory.removeAt(0);
      }
    } else {
      _status = PrivacyStatus.error;
    }
    notifyListeners();
  }

  @override
  void dispose() {
    _timer?.cancel();
    super.dispose();
  }
}
