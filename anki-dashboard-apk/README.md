# Anki Dashboard (Android)

Native Android port of [anki-dashboard](https://github.com/TepMex/anki-dashboard): Anki statistics, progress charts, review heatmaps, and leeches — powered by **AnkiDroid** on your phone.

## Data sources

1. **AnkiDroid API** — deck list, card counts, intervals, and leeches via the [content provider](https://github.com/ankidroid/Anki-Android/wiki/AnkiDroid-API) (`READ_WRITE_DATABASE` permission), same pattern as `ankidroid-llm`.
2. **collection.anki2** — review history (revlog) for charts and calendars. Loaded from (in order):
   - **AnkiWeb sync** (menu → *Sync from AnkiWeb*) — download-only, same protocol as the [web dashboard](https://github.com/TepMex/anki-dashboard); cached locally in app storage.
   - A file you pick once from the menu (persisted URI).
   - Default paths when readable: `com.ichi2.anki/collection.anki2`, `AnkiDroid/collection.anki2`, etc.

On modern Android, AnkiDroid’s collection folder is often not readable by other apps; **AnkiWeb sync is the recommended way** to enable history charts.

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
