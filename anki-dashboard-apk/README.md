# Anki Dashboard (Android)

Native Android port of [anki-dashboard](https://github.com/TepMex/anki-dashboard): Anki statistics, progress charts, review heatmaps, and leeches — powered by **AnkiDroid** on your phone (no AnkiConnect or AnkiWeb sync).

## Data sources

1. **AnkiDroid API** — deck list, card counts, intervals, and leeches via the [content provider](https://github.com/ankidroid/Anki-Android/wiki/AnkiDroid-API) (`READ_WRITE_DATABASE` permission), same pattern as `ankidroid-llm`.
2. **collection.anki2** — review history (revlog) for charts and calendars. The app tries `/storage/emulated/0/AnkiDroid/collection.anki2` when readable, or you can pick the file once from the menu (persisted URI).

## Requirements

- Android 14+ (min SDK 34), AnkiDroid installed
- Grant **AnkiDroid database access** when prompted

## Build

```bash
./gradlew assembleRelease
```

APK: `app/build/outputs/apk/release/app-release.apk`

Release builds use the monorepo sideload keystore (see root `README.md`).

## CI

Push to `master` publishes `/<repo>/anki-dashboard-apk/anki-dashboard-apk.apk` on GitHub Pages (see `.github/workflows/deploy.yml`).
