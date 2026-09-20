# paizhao-unknown-hanzi (拍照未知汉字) — SPEC

## Purpose

Offline Android camera app for Mandarin learners who already use **Pleco**. The user photographs Chinese text or picks a still from the gallery; the app reads hanzi on-device and shows unique characters as large flashcard-like cells (character + toned pinyin). Known Hanzi stay in the results by default and can be hidden. Tapping a cell opens that character in Pleco. A share action sends every unique recognized character to the system share sheet.

Audience: a learner walking past signs, menus, and printed text who wants a fast “what is on this photo?” pass without a network.

## Requirements

1. Display name **拍照未知汉字**; project / Pages path **paizhao-unknown-hanzi**; application id `com.tepmex.paizhaounknownhanzi`.
2. Settings screen with a multiline text field. Every Chinese character in that field is a **Known Hanzi**. A **Сохранить** button persists the field.
3. Main screen has a **Сделать фото** control that captures a still photo (in-app CameraX preview) and an **Из галереи** control that opens the system photo picker and runs the same OCR pipeline on the chosen still.
4. After a photo, run **on-device** Chinese OCR with no network. Engine: **LiteRT** (`com.google.ai.edge.litert`) running bundled **PP-OCRv5** detector + recognizer (`.tflite` from `litert-community/PP-OCRv5-LiteRT`).
5. From OCR text, keep CJK ideographs only. Remaining unique characters appear once each, in first-seen (reading) order. A **Показывать известные иероглифы** checkbox (default on, persisted) keeps Known Hanzi in that selection; when off, drop any character that is in the Known Hanzi set.
6. Results are a **two-column grid**. Each cell shows the character large and its Hanyu pinyin with tone marks (multiple readings joined with `/`). Tapping a cell opens Pleco via `plecoapi://x-callback-url/s?q={hanzi}&x-source=paizhao-unknown-hanzi`. If Pleco is missing, show a snackbar instead of crashing. Beside the list, a share-text icon opens the system share sheet with every unique recognized hanzi (first-seen order, concatenated), including Known Hanzi.
7. The APK must work fully offline after install: models, dictionary, and pinyin data are bundled. No runtime download.
8. minSdk 34, compile/targetSdk 36; Kotlin + Jetpack Compose + Material 3.
9. Sign release (and debug when the keystore is present) with the shared committed sideload keystore. Publish a GitHub Pages landing at `/paizhao-unknown-hanzi/` with `paizhao-unknown-hanzi.apk`.

## Interfaces

| Interface | Detail |
| --------- | ------ |
| Launcher activity | `com.tepmex.paizhaounknownhanzi.MainActivity` |
| Known Hanzi persistence | DataStore preferences key `known_hanzi_text` |
| Show Known persistence | DataStore preferences key `show_known_hanzi` (boolean, default `true`) |
| OCR models | `assets/ocr/ppocr_det_fp16.tflite`, `assets/ocr/ppocr_rec_fp16.tflite` |
| OCR charset | `assets/ocr/ppocrv5_dict.txt` (CTC: blank + dict lines + space) |
| Pleco search | `plecoapi://x-callback-url/s?q={query}&x-source=paizhao-unknown-hanzi` |
| Camera | `android.permission.CAMERA`; CameraX ImageCapture |
| Gallery | `ActivityResultContracts.PickVisualMedia` (images only); no extra storage permission |
| Share | `Intent.ACTION_SEND` `text/plain` chooser with unique recognized hanzi |
| Gradle | `./gradlew assembleRelease` → `app/build/outputs/apk/release/app-release.apk` |
| Pages download | `https://<host>/<repo>/paizhao-unknown-hanzi/paizhao-unknown-hanzi.apk` |

No accounts, no sync, no incoming URL scheme in v1.

## Data model

- **Known Text** — raw string the user typed/pasted on Settings.
- **Known Hanzi** — unique CJK ideographs extracted from Known Text (BMP unified + Ext. A + compatibility ideographs).
- **Scan** — one captured or gallery still plus the OCR lines produced from it.
- **Unknown Hanzi** — unique CJK ideographs from the Scan that are not Known Hanzi, first-seen order.
- **Hanzi Card** — one selected hanzi from the Scan + its toned pinyin string. Selection is all unique Scan hanzi when Show Known is on; otherwise Unknown Hanzi only.
- **Share Text** — unique CJK ideographs from the Scan, first-seen order, concatenated, independent of Show Known.

Persistence: Known Text and Show Known. Uninstall wipes both. Photos are not stored.

## UI / UX

1. Cold start → capture screen: title, **Сделать фото**, **Из галереи**, Show Known checkbox, settings action.
2. First camera use requests the CAMERA permission; denial shows a short explanation and a retry control. Gallery remains available without CAMERA.
3. After shutter or gallery pick: processing overlay (model load / OCR). Then results grid or an empty state (“иероглифы не найдены” / “все иероглифы известны”). Results include Show Known and a share-text icon when any hanzi were recognized.
4. Settings: hint that every hanzi in the field counts as known; unique-count chip; **Сохранить**; snackbar on save.
5. Landing page: brand 拍照未知汉字 / paizhao-unknown-hanzi, APK download, update note.

## Out of scope

- Word segmentation, definitions, or an in-app dictionary (Pleco remains the dictionary)
- Handwriting input or video
- Cloud OCR, accounts, sync, analytics
- Traditional/simplified conversion (characters match exactly)
- Play Store / App Bundle
- Incoming deep links

## Acceptance criteria

1. Saving `你好世界` on Settings stores those four characters as Known Hanzi; OCR text `你好朋友` with Show Known off yields cards for `朋` and `友` only; with Show Known on (the default) it yields `你`, `好`, `朋`, and `友`.
2. A cell for `汉` builds `plecoapi://x-callback-url/s?q=%E6%B1%89&x-source=paizhao-unknown-hanzi`.
3. Pinyin for `汉` includes a tone mark (e.g. `hàn`).
4. Share Text for OCR `你好, 你好朋友!` is `你好朋友` and opens the system share sheet.
5. Picking an image from the gallery runs the same on-device OCR path as a camera still.
6. `./gradlew test assembleRelease` succeeds and the APK verifies with `android/verify-apk-sideload-cert.sh`.
7. The release APK contains the two `.tflite` models and `ppocrv5_dict.txt`; the app declares no `INTERNET` permission.
8. Deploy workflow includes `paizhao-unknown-hanzi` in `ANDROID_APPS` and rebuilds when `paizhao-unknown-hanzi/**` changes.
