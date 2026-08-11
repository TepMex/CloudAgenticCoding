# Memory Garden Android — SPEC

## Purpose

Ship **Memory Garden** (Сад памяти / `rth-agriculture`) as a sideloadable Android APK by wrapping the existing React/Vite web game in a thin native shell. Players get the same meaning→write Hanzi garden game on Android 14+, downloadable from the monorepo GitHub Pages landing page.

Audience: Mandarin learners who already use the web game and want a home-screen install with local progress.

## Requirements

1. Package the production build of `rth-agriculture` into the APK under `assets/www/` and load it in a full-screen WebView (`file:///android_asset/www/index.html`).
2. Preserve game behavior: JavaScript, IndexedDB (Dexie), DOM storage, canvas/Hanzi Writer pointer input, and Web Audio must work inside the WebView.
3. Allow **sensor** orientation (portrait and landscape) to match the web game’s adaptive desktop/mobile UI.
4. Use immersive system UI (hide status/nav bars) so the game fills the screen.
5. Application id `com.tepmex.rthagriculture`; display name **Memory Garden**.
6. minSdk 34, compile/targetSdk 35; Kotlin + ViewBinding shell matching other monorepo Android apps.
7. Sign release (and debug when keystore present) with the shared committed **sideload** keystore so Pages APKs upgrade in place.
8. Publish a GitHub Pages landing at `/rth-agriculture-android/` with `rth-agriculture-android.apk` download, using the shared `android/landing` styles.
9. CI rebuilds the APK when `rth-agriculture-android/**` or `rth-agriculture/**` changes (bundled shell must stay in sync with the web game).
10. Provide a local/CI script to build `rth-agriculture` with relative `base: ./` and sync `dist` into `app/src/main/assets/www/`.
11. **Heavy art stays out of the APK.** Garden map PNGs, battle-field backdrops, and cleaning-court images are downloaded after launch from the monorepo on GitHub (`raw.githubusercontent.com/…/rth-agriculture/public/…`), then cached (Cache Storage + blob URLs) so later launches can paint offline. Hanzi stroke JSON and the JS/CSS shell remain bundled.
12. Declare `INTERNET` so the WebView can fetch heavy art over HTTPS.

## Interfaces

| Interface | Detail |
| --------- | ------ |
| Launcher activity | `com.tepmex.rthagriculture.MainActivity` — single WebView host |
| Bundled URI | `file:///android_asset/www/index.html` |
| Asset sync CLI | `./scripts/sync-web-assets.sh` (from `rth-agriculture-android/`) |
| Remote heavy art | `VITE_REMOTE_ASSET_BASE` / `RTH_REMOTE_ASSET_BASE` (default: GitHub raw `…/rth-agriculture/public/`) |
| Gradle | `./gradlew assembleRelease` → `app/build/outputs/apk/release/app-release.apk` |
| Pages download | `https://<host>/<repo>/rth-agriculture-android/rth-agriculture-android.apk` |
| Upstream game | Sibling project `rth-agriculture` (React + Vite + TypeScript) |

No deep links, no native plugins, no Play Store listing in v1.

## Data model

- **No native persistence.** Cards, FSRS state, and field unlocks remain in the WebView’s IndexedDB (Dexie) under the same schema as the web game.
- Heavy art cache lives in the WebView Cache Storage (`rth-heavy-art-v1`); clearing app storage / uninstall wipes progress and cached art.
- Bundled `www/` is a build artifact of `rth-agriculture` (not edited by hand) and must omit heavy PNGs so the release APK stays under GitHub’s 100 MB push limit.

## UI / UX

1. Cold start → splash theme → WebView loads bundled `index.html` → optional first-launch download of map art (“Загрузка карты сада…”) → welcome / map.
2. Orientation follows the device sensor; the upstream UI adapts to portrait and landscape.
3. System back: if the WebView history stack has an entry, go back; otherwise finish the activity.
4. Landing page: brand **Memory Garden**, short tagline, APK download link, update note (same pattern as other APK landings).
5. WebView must honor the game’s `width=device-width` viewport at 100% scale (no overview zoom) so mobile battle layout matches Chrome on phones — writer canvas clipped, no stroke SVG bleed over chrome.
6. Entering a battle downloads that garden’s four backdrop PNGs on demand (then caches them).

## Out of scope

- Native rewrite of map, battle, or FSRS logic
- Online multiplayer / cloud sync of progress
- Play Store / App Bundle distribution
- Loading the live GitHub Pages **app shell** instead of bundled `www/` (only heavy PNGs are remote)
- Push notifications, accounts, ads

## Acceptance criteria

1. `./scripts/sync-web-assets.sh` produces a non-empty `app/src/main/assets/www/index.html` with relative shell URLs, **without** shipping `garden-map*.png` or `battle-fields/` inside `www/`.
2. `./gradlew assembleRelease` produces a sideload-signed APK under 100 MB that verifies with `android/verify-apk-sideload-cert.sh`.
3. First launch with network downloads map art from GitHub raw URLs and reaches the welcome/map screens; a later launch with cache populated still shows the map offline.
4. Completing a battle persists card/field state across process death (WebView IndexedDB).
5. Deploy workflow includes `rth-agriculture-android` in `ANDROID_APPS` and rebuilds when the wrapper or `rth-agriculture` changes.
6. Root `README.md` lists the app with its Pages path.
7. On a phone-sized WebView, battle chrome does not overlap a clipped circular writer; layout matches mobile web (no overview-scaled desktop CSS).
8. V2 garden map (including negative layer) and field-cleaning backdrops render via remote GitHub URLs + cache — writing field stays visibly rendered through dirty → clean states rather than a blank dark void.
9. Battle quiz works without network for Hanzi stroke JSON (bundled; loaded via XHR because Fetch is blocked on `file://`).
10. `scripts/sync-web-assets.sh` fails if the bundled `www/` tree exceeds 95 MB.
