# zou-lu-shang-2

Android app: paint OpenStreetMap by walking. Your GPS position acts like a real brush — movement draws strokes, staying adds dots.

## Features

- OpenStreetMap map with live GPS location marker
- **Brush toolbar** on the main screen — color palette and thickness slider (like Paint/Corel)
- **Start** / **Stop** painting session (foreground GPS sampling every 3 seconds)
- Stroke-based storage (Room SQLite) with color and thickness per stroke
- Export / import drawing as JSON from Settings
- Clear drawing from Settings

## Build

```bash
cd zou-lu-shang-2
chmod +x gradlew
./gradlew assembleRelease
```

APK: `app/build/outputs/apk/release/app-release.apk`

## Permissions

- Fine/coarse location — map position and painting
- Notifications — foreground painting session indicator

## Export format

JSON v2 with stroke segments: start/end lat/lng, color (ARGB), thickness in meters (`DrawingExportCodec`).
