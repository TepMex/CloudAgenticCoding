# zou-lu-shang (走路上的)

Android app that visualizes **Google Takeout Location History** as a **zoom-15 tile grid** overlaid on **OpenStreetMap**.

## How it works

1. **City selection** — search a city via the [Nominatim](https://nominatim.openstreetmap.org/) API and cache its administrative boundary polygon (GeoJSON) in Room.
2. **Import** — load location points from a unified `takeout.db` (same schema as [ctx-calendar](../ctx-calendar/)) or stream-parse raw `Records.json`.
3. **Processing** — filter points with accuracy &lt; 50 m, cluster consecutive stationary samples, map survivors to slippy-map tiles at zoom 15, keep only tiles inside the city polygon (ray casting), and store unique tile IDs in Room.
4. **Map** — OSMDroid renders visited tiles as semi-transparent rectangles via a custom overlay; only tiles in the visible viewport are drawn, with `HashMap<Long, Boolean>` lookup during rendering.

After the initial city search and data import, the app works **offline-first** (cached boundaries, tiles, and previously fetched map tiles).

## Requirements

- Android **14+** (`minSdk` 34); `targetSdk` / `compileSdk` 36
- Network for Nominatim city search and OSM map tiles (first use)
- Google Takeout `takeout.db` (built with `takeout_db`) or `Records.json`

## Build

```bash
cd zou-lu-shang
./gradlew assembleRelease
```

APK: `app/build/outputs/apk/release/app-release.apk`

Release builds use the committed **sideload keystore** (`sideload.keystore` + `sideload-signing.properties`). Optional override: `zoulushang.signing*` in `local.properties`.

Copy `local.properties.example` to `local.properties` and set `sdk.dir`.

## Tech stack

- Kotlin, Jetpack Compose, Material 3
- Room (city boundaries + visited tiles)
- OSMDroid (OpenStreetMap)
- kotlinx.serialization JSON streaming for large Takeout files
- Nominatim API for city boundaries

## Updating

Install the latest APK from GitHub Pages over the existing app. If Android blocks the install (older build with a different signature), uninstall once and reinstall.

## Deployment / CI

Selective APK builds are configured in [`.github/workflows/deploy.yml`](../.github/workflows/deploy.yml). Published at `/<repo>/zou-lu-shang/zou-lu-shang.apk` on GitHub Pages.
