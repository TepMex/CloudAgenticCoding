# Anki Dashboard (Android)

Native Android port of [anki-dashboard](https://github.com/TepMex/anki-dashboard): Anki statistics, progress charts, review heatmaps, and leeches — powered by your **collection.anki2** database (same data model as the web app).

## Data source

All statistics come from **collection.anki2**, loaded in this order:

1. **AnkiWeb sync** (menu → *Sync from AnkiWeb*) — download-only, same protocol as the [web dashboard](https://github.com/TepMex/anki-dashboard); cached locally in app storage.
2. A file you pick once from the menu (persisted URI).
3. Default paths when readable: `com.ichi2.anki/collection.anki2`, `AnkiDroid/collection.anki2`, etc.

**AnkiWeb sync is the recommended way** on modern Android (other apps usually cannot read AnkiDroid’s private storage).

AnkiDroid is **not** required — you only need a synced or imported collection file.

## Requirements

- Android 14+ (min SDK 34)
- AnkiWeb account (for sync) or a copy of `collection.anki2`

## Build

```bash
./gradlew assembleRelease
```

APK: `app/build/outputs/apk/release/app-release.apk`

Release builds use the monorepo sideload keystore (see root `README.md`).

## CI

Push to `master` publishes `/<repo>/anki-dashboard-apk/anki-dashboard-apk.apk` on GitHub Pages (see `.github/workflows/deploy.yml`).
