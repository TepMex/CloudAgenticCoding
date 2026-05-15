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

Release builds in this repo use the debug keystore for signing so CI can produce an installable APK without secrets.

APK output: `app/build/outputs/apk/release/app-release.apk`.

## CI and download

On push to `master`, `.github/workflows/deploy.yml` builds the release APK and publishes it on GitHub Pages at `/<repository>/chesswatch/chesswatch.apk` together with a small `index.html` landing page.
