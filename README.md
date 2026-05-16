# Smart Route — Dynamic Mock GPS for Security Research & App QA

[![License: MIT](https://img.shields.io/badge/License-MIT-green.svg)](LICENSE)
[![Platform](https://img.shields.io/badge/Platform-Android-blue.svg)](https://developer.android.com)

**Smart Route** is an open-source Flutter/Android tool that injects **real mock GPS coordinates** into the Android location stack using the official [test location provider](https://developer.android.com/develop/sensors-sensors-location/testing) API—not a map-only simulation.

Use it for **authorized** work: penetration testing (with written scope), mobile app QA, privacy research, and university cybersecurity labs.

## What makes it a real tool

| Capability | How it works |
|------------|--------------|
| **System-level GPS injection** | `LocationManager` test provider (`smart_route_gps`) |
| **Background persistence** | Foreground service keeps injecting when the app is minimized |
| **Crash / reboot recovery** | Session state on disk; boot receiver restarts safe-zone hold |
| **Realistic motion** | Speed, bearing, curved paths, pauses; fused-location-friendly updates |
| **Privacy safeguards** | Safe-zone fallback, optional leak detection, encrypted audit log |
| **GPX export** | Document routes for reports and coursework evidence |

## Screenshots / flow

1. **Setup** — Developer options → mock location app → Smart Route  
2. **Map** — Drag start/end, add waypoints  
3. **Start** — Other apps reading GPS see movement along your route  
4. **Notification** — Ongoing foreground service while active  

## Quick start

### Prerequisites

- Flutter 3.2+
- Android device or emulator (API 21+; **physical device recommended** for mock location)
- [Google Maps API key](https://developers.google.com/maps/documentation/android-sdk/get-api-key)

### Build & run

```bash
git clone https://github.com/YOUR_USERNAME/smart-route.git
cd smart-route
flutter pub get
```

1. Edit `android/app/src/main/res/values/strings.xml` — set `google_maps_api_key`  
2. On device: **Developer options → Select mock location app → Smart Route**  
3. Run:

```bash
flutter run --release
```

Open **Setup** from the app bar and complete the checklist.

## Verify it works

1. Start a dynamic route (default: Islamabad → Abbottabad).  
2. Open **Google Maps** or a test app that displays GPS.  
3. Confirm coordinates update every 1–5 seconds along the path.  
4. Press Home — injection should continue (notification visible).  
5. Export **GPX** after the session for your lab report.

## Project structure

```
lib/
  services/     spoof_controller, mock_location, path_simulator, session_serializer
  screens/      home, map, settings, setup
android/.../
  MockLocationEngine.kt           # Test provider injection
  MockLocationForegroundService.kt
  NativeRouteSimulator.kt
  BootRecoveryReceiver.kt
docs/USE_CASES.md
```

## Responsible use

Read [SECURITY.md](SECURITY.md) and [docs/USE_CASES.md](docs/USE_CASES.md).

- Only test devices and apps you own or have **written permission** to assess.  
- Mock location may be detectable (`isFromMockProvider`) — document this in security reports.  
- Do not use to deceive individuals, evade law enforcement, or harass others.

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md).

## License

MIT — [LICENSE](LICENSE)

## Citation (academic)

```bibtex
@software{smart_route2026,
  title = {Smart Route: Dynamic Mock GPS for Android Security Research},
  year = {2026},
  license = {MIT}
}
```
