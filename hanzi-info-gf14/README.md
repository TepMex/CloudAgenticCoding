# hanzi-info-gf14 (汉字 GF14)

Android map of GF 0014-2009 components for the 3500 most frequent characters. The character you type sits in the center of a hexagonal grid. Each direction is one component of that character: along the ray are other characters that differ only by replacing that component.

## Features

- **Center cell** — the character from the field, a tap, or an incoming link
- **Up to six rays** — east, west, then the four diagonals. Characters that replace the same component line up on one ray, most frequent first
- **Pan and pinch** — long rays (especially single-component characters) extend past the screen
- **Back** — returns to the previous center
- **x-callback-url** — other apps open a character directly
- Offline: the component JSON is bundled

## Open from another app

```
hanziinfogf14://x-callback-url?q=请
hanziinfogf14://x-callback-url/open?q=%E8%AF%B7
hanziinfogf14://x-callback-url/q/明
```

`q` is the first CJK ideograph in the value. `x-source`, `x-success`, `x-error`, and `x-cancel` are accepted and ignored — the app stays on the map. **Ссылка** copies the URL for the current character.

## Requirements

- Android 14 (API 34) or newer — `minSdk` 34; `targetSdk` and `compileSdk` are 36

## Build

```bash
cd hanzi-info-gf14
./gradlew test assembleRelease
```

APK: `app/build/outputs/apk/release/app-release.apk`

Release builds use the committed **sideload keystore** (`sideload.keystore` + `sideload-signing.properties`) so CI and local builds share one signing key with the other monorepo Android apps. Optional override: `hanziinfogf14.signing*` in `local.properties`.

Copy `local.properties.example` to `local.properties` and set `sdk.dir`.

## Install / update

1. Download the latest `hanzi-info-gf14.apk` from GitHub Pages and install it over the existing app.
2. If Android refuses the install (older build signed with a different key), uninstall once, reinstall, then later updates stay in place.

## Deployment

On push to `master`, `.github/workflows/deploy.yml` builds the release APK, verifies sideload signing, and publishes it on GitHub Pages at `/<repository>/hanzi-info-gf14/hanzi-info-gf14.apk` with a small `index.html` landing page.

## Tech stack

- Kotlin, Jetpack Compose, Material 3
- Bundled GF 0014 component JSON (3500 level-1 characters)
