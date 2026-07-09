# Smart Route — Usage Guide

A minimal, opinionated test plan that takes you from a freshly-built APK to
verified system-wide GPS injection.

---

## 0. Prerequisites

Before you do anything else, make sure you have:

- A physical Android device on API 24 (Nougat) or newer.
  *Stock emulator images often expose a non-functioning mock-location provider
  that compiles fine but doesn't actually propagate fixes to other apps.
  Use a real device for any meaningful test.*
- The device's USB-debugging mode enabled (Settings → Developer options).
- A Google Cloud API key in `secrets.properties` with **Maps SDK for Android**, **Places API**, and **Directions API** enabled (see the project [README](README.md)).
- Android Studio with the project opened, OR the debug APK installed:
  ```bash
  ./gradlew :app:installDebug
  ```

---

## 1. One-time device setup

### 1.1 Enable Developer options

1. Settings → About phone.
2. Tap **Build number** seven times.
3. You'll see "*You are now a developer*".

### 1.2 Select Smart Route as the mock-location app

1. Settings → System → Developer options.
2. Scroll to **Select mock location app**.
3. Pick **Smart Route**.

### 1.3 Grant runtime permissions

The first time you launch the app, it prompts for:

- **Location** (fine + coarse) — required for the foreground service type and for the leak-detector fallback.
- **Notifications** (Android 13 +) — required for the live progress notification.

If you missed any of those prompts, the **Setup** screen has a "Grant" button
that re-asks.

The Setup checklist's status banner will turn green and read **"Ready for
mock GPS"** once everything is wired up.

---

## 2. Plan a route

1. From the dashboard tap **Edit on map** (or press **Start** with the placeholder route — it will pop a "pick a route first" dialog with an *Open map* button).
2. The map opens centred on your **real location** (blue dot) when location permission is granted.
3. **Search** for a place using the search bar or toolbar search icon (Places Autocomplete).
4. Drag the **green** marker to your starting point and the **red** marker to your endpoint.
5. Wait for the **blue road polyline** and **turn-by-turn directions** card to appear (requires internet + Directions API enabled).
6. (Optional) Long-press anywhere on the map to drop a waypoint, or use the toolbar's "Add waypoint" button. Use "Clear waypoints" to reset.
7. Tap the floating **Save route** button. You're returned to the dashboard.

The route summary card now shows your start coordinates, end coordinates, waypoint count, and total route distance (km).

---

## 3. Tweak motion settings (optional)

Open **Settings**:

| Setting | Effect |
|---|---|
| **Speed range** | Lower / upper bounds (km/h) the simulator will lerp between |
| **Update interval** | How often a new GPS fix is pushed (1 – 5 s) |
| **Journey duration** | Hard cap; the session auto-stops after this |
| **Natural pauses** | Random short stops along the route (more lifelike) |
| **Path shape** | Straight segments vs. quadratic Bézier curves between waypoints |
| **Acceleration** | `smooth` (exponential), `constant`, or `sudden` (step) |
| **Auto-reset window** | Optional secondary cap. `Off` to disable |
| **Safe-zone fallback** | Coordinate that the app falls back to whenever a leak is detected or injection fails |

Tap **Apply settings** to save.

---

## 4. Start a session

1. Confirm Smart Route is the selected mock-location app (the orange "not configured" card should be gone).
2. Tap the bottom-right **Start** FAB.
3. The status pill turns green ("Active") and the live KPI tiles begin updating every interval.

### What you should observe

- **Coordinates** tile updates each tick.
- **Ground speed** tile shows the current simulated km/h.
- **Bearing** tile shows the heading in degrees.
- **Distance traveled** tile increases monotonically.
- The **progress bar** climbs toward 100% over the session duration.
- The **ongoing notification** appears with the live coordinates, a progress bar, and a **Stop** action button.

### Verify another app receives the mocked GPS

This is the actual test:

1. Without stopping the session, open **Google Maps**.
2. Press the locate-me button. The blue dot should jump to (and follow) the route you planned in Smart Route.
3. Or open any GPS-displaying app (a basic GPS info viewer like *GPS Test* is excellent for this) — every tick you should see Smart Route's coordinates, accuracy, bearing, and speed appear there.

If the blue dot doesn't move, see **Troubleshooting** below.

---

## 5. Backgrounding & lifecycle

- **Press Home** while a session is running. The notification persists, the foreground service keeps injecting, and other apps still see the moving fix.
- **Tap the notification's Stop button** — the session ends cleanly, the test provider is removed, and the audit log records `Stopped (user=true)`.
- **Reboot the device** while a session was running. After unlock, Smart Route's boot receiver re-attaches and switches injection to the safe-zone fallback (defensive default) until you decide to resume or stop. The audit log records `Crash/boot recovery`.

---

## 6. Privacy / leak detection

While a session is active, every tick the app asks the FusedLocationProvider
for the device's actual GPS fix and compares it against the most recently
injected coordinate.

- Distance ≤ 150 m → no action.
- Distance &gt; 150 m → status pill turns red, status text reads "Leak detected", the next injection is forced to your safe-zone, and an audit-log entry is added: `Leak detected — fallback`.

You can change the safe-zone in Settings → **Safe-zone fallback** (tap to open
a map picker).

---

## 7. Export & share the trail

After (or during) a session:

- **Export GPX** writes a `smart_route_<epoch>.gpx` file to
  `Android/data/com.cybersec.smartroute/files/Documents/`. The dashboard shows a snackbar with the absolute path.
- **Share GPX** opens the standard Android share sheet with the same file attached as `application/gpx+xml`. Drop it into Drive, Slack, an email, etc.

Both files are valid GPX 1.1 — they import cleanly into Garmin BaseCamp,
GPX Studio, Strava (as activities), QGIS, JOSM, etc.

---

## 8. Audit log

Toolbar → 📜 (history icon).

Every state change is timestamped:

```
2026-06-02T19:14:33 | Session started (dynamicPath)
2026-06-02T19:14:35 | Resumed
2026-06-02T19:18:01 | Leak detected — fallback
2026-06-02T19:25:12 | Stopped (user=true)
```

The log is stored encrypted at rest via `EncryptedSharedPreferences` (Tink
under the hood) and capped at 200 entries.

---

## 9. Troubleshooting

| Symptom | Likely cause | Fix |
|---|---|---|
| Tap Start → "Select Smart Route as mock location app in Developer options." | Step 1.2 not done | Settings → Developer options → Select mock location app → **Smart Route** |
| Tap Start → "Could not register GPS test provider." | Some OEMs (Xiaomi, Vivo) require an extra "Allow mock locations" toggle inside their custom Developer options. | Find that toggle in your OEM-specific developer settings and enable it. |
| Map renders grey tiles | Maps key invalid, billing not enabled, or **Maps SDK for Android** not enabled | Re-check `secrets.properties`, enable the API and billing in Google Cloud Console, rebuild |
| Search returns no results | **Places API** not enabled for the project/key | Enable Places API in Google Cloud Console |
| No road route / straight line only | **Directions API** not enabled, or device offline while editing | Enable Directions API; confirm internet when placing markers |
| Route not saved after map edit | Save tapped before markers moved, or while directions still loading | Drag markers to real locations; wait for road route, then tap **Save route** |
| Metrics frozen / buttons disabled | Session failed to start or mock app not selected | Confirm mock app in Developer options; tap **Stop** then **Start** again |
| Other apps don't see the mock fix | Many apps (banking, Pokémon Go, ride-share) actively detect mock locations via `Location#isMock()`. This is a safety feature of those apps, not a bug here. | Test with a non-blocking app like Google Maps or GPS Test. |
| Notification missing on Android 13 + | Notification permission denied | Settings → Apps → Smart Route → Notifications → enable. |
| Status pill stays red ("Leak detected") | Real GPS fix and injected coordinate diverge by &gt; 150 m, which is normal at session start before the first lock | Wait one or two intervals; the pill returns to green once the next FusedLocation poll comes back consistent. |

---

## 10. Cleaning up

When you're done:

1. Tap **Stop** in-app, or use the notification's **Stop** action.
2. (Optional) Settings → Developer options → Select mock location app → **(none)** — this de-authorizes Smart Route system-wide.
3. (Optional) `adb uninstall com.cybersec.smartroute`.

The encrypted prefs and any GPX files in scoped external storage are wiped
when the app is uninstalled.
