# Smart Route — Native Android (Java) Mock GPS

[![License: MIT](https://img.shields.io/badge/License-MIT-green.svg)](LICENSE)
[![Platform](https://img.shields.io/badge/Platform-Android%20%E2%89%A5%2024-blue.svg)](https://developer.android.com)

**Smart Route** is a native Android (Java) tool that injects **real mock GPS
coordinates** into the Android location stack via the official
[test location provider](https://developer.android.com/develop/sensors-sensors-location/testing)
API. Other apps reading GPS receive these coordinates as if they came from
the device's real GPS chip — this is **not** a map-only simulation.

Use only for **authorized** work: penetration testing under written scope,
mobile-app QA, privacy research, and university cybersecurity labs.

## What's real about it

| Capability | How it works |
|------------|--------------|
| System-level GPS injection | `LocationManager.addTestProvider("smart_route_gps")` + `setTestProviderLocation` |
| Background persistence | Foreground `Service` (`location` type) keeps injecting when the activity is closed |
| Crash / reboot recovery | Session blob stored in `SharedPreferences`; `BOOT_COMPLETED` receiver restarts safe-zone hold |
| Realistic motion | Speed range, smooth/constant/sudden acceleration, curved Bézier paths, optional pauses |
| Privacy safeguards | Safe-zone fallback, FusedLocationProvider leak detection, encrypted audit log (`EncryptedSharedPreferences`) |
| GPX export | Persisted to app-scoped external storage |

## Build (Android Studio)

### Requirements

- Android Studio Iguana (or newer) with AGP 8.7+
- JDK 17
- Android device or emulator (API 24+; **physical device recommended**)
- Google Maps API key — [get one](https://developers.google.com/maps/documentation/android-sdk/get-api-key)

### Steps

1. `cp secrets.properties.example secrets.properties` and paste your Maps key.
2. Open the project root in Android Studio (`File → Open` → this directory).
3. Sync Gradle. Run on a real device.
4. On the device:
   - Settings → About phone → tap **Build number** 7× to enable Developer options.
   - Developer options → **Select mock location app → Smart Route**.
   - Open the app → Setup → grant location & notification permissions.
5. Pick a route on the **Map** screen, hit **Start**, and watch any other
   GPS app (Google Maps, etc.) follow Smart Route's coordinates.

## Project structure

```
app/
  src/main/
    AndroidManifest.xml
    java/com/cybersec/smartroute/
      engine/        # MockLocationEngine, NativeRouteSimulator, SessionAdvancer, MockLocationPermissions
      service/       # MockLocationForegroundService, BootRecoveryReceiver, SpoofController
      storage/       # SecureStorage (EncryptedSharedPreferences), MockLocationSessionStore
      model/         # SpoofConfig, LatLng, enums
      util/          # LeakDetector, MotionMetrics, GpxExporter
      ui/            # MainActivity, MapActivity, SettingsActivity, SetupActivity, SafeZonePickerActivity
    res/             # Material 3 layouts, themes, strings, drawables
```

## Verify it works

1. Open the **Map** screen, drag start/end markers (default: Islamabad → Abbottabad).
2. Tap **Start** in the home screen.
3. Open Google Maps or any GPS-using app — coordinates update every
   `updateIntervalSeconds` (default 2s).
4. Press Home — the foreground notification stays; injection continues.
5. Hit **Export GPX** to save the trail under
   `Android/data/com.cybersec.smartroute/files/Documents/`.

## Responsible use

Read [SECURITY.md](SECURITY.md).

- Only test devices and apps you own or have **written permission** to assess.
- Mock location is detectable (`Location#isMock()` / `isFromMockProvider()`) —
  document this in security reports.
- Do not use to deceive individuals, evade law enforcement, or harass others.

## License

MIT — see [LICENSE](LICENSE).
