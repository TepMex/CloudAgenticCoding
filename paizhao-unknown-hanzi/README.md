# paizhao-unknown-hanzi (拍照未知汉字)

Offline Android camera for Mandarin learners. Photograph Chinese text or pick a still from the gallery; the app shows unique characters in a two-column grid (large hanzi + toned pinyin). Known Hanzi stay in the list by default and can be hidden. Tap a cell to open **Pleco** via x-callback-url, or share every recognized character through the system share sheet.

## Features

- **Settings** — paste any text; every Chinese character in the field is Known Hanzi. **Сохранить** writes it to DataStore.
- **Сделать фото** — CameraX still capture, then on-device OCR.
- **Из галереи** — system photo picker, same OCR pipeline, no extra storage permission.
- **Показывать известные иероглифы** — on by default; turn off to hide Known Hanzi from the grid.
- **Поделиться** — system share sheet with every unique recognized hanzi.
- **Unknown grid** — two columns; tap opens `plecoapi://x-callback-url/s?q=…`.
- **Offline** — LiteRT runtime + bundled PP-OCRv5 detector/recognizer. No `INTERNET` permission.

## Requirements

- Android 14 (API 34) or newer
- A camera
- Pleco (optional) for lookups

## Build

```bash
cd paizhao-unknown-hanzi
./scripts/download-ocr-models.sh   # first time, or let Gradle preBuild do it
./gradlew test assembleRelease
```

APK: `app/build/outputs/apk/release/app-release.apk`

OCR weights come from [litert-community/PP-OCRv5-LiteRT](https://huggingface.co/litert-community/PP-OCRv5-LiteRT) (Apache-2.0). `preBuild` downloads them into `app/src/main/assets/ocr/` when missing.

Release builds use the committed **sideload keystore** so CI and local builds share one signing key with the other monorepo Android apps. Optional override: `paizhaounknownhanzi.signing*` in `local.properties`.

Copy `local.properties.example` to `local.properties` and set `sdk.dir`.

## Install / update

1. Download the latest `paizhao-unknown-hanzi.apk` from GitHub Pages and install it over the existing app.
2. If Android refuses the install (older build signed with a different key), uninstall once, reinstall, then later updates stay in place.

## Deployment

On push to `master`, `.github/workflows/deploy.yml` builds the release APK, verifies sideload signing, and publishes it on GitHub Pages at `/<repository>/paizhao-unknown-hanzi/paizhao-unknown-hanzi.apk`.

## Tech stack

- Kotlin, Jetpack Compose, Material 3, CameraX
- LiteRT `CompiledModel` — detector and recognizer share one GPU `Environment` (CPU fallback)
- PP-OCRv5 FP16 detection + CTC recognition
- pinyin4j tone marks
