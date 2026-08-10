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
| `han-count-me` | [han-count-me](https://tepmex.github.io/CloudAgenticCoding/han-count-me/) | Han Count Me · Chinese classifier / measure-word gate defense |
| `han-count-android` | [han-count-android](https://tepmex.github.io/CloudAgenticCoding/han-count-android/) | Han Count Me · Android WebView wrapper (APK) |
| `rth-agriculture` | [rth-agriculture](https://tepmex.github.io/CloudAgenticCoding/rth-agriculture/) | Memory Garden · meaning→write Hanzi garden (Сад памяти) |
| `rth-agriculture-android` | [rth-agriculture-android](https://tepmex.github.io/CloudAgenticCoding/rth-agriculture-android/) | Memory Garden · Android WebView wrapper (APK) |
| `sense-of-text` | [sense-of-text](https://tepmex.github.io/CloudAgenticCoding/sense-of-text/) | Sense of Text · highlight important words (WASM embeddings or LLM) |
| `map-of-chinese` | [map-of-chinese](https://tepmex.github.io/CloudAgenticCoding/map-of-chinese/) | Map of Chinese · Mandarin characters on an initial × final matrix |
| `mandarin-kanshu-pengyou` | [mandarin-kanshu-pengyou](https://tepmex.github.io/CloudAgenticCoding/mandarin-kanshu-pengyou/) | 看书朋友 · local-first Chinese EPUB reading companion |
| `chesswatch` | [chesswatch](https://tepmex.github.io/CloudAgenticCoding/chesswatch/) | ChessWatch · Android time tracker (APK) |
| `ankidroid-llm` | [ankidroid-llm](https://tepmex.github.io/CloudAgenticCoding/ankidroid-llm/) | AnkiDroid LLM · Android story from study queue (APK) |
| `anki-entertainer` | [anki-entertainer](https://tepmex.github.io/CloudAgenticCoding/anki-entertainer/) | Anki Entertainer · LLM text chunks from AnkiDroid deep link (APK) |
| `local-tts` | [local-tts](https://tepmex.github.io/CloudAgenticCoding/local-tts/) | Local TTS · on-device Vosk ONNX text-to-speech (APK) |
| `anki-dashboard-apk` | [anki-dashboard-apk](https://tepmex.github.io/CloudAgenticCoding/anki-dashboard-apk/) | Anki Dashboard · Android stats from synced collection.anki2 (APK) |
| `zuo-tasks` | [zuo-tasks](https://tepmex.github.io/CloudAgenticCoding/zuo-tasks/) | ZuoTasks · nested projects and regular tasks (APK) |
| `ctx-calendar` | [ctx-calendar](https://tepmex.github.io/CloudAgenticCoding/ctx-calendar/) | ctx-calendar · month calendar with gallery photo previews (APK) |
| `wo-zai-naar` | [wo-zai-naar](https://tepmex.github.io/CloudAgenticCoding/wo-zai-naar/) | wo-zai-naar · daily movement tracker with OSM map (APK) |
| `zou-lu-shang` | [zou-lu-shang](https://tepmex.github.io/CloudAgenticCoding/zou-lu-shang/) | zou-lu-shang · Takeout location history tile grid on OSM (APK) |
| `zou-lu-shang-2` | [zou-lu-shang-2](https://tepmex.github.io/CloudAgenticCoding/zou-lu-shang-2/) | zou-lu-shang-2 · Paint the map by walking with GPS brush (APK) |
| `pair-comp-elo` | [pair-comp-elo](https://tepmex.github.io/CloudAgenticCoding/pair-comp-elo/) | Pair Comp Elo · pairwise ranking with Elo + decay (APK) |
| `running-log` | [running-log](https://tepmex.github.io/CloudAgenticCoding/running-log/) | running-log · Mi Band running journal (APK) |
| `ideal-timing` | [ideal-timing](https://tepmex.github.io/CloudAgenticCoding/ideal-timing/) | ideal-timing · 16h day clock from Mi Fitness wake-up (APK) |

Develop and build from inside the app directory (see `README.md` in each app that ships one). Deployment is configured in `.github/workflows/deploy.yml`.

Android APKs (`chesswatch`, `ankidroid-llm`, `anki-entertainer`, `local-tts`, `anki-dashboard-apk`, `zuo-tasks`, `ctx-calendar`, `wo-zai-naar`, `zou-lu-shang`, `zou-lu-shang-2`, `pair-comp-elo`, `running-log`, `ideal-timing`, `han-count-android`, `rth-agriculture-android`) are signed with a shared committed sideload keystore so GitHub Pages builds upgrade in place without wiping app data. See each app’s README for a one-time uninstall if you still have an older differently signed build.
