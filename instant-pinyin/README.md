# instant-pinyin (即时拼音)

Offline Android camera that works like reading glasses for Chinese. Point the back camera at a sign, menu, or page and toned pinyin floats directly above each character. Tap a character to open it in **Pleco**.

Same on-device PP-OCRv5 stack as [paizhao-unknown-hanzi](../paizhao-unknown-hanzi). This app does not build a flashcard grid; it keeps the pinyin on the camera image.

## Features

- **Live overlay** — CameraX preview, pinyin pill above each horizontal hanzi and beside each vertical one.
- **Hold still** — one missed frame keeps the last labels so they do not flicker off.
- **Pleco** — tap a character: `plecoapi://x-callback-url/s?q=…&x-source=instant-pinyin`.
- **Still reading** — photograph or pick a gallery image. OCR runs once. The text is grouped into words (forward maximum match). Each cell is as wide as the word: pinyin on top, Russian gloss below. A tap opens the word in Pleco when a word was found, otherwise the character.
- **Фонарик** — torch toggle for dim text.
- **Offline** — LiteRT runtime + bundled PP-OCRv5 detector/recognizer. No `INTERNET` permission.

## Requirements

- Android 14 (API 34) or newer
- A back camera
- Pleco (optional) for lookups

## Build

```bash
cd instant-pinyin
./scripts/download-ocr-models.sh   # first time, or let Gradle preBuild do it
./gradlew test assembleRelease
```

APK: `app/build/outputs/apk/release/app-release.apk`

OCR weights come from [litert-community/PP-OCRv5-LiteRT](https://huggingface.co/litert-community/PP-OCRv5-LiteRT) (Apache-2.0). `preBuild` downloads them into `app/src/main/assets/ocr/` when missing.

Release builds use the committed **sideload keystore** so CI and local builds share one signing key with the other monorepo Android apps. Optional override: `instantpinyin.signing*` in `local.properties`.

Copy `local.properties.example` to `local.properties` and set `sdk.dir`.

## Install / update

1. Download the latest `instant-pinyin.apk` from GitHub Pages and install it over the existing app.
2. If Android refuses the install (older build signed with a different key), uninstall once, reinstall, then later updates stay in place.

## Deployment

On push to `master`, `.github/workflows/deploy.yml` builds the release APK, verifies sideload signing, and publishes it on GitHub Pages at `/<repository>/instant-pinyin/instant-pinyin.apk`.

## Tech stack

- Kotlin, Jetpack Compose, Material 3, CameraX Preview + ImageAnalysis
- LiteRT `CompiledModel` — detector and recognizer share one GPU `Environment` (CPU fallback)
- PP-OCRv5 FP16 detection + CTC recognition
- pinyin4j tone marks
