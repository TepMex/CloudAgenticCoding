# Общий элемент — Android SPEC

## Purpose

Ship the same-element trainer as a sideloadable Android APK. The React app is bundled offline inside a WebView so a learner can drill shared-element characters without a network.

Audience: the same Mandarin learner as the web app, installing from the GitHub Pages landing.

## Requirements

1. Package the production build of `same-element` into `assets/www/` and load `file:///android_asset/www/index.html`.
2. JavaScript, DOM storage, and Hanzi Writer pointer input work in the WebView. Stroke JSON loads via XHR because Fetch is blocked on `file://`.
3. Sensor orientation, immersive system UI, screen kept on.
4. Application id `com.tepmex.sameelement`; launcher name **Общий элемент**.
5. minSdk 34, compile/targetSdk 35; Kotlin + ViewBinding.
6. Sign release and debug with the shared committed sideload keystore.
7. GitHub Pages landing at `/same-element-android/` with the APK download.
8. CI rebuilds the APK when `same-element-android/**` or `same-element/**` changes.
9. `scripts/sync-web-assets.sh` builds the web app with relative `base: ./` and fails if `www/` exceeds 95 MB or `hanzi/江.json` is missing.

## Interfaces

| Interface | Detail |
| --------- | ------ |
| Launcher | `com.tepmex.sameelement.MainActivity` |
| Bundled URI | `file:///android_asset/www/index.html` |
| Sync | `./scripts/sync-web-assets.sh` |
| Gradle | `./gradlew assembleRelease` |
| Pages | `https://<host>/<repo>/same-element-android/same-element-android.apk` |
| Upstream | Sibling project `same-element` |

## Data model

No native database. The selected list and clean/miss counts stay in WebView `localStorage`. Uninstall clears them.

## UI / UX

Cold start opens the element list. System back walks WebView history, then finishes the activity. The landing page names the app and links the APK.

## Out of scope

Native rewrite of the sheet, Play Store listing, loading the live site instead of bundled assets.

## Acceptance criteria

1. `./scripts/sync-web-assets.sh` writes a non-empty `app/src/main/assets/www/index.html` and `hanzi/江.json`.
2. `./gradlew assembleRelease` produces a sideload-signed APK that `android/verify-apk-sideload-cert.sh` accepts.
3. The deploy workflow lists `same-element-android` and rebuilds it when the wrapper or `same-element` changes.
4. Root `README.md` lists the APK path.
