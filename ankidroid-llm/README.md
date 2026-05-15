# AnkiDroid LLM

Android (Kotlin) app that reads vocabulary from your **current AnkiDroid study queue** (via the [AnkiDroid content provider](https://github.com/ankidroid/Anki-Android/wiki/Ankidroid-Api)) and asks an LLM to write a short story using those words.

- **Requirements:** Android 14 or newer (min SDK 34), compile SDK 35.
- **On-device default:** [LiteRT-LM](https://ai.google.dev/edge/lite/android) with the community [Gemma 4 E2B IT](https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm) `.litertlm` model (downloaded on first use; large file).
- **Remote option:** OpenAI-compatible `POST /v1/chat/completions` (configure base URL, bearer token, and model name in Settings).

## Local build

Install the Android SDK (Android Studio or command-line tools), then from this directory:

```bash
./gradlew assembleRelease
```

Release builds use the debug keystore for signing so CI can produce an installable APK without secrets.

APK output: `app/build/outputs/apk/release/app-release.apk`.

## CI and download

On push to `master`, `.github/workflows/deploy.yml` builds the release APK and publishes it on GitHub Pages at `/<repository>/ankidroid-llm/ankidroid-llm.apk` with a small `index.html` landing page.
