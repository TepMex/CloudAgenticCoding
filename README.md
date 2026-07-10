# Monorepo (GitHub Pages)

This repository hosts multiple browser apps and Android APK landing pages. Each app lives in its own top-level folder and is built and deployed independently to a matching path on **project** GitHub Pages.

**Live site base:** [https://tepmex.github.io/CloudAgenticCoding/](https://tepmex.github.io/CloudAgenticCoding/)

## Apps

| Folder | Live site | Description |
| ------ | --------- | ----------- |
| `socratus` | [socratus](https://tepmex.github.io/CloudAgenticCoding/socratus/) | Socratus · Socratic Reading Agent |
| `mandarin-koan` | [mandarin-koan](https://tepmex.github.io/CloudAgenticCoding/mandarin-koan/) | Mandarin Koan |
| `hanzi-info` | [hanzi-info](https://tepmex.github.io/CloudAgenticCoding/hanzi-info/) | Hanzi Info · phonetic DB (EN/RU UI, Russian glosses) |
| `hanzi-reading-roguelike` | [hanzi-reading-roguelike](https://tepmex.github.io/CloudAgenticCoding/hanzi-reading-roguelike/) | Hanzi Reading Roguelike · creature sprites & game scaffold |
| `sense-of-text` | [sense-of-text](https://tepmex.github.io/CloudAgenticCoding/sense-of-text/) | Sense of Text · highlight important words (WASM embeddings or LLM) |
| `chesswatch` | [chesswatch](https://tepmex.github.io/CloudAgenticCoding/chesswatch/) | ChessWatch · Android time tracker (APK) |
| `ankidroid-llm` | [ankidroid-llm](https://tepmex.github.io/CloudAgenticCoding/ankidroid-llm/) | AnkiDroid LLM · Android story from study queue (APK) |
| `local-tts` | [local-tts](https://tepmex.github.io/CloudAgenticCoding/local-tts/) | Local TTS · on-device Vosk ONNX text-to-speech (APK) |
| `anki-dashboard-apk` | [anki-dashboard-apk](https://tepmex.github.io/CloudAgenticCoding/anki-dashboard-apk/) | Anki Dashboard · Android stats from synced collection.anki2 (APK) |
| `zuo-tasks` | [zuo-tasks](https://tepmex.github.io/CloudAgenticCoding/zuo-tasks/) | ZuoTasks · nested projects and regular tasks (APK) |
| `ctx-calendar` | [ctx-calendar](https://tepmex.github.io/CloudAgenticCoding/ctx-calendar/) | ctx-calendar · month calendar with gallery photo previews (APK) |
| `wo-zai-naar` | [wo-zai-naar](https://tepmex.github.io/CloudAgenticCoding/wo-zai-naar/) | wo-zai-naar · daily movement tracker with OSM map (APK) |
| `zou-lu-shang` | [zou-lu-shang](https://tepmex.github.io/CloudAgenticCoding/zou-lu-shang/) | zou-lu-shang · Takeout location history tile grid on OSM (APK) |
| `zou-lu-shang-2` | [zou-lu-shang-2](https://tepmex.github.io/CloudAgenticCoding/zou-lu-shang-2/) | zou-lu-shang-2 · Paint the map by walking with GPS brush (APK) |
| `pair-comp-elo` | [pair-comp-elo](https://tepmex.github.io/CloudAgenticCoding/pair-comp-elo/) | Pair Comp Elo · pairwise ranking with Elo + decay (APK) |

Develop and build from inside the app directory (see `README.md` in each app that ships one). Deployment is configured in `.github/workflows/deploy.yml`.

Android APKs (`chesswatch`, `ankidroid-llm`, `local-tts`, `anki-dashboard-apk`, `zuo-tasks`, `ctx-calendar`, `wo-zai-naar`, `zou-lu-shang`, `zou-lu-shang-2`, `pair-comp-elo`) are signed with a shared committed sideload keystore so GitHub Pages builds upgrade in place without wiping app data. See each app’s README for a one-time uninstall if you still have an older differently signed build.
