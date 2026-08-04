# Han Count Android — SPEC

## Purpose

Ship **Han Count Me** (量词守门人) as a sideloadable Android APK by wrapping the existing Phaser/Vite web game in a thin native shell. Players get the same classifier gate-defense game offline on Android 14+, downloadable from the monorepo GitHub Pages landing page.

Audience: Mandarin learners who already use the web game and want a home-screen install with landscape play and local progress.

## Requirements

1. Package the production build of `han-count-me` into the APK under `assets/www/` and load it in a full-screen WebView (`file:///android_asset/www/index.html`).
2. Preserve game behavior: JavaScript, DOM storage (`localStorage` progress), Web Audio, and touch/keyboard input must work inside the WebView.
3. Lock the activity to **landscape** (sensor landscape) to match the game’s 1280×720 FIT layout.
4. Use immersive system UI (hide status/nav bars) so the game fills the screen.
5. Application id `com.tepmex.hancount`; display name **Han Count Me**.
6. minSdk 34, compile/targetSdk 35; Kotlin + ViewBinding shell matching other monorepo Android apps.
7. Sign release (and debug when keystore present) with the shared committed **sideload** keystore so Pages APKs upgrade in place.
8. Publish a GitHub Pages landing at `/han-count-android/` with `han-count-android.apk` download, using the shared `android/landing` styles.
9. CI rebuilds the APK when `han-count-android/**` or `han-count-me/**` changes (bundled assets must stay in sync with the web game).
10. Provide a local/CI script to build `han-count-me` with relative `base: ./` and sync `dist` into `app/src/main/assets/www/`.

## Interfaces

| Interface | Detail |
| --------- | ------ |
| Launcher activity | `com.tepmex.hancount.MainActivity` — single WebView host |
| Bundled URI | `file:///android_asset/www/index.html` |
| Asset sync CLI | `./scripts/sync-web-assets.sh` (from `han-count-android/`) |
| Gradle | `./gradlew assembleRelease` → `app/build/outputs/apk/release/app-release.apk` |
| Pages download | `https://<host>/<repo>/han-count-android/han-count-android.apk` |
| Upstream game | Sibling project `han-count-me` (Phaser 3 + Vite + TypeScript) |

No deep links, no native plugins, no Play Store listing in v1.

## Data model

- **No native persistence.** Progress, settings, and dictionary unlocks remain in the WebView’s `localStorage` under the same versioned keys as the web game.
- Clearing app storage / uninstall wipes progress (same as clearing browser site data).
- Bundled `www/` is a build artifact of `han-count-me` (not edited by hand).

## UI / UX

1. Cold start → splash theme → WebView loads bundled `index.html` → Phaser boot/menu.
2. Portrait orientation is discouraged by sensor-landscape lock; if the device is held portrait, Android rotates to landscape.
3. System back: if the WebView history stack has an entry, go back; otherwise finish the activity.
4. Landing page: brand **Han Count Me**, short tagline, APK download link, update note (same pattern as other APK landings).

## Out of scope

- Native rewrite of game scenes or rules
- Online multiplayer / cloud sync of progress
- Play Store / App Bundle distribution
- Loading the live GitHub Pages URL instead of bundled assets
- Push notifications, accounts, ads
- Portrait-optimized layout (upstream game already shows a rotate hint)

## Acceptance criteria

1. `./scripts/sync-web-assets.sh` produces a non-empty `app/src/main/assets/www/index.html` with relative asset URLs.
2. `./gradlew assembleRelease` produces a sideload-signed APK that verifies with `android/verify-apk-sideload-cert.sh`.
3. Installing the APK on API 34+ opens the game menu without a network connection.
4. Completing a run persists high score across process death (WebView `localStorage`).
5. Deploy workflow includes `han-count-android` in `ANDROID_APPS` and rebuilds when the wrapper or `han-count-me` changes.
6. Root `README.md` lists the app with its Pages path.
