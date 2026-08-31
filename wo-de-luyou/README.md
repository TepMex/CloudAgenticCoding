# wo-de-luyou (我的旅游)

Android travel dictionary for a China 2026 trip. Large category tiles open word cards with hanzi, pinyin, and Russian. Tap hanzi or pinyin to copy; open the same headword in **Pleco** via `plecoapi://x-callback-url/s?q=…`.

## Features

- **Categories** — one large tile per glossary category (places, sights, food, transport, phrases, …)
- **Word cards** — hanzi, pinyin, Russian, optional region / note / priority
- **Copy** — tap characters to copy hanzi; tap pinyin to copy pinyin
- **Pleco** — x-callback-url search (`plecoapi://x-callback-url/s?q=` + `x-source=wo-de-luyou`)
- **Search** — hanzi, tone-stripped pinyin, Russian, region, note
- Offline: glossary is bundled from the trip TSV (174 entries)

## Requirements

- Android 14 (API 34) or newer — `minSdk` 34; `targetSdk` and `compileSdk` are 36
- Pleco (optional) for dictionary lookups

## Build

```bash
cd wo-de-luyou
./gradlew assembleRelease
```

APK: `app/build/outputs/apk/release/app-release.apk`

Release builds use the committed **sideload keystore** (`sideload.keystore` + `sideload-signing.properties`) so CI and local builds share one signing key with the other monorepo Android apps. Optional override: `wodeluyou.signing*` in `local.properties`.

Copy `local.properties.example` to `local.properties` and set `sdk.dir`.

## Install / update

1. Download the latest `wo-de-luyou.apk` from GitHub Pages and install it over the existing app.
2. If Android refuses the install (older build signed with a different key), uninstall once, reinstall, then later updates stay in place.

## Deployment

On push to `master`, `.github/workflows/deploy.yml` builds the release APK, verifies sideload signing, and publishes it on GitHub Pages at `/<repository>/wo-de-luyou/wo-de-luyou.apk` with a small `index.html` landing page.

## Tech stack

- Kotlin, Jetpack Compose, Material 3
- Navigation Compose
- Bundled UTF-8 TSV glossary
