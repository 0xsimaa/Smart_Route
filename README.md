# Smart Route

[![License: MIT](https://img.shields.io/badge/License-MIT-green.svg)](LICENSE)
[![Platform](https://img.shields.io/badge/Platform-Android%20%E2%89%A5%2024-blue.svg)](https://developer.android.com)
[![Language](https://img.shields.io/badge/Language-Java-orange.svg)](https://docs.oracle.com/en/java/)

**Smart Route** is a native Android application (Java, Material 3) that injects
**real mock GPS coordinates** through the operating system's
[test location provider](https://developer.android.com/develop/sensors-sensors-location/testing).
Any app that reads device location — maps, fitness trackers, ride-share clients,
or your own QA build — sees the injected position as if it came from the GPS
hardware.

This is **not** a map-only animation. Coordinates are written to
`LocationManager` and propagate system-wide while a session is active.

> **Authorized use only.** Run Smart Route on hardware you own or on systems
> covered by written permission. See [SECURITY.md](SECURITY.md).

---

## What you get

| Area | Details |
|------|---------|
| **Injection** | Custom provider `smart_route_gps` with latitude, longitude, accuracy, bearing, speed, and timestamps |
| **Background** | Foreground service (`location` type) with live progress, coordinates, and a **Stop** action in the notification |
| **Route planner** | Google Maps tiles, **place search** (Places Autocomplete), **road-following routes** (Directions API), turn-by-turn steps, draggable start/end/waypoint markers, **My Location** blue dot |
| **Motion** | Speed range, smooth / constant / sudden acceleration, road polyline or straight/curved paths, multi-waypoint routes, optional pauses |
| **Safety** | Safe-zone fallback, leak detection (real GPS vs. injected), encrypted audit log |
| **Export** | GPX 1.1 export and share via `FileProvider` |
| **UI** | Dashboard with status pill, route summary, live metric tiles, route planner map, settings, setup wizard |

On first launch the route is a neutral placeholder; you **must** open the map
and set start / end (and optional waypoints) before starting a session.

---

## Requirements

| Tool | Version |
|------|---------|
| Android Studio | Iguana or newer (AGP 8.7+) |
| JDK | 17 |
| Android SDK | API 34 (`compileSdk`); device **API 24+** |
| Google Cloud APIs | One API key with **Maps SDK for Android**, **Places API**, and **Directions API** enabled |
| Device | **Physical phone strongly recommended** — emulators often do not propagate mock locations to other apps reliably |

---

## 1. Download dependencies

From the project root (Linux / macOS):

```bash
chmod +x scripts/download-deps.sh
./scripts/download-deps.sh
```

Or manually:

```bash
# Use JDK 17 if your default Java is newer and Gradle complains
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64   # adjust path on your OS

./gradlew --refresh-dependencies :app:assembleDebug :app:assembleRelease :app:testDebugUnitTest
```

Android Studio performs the same download on **File → Sync Project with Gradle Files**.

**Outputs after a successful build:**

| Artifact | Path |
|----------|------|
| Debug APK | `app/build/outputs/apk/debug/app-debug.apk` |
| Release APK | `app/build/outputs/apk/release/app-release.apk` |

---

## 2. Configure the Google API key

```bash
cp secrets.properties.example secrets.properties
```

Edit `secrets.properties`:

```properties
MAPS_API_KEY=your_key_here
```

In [Google Cloud Console](https://console.cloud.google.com/):

1. Create or select a project.
2. Enable all three APIs under **APIs & Services → Library**:
   - [Maps SDK for Android](https://developers.google.com/maps/documentation/android-sdk/get-api-key)
   - [Places API](https://developers.google.com/maps/documentation/places/web-service/overview) (map search)
   - [Directions API](https://developers.google.com/maps/documentation/directions/overview) (road-following routes)
3. Create an API key under **Credentials**.
4. Restrict the key to Android apps with package name `com.cybersec.smartroute` and your SHA-1 fingerprint.

Obtain your debug SHA-1:

```bash
./gradlew signingReport
```

Rebuild after changing the key (`Build → Rebuild Project` or `./gradlew :app:assembleDebug`).

`secrets.properties` is git-ignored.

---

## 3. Open in Android Studio

1. **File → Open** → select this repository root (the folder containing `settings.gradle`).
2. Wait for Gradle sync to finish.
3. If prompted, accept SDK licenses and install **Android SDK Platform 34**.
4. Set **JDK 17** under *Settings → Build, Execution, Deployment → Build Tools → Gradle*.

Create `local.properties` automatically if missing (Studio writes `sdk.dir=…`), or:

```properties
sdk.dir=/path/to/Android/Sdk
```

---

## 4. Run on a device

### One-time device setup

1. **Developer options** — Settings → About phone → tap **Build number** seven times.
2. **Mock location app** — Developer options → **Select mock location app** → **Smart Route**.
3. **USB debugging** — enable in Developer options; connect the phone via USB.
4. In the app: open **Setup** (toolbar) and complete all four checklist steps (permissions + Maps key).

### Install and launch

**From Android Studio:** select your device → **Run** (▶) or `Shift+F10`.

**From the command line:**

```bash
./gradlew :app:installDebug
adb shell am start -n com.cybersec.smartroute/.ui.MainActivity
```

---

## 5. Test that it works

Use this checklist for a demo or lab report:

| Step | Action | Expected result |
|------|--------|-----------------|
| 1 | Open **Edit on map** — search for a place, drag green (start) and red (end) markers, wait for the blue road route and directions card | Road polyline and turn-by-turn steps appear; **My Location** shows your real position while editing |
| 2 | Tap **Save route** | Dashboard route summary updates with coordinates and distance (km) |
| 3 | Tap **Start** (extended FAB) | Status pill turns green; metric tiles show changing coordinates, speed, bearing; Pause / Stop / Export / Share enable |
| 4 | Open **Google Maps** (or another GPS app) | The blue dot moves along the road route |
| 5 | Press **Home** | Persistent notification shows progress % and coordinates; injection continues |
| 6 | Tap **Stop** in the app or **Stop** on the notification | Injection ends; notification disappears |
| 7 | **Export GPX** / **Share GPX** | File saved under app storage and share sheet opens |

Optional: **Settings** → adjust speed, interval, curved path, safe zone → start again.

Full walkthrough: [USAGE.md](USAGE.md).

**Unit tests (no device):**

```bash
./gradlew :app:testDebugUnitTest
```

---

## Project layout

```
app/src/main/java/com/cybersec/smartroute/
  engine/     MockLocationEngine, NativeRouteSimulator, SessionAdvancer
  service/    SpoofController, MockLocationForegroundService, BootRecoveryReceiver
  storage/    SecureStorage, MockLocationSessionStore
  model/      SpoofConfig, LatLng, enums
  util/       DirectionsClient, GpxExporter, LeakDetector, MotionMetrics
  ui/         MainActivity, MapActivity, SettingsActivity, SetupActivity, SafeZonePickerActivity
app/src/test/ RouteSimulatorTest (JVM)
.github/      android_ci.yml
scripts/      download-deps.sh
USAGE.md      Step-by-step testing guide
SECURITY.md   Responsible-use policy
```

---

## Permissions (summary)

`ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION`, `ACCESS_MOCK_LOCATION`,
`FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_LOCATION`, `POST_NOTIFICATIONS`,
`RECEIVE_BOOT_COMPLETED`, `INTERNET` (map tiles, Places search, Directions API).

No analytics SDKs.

---

## Troubleshooting

| Symptom | Fix |
|---------|-----|
| Map is blank / gray | Add `MAPS_API_KEY` to `secrets.properties`, enable **Maps SDK for Android**, rebuild |
| Search returns no results | Enable **Places API** in Google Cloud Console for the same project/key |
| No road route / straight line only | Enable **Directions API**; check device has internet when editing the route |
| Route not saved after map edit | Tap **Save route** before leaving; confirm coordinates update on the dashboard |
| Metrics frozen / buttons disabled | Rebuild with latest code; confirm mock app is selected and tap **Start** again |
| "Mock location not configured" | Select Smart Route as mock app in Developer options |
| Other apps don't move | Use a **physical device**; confirm mock app selection and tap **Start** |
| Gradle: `JAVA_COMPILER` error | Point `JAVA_HOME` to JDK **17**, not a JRE-only install |
| Injection stops in background | Disable aggressive battery optimization for Smart Route |

---

## License

[MIT](LICENSE)
