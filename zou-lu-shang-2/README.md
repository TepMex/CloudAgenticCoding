# zou-lu-shang-2

Android app: paint OpenStreetMap by walking. Your GPS position acts like a brush — movement draws lines, staying increases color intensity.

## Features

- OpenStreetMap map with live GPS location marker
- **Start** / **Stop** painting session (foreground GPS sampling every 3 seconds)
- Efficient cell-based storage (Room SQLite, zoom-16 grid with intensity values)
- Export / import drawing as compact JSON from Settings
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

JSON with packed cell keys and intensity values (`DrawingExportCodec`).
