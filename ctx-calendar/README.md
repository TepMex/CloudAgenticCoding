# ctx-calendar

Android calendar in month view with photo previews from your gallery on each day — similar to Google Calendar’s month layout, but each day cell shows thumbnails of pictures taken that day.

## Features

- **Month view** — swipe between months; weekday headers follow your locale
- **Day cells** — up to four photo previews per day, with a count when there are more
- **Day detail** — tap a day for a two-column photo grid
- **Full screen** — tap a photo to view it full size (pinch to zoom)

## Requirements

- Android 16 (API 36) — `minSdk`, `targetSdk`, and `compileSdk` are all 36
- `READ_MEDIA_IMAGES` permission to read the device gallery

## Build

```bash
cd ctx-calendar
./gradlew assembleRelease
```

APK: `app/build/outputs/apk/release/app-release.apk`

Release builds use the committed **sideload keystore** (`sideload.keystore` + `sideload-signing.properties`). Optional override: `ctxcalendar.signing*` in `local.properties`.

## Tech stack

- Kotlin, Jetpack Compose, Material 3
- Coil for image loading
- MediaStore for gallery photos grouped by capture date
- Navigation Compose, ViewModel, Coroutines
