# Monorepo (GitHub Pages)

This repository hosts multiple browser apps. Each app lives in its own top-level folder and is built and deployed independently to a matching path on **project** GitHub Pages (`https://<owner>.github.io/<repository>/…`).

## Apps

| Folder           | Published path             | Description                        |
| ---------------- | -------------------------- | ---------------------------------- |
| `socratus`       | `/<repo>/socratus/`        | Socratus · Socratic Reading Agent  |
| `mandarin-koan`  | `/<repo>/mandarin-koan/`   | Mandarin Koan                      |
| `hanzi-info`     | `/<repo>/hanzi-info/`      | Hanzi Info · phonetic DB (EN/RU UI, Russian glosses) |
| `chesswatch`     | `/<repo>/chesswatch/`      | ChessWatch · Android time tracker (APK) |
| `ankidroid-llm` | `/<repo>/ankidroid-llm/`   | AnkiDroid LLM · Android story from study queue (APK) |
| `local-tts`     | `/<repo>/local-tts/`       | Local TTS · on-device Vosk ONNX text-to-speech (APK) |

Develop and build from inside the app directory (see `README.md` in each app that ships one). Deployment is configured in `.github/workflows/deploy.yml`.

Android APKs (`chesswatch`, `ankidroid-llm`, `local-tts`) are signed with a shared committed sideload keystore so GitHub Pages builds upgrade in place without wiping app data. See each app’s README for a one-time uninstall if you still have an older differently signed build.
