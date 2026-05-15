# Monorepo (GitHub Pages)

This repository hosts multiple browser apps. Each app lives in its own top-level folder and is built and deployed independently to a matching path on **project** GitHub Pages (`https://<owner>.github.io/<repository>/…`).

## Apps

| Folder           | Published path             | Description                        |
| ---------------- | -------------------------- | ---------------------------------- |
| `socratus`       | `/<repo>/socratus/`        | Socratus · Socratic Reading Agent  |
| `mandarin-koan`  | `/<repo>/mandarin-koan/`   | Mandarin Koan                      |
| `chesswatch`     | `/<repo>/chesswatch/`      | ChessWatch · Android time tracker (APK) |
| `ankidroid-llm` | `/<repo>/ankidroid-llm/`   | AnkiDroid LLM · Android story from study queue (APK) |

Develop and build from inside the app directory (see each app’s `README.md`). Deployment is configured in `.github/workflows/deploy.yml`.
