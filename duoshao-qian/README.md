# duoshao-qian (多少钱)

Android listening game for tourist prices in China. See a snack or drink, tap **多少钱?**, hear the yuan amount in Chinese, then pay with stylized RMB notes.

## Features

- Generated stall photos: water, soy milk, tea egg, tanghulu, baozi, jianbing, fruit, milk tea, skewers, dumplings, fried rice, beef noodles, malatang, roast-duck rice
- Prices stay in a realistic band for each item, and every bundled spoken yuan amount (¥1–40 in the audio catalog) is practiced about equally
- Bundled Chinese clips from `chinese_money_numbers.json` (base64 audio per number)
- Wallet with every current paper denomination: ¥1, ¥5, ¥10, ¥20, ¥50, ¥100
- Exact-tender scoring; replay the clip until it clicks
- Offline after install

## Requirements

- Android 14 (API 34) or newer — `minSdk` 34; `targetSdk` and `compileSdk` are 36

## Build

```bash
cd duoshao-qian
./gradlew assembleRelease
```

APK: `app/build/outputs/apk/release/app-release.apk`

Release builds use the committed **sideload keystore** so CI and local builds share one signing key with the other monorepo Android apps.

Copy `local.properties.example` to `local.properties` and set `sdk.dir`.

To regenerate spoken prices (only if you are replacing the bundled catalog):

```bash
python3 scripts/generate_money_audio.py
```

If a `chinese_money_numbers.json` already exists next to the script, it is copied into assets unchanged.

## Install / update

1. Download the latest `duoshao-qian.apk` from GitHub Pages and install it over the existing app.
2. If Android refuses the install (older build signed with a different key), uninstall once, reinstall, then later updates stay in place.

## Deployment

On push to `master`, `.github/workflows/deploy.yml` builds the release APK, verifies sideload signing, and publishes it on GitHub Pages at `/<repository>/duoshao-qian/duoshao-qian.apk`.

## Tech stack

- Kotlin, Jetpack Compose, Material 3
- Bundled JSON audio catalog + JPEG product photos
