# ctx-calendar

Android calendar in month view with photo previews from your gallery on each day — similar to Google Calendar’s month layout, but each day cell shows thumbnails of pictures taken that day.

## Features

- **Month view** — swipe between months; weekday headers follow your locale
- **Day cells** — up to four photo previews per day, with a count when there are more
- **Day detail** — tap a day, then swipe tabs or use the tab bar:
  - **Photos** — two-column gallery grid (full screen on tap)
  - **Route** — OpenStreetMap polyline of your movement that day (from `takeout.db` chronology)
  - **Searches** — YouTube search history for the day
  - **Watched** — YouTube watch history for the day
- **Settings** — pick a `takeout.db` SQLite file (Google Takeout timeline + YouTube, built with `takeout_db`)

## takeout.db

Build a unified timeline database with the `takeout_db` converter, then choose that file in **Settings**. The app reads chronology (`path_point` / `position` for the map route, visits and activities in the list) and YouTube (`search` / `watch`) for the selected calendar day in your local timezone.

## Requirements

- Android 14 (API 34) or newer — `minSdk` 34; `targetSdk` and `compileSdk` are 36
- `READ_MEDIA_IMAGES` permission to read the device gallery
- Network access for OpenStreetMap tiles on the route tab

## Build

```bash
cd ctx-calendar
./gradlew assembleRelease
```

APK: `app/build/outputs/apk/release/app-release.apk`

Release builds use the committed **sideload keystore** (`sideload.keystore` + `sideload-signing.properties`). Optional override: `ctxcalendar.signing*` in `local.properties`.

Copy `local.properties.example` to `local.properties` and set `sdk.dir`.

## Tech stack

- Kotlin, Jetpack Compose, Material 3
- Coil for image loading
- MediaStore for gallery photos grouped by capture date
- SQLite (`takeout.db`) for chronology and YouTube timeline
- OpenStreetMap (OSMDroid) for daily route — no API key required
- Navigation Compose, ViewModel, DataStore, Coroutines
