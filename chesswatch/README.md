# ChessWatch

Native Android (Kotlin) app: a grid of rounded activity tiles with timers. Exactly one activity is selected at a time; its timer runs so you always know what you are doing now.

- **Requirements:** Android 14 or newer (min SDK 34), built with compile SDK 35.
- **Defaults:** One activity named `idle`. Use the **+** button to add more (e.g. working, reading, fitness). Long-press a tile to remove it (`idle` cannot be removed).
- **Layout:** 2 columns on narrow phones, 3 from ~480dp width upward.

## Local build

Install [Android Studio](https://developer.android.com/studio) or the command-line SDK, then from this directory:

```bash
./gradlew assembleRelease
```

Release builds are signed with the committed **sideload keystore** (`sideload.keystore` + `sideload-signing.properties`) so every CI and local build uses the same key. New APKs install **over** the previous version.

APK output: `app/build/outputs/apk/release/app-release.apk`.

Optional: override signing via `chesswatch.signing*` entries in `local.properties`.

### Updating on your phone

1. Download the latest `chesswatch.apk` from GitHub Pages and install it over the existing app.
2. If Android refuses (e.g. you installed an older build signed with a different key), **uninstall once**, install the latest APK, then future updates install in place.

## CI and download

On push to `master`, `.github/workflows/deploy.yml` builds the release APK, verifies sideload signing, and publishes it on GitHub Pages at `/<repository>/chesswatch/chesswatch.apk` together with a small `index.html` landing page.
