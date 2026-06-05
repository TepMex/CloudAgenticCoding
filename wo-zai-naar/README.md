# wo-zai-naar (我在哪儿)

Android app that samples your location every **15 minutes** in the background, stores coordinates in **SQLite** (Room), and shows each day’s path on an **OpenStreetMap** map.

## How it works

1. **WorkManager** enqueues a unique `PeriodicWorkRequest` (15-minute interval, the platform minimum).
2. **`LocationWorker`** (`CoroutineWorker`) calls `setForeground()` at the start of `doWork()`, which posts an ongoing notification via WorkManager’s `setForegroundAsync()` and briefly runs as a **foreground service** with `FOREGROUND_SERVICE_TYPE_LOCATION`.
3. **`FusedLocationProviderClient`** fetches a **single** point per run (`getCurrentLocation` with balanced power accuracy, falling back to `lastLocation`).
4. The sample is written to **`wo_zai_naar.db`** (`location_points` table).
5. The main screen loads today’s (or any past day’s) points and draws a polyline on **OSMDroid** / OpenStreetMap tiles.

## Requirements

- Android **14+** (`minSdk` 34); `targetSdk` / `compileSdk` 36
- **Fine or coarse location**, **background location**, and **notifications** (for foreground sampling)
- Network for map tiles

Grant **“Allow all the time”** for location when prompted so periodic work can run while the app is closed.

## Build

```bash
cd wo-zai-naar
./gradlew assembleRelease
```

APK: `app/build/outputs/apk/release/app-release.apk`

Release builds use the committed **sideload keystore** (`sideload.keystore` + `sideload-signing.properties`). Optional override: `wozainaar.signing*` in `local.properties`.

Copy `local.properties.example` to `local.properties` and set `sdk.dir`.

## Tech stack

- Kotlin, Jetpack Compose, Material 3
- WorkManager 2.x, Play Services Location
- Room (SQLite)
- OSMDroid (OpenStreetMap) — no API key

## Updating

Install the latest APK from GitHub Pages over the existing app. If Android blocks the install (older build with a different signature), uninstall once and reinstall.
