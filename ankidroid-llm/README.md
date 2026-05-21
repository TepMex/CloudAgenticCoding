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

Release builds are signed with the committed **sideload keystore** (`sideload.keystore` + `sideload-signing.properties`) so every CI and local build uses the same key. New APKs install **over** the previous version and keep app data (downloaded LiteRT models, settings).

APK output: `app/build/outputs/apk/release/app-release.apk`.

Optional: override signing via `ankidroidllm.signing*` entries in `local.properties` (only if you intentionally use a different key).

### Updating on your phone

1. Download the latest `ankidroid-llm.apk` from GitHub Pages and install it over the existing app.
2. If Android refuses (signature changed from an older debug or CI build), **uninstall once**, install the latest APK, then future updates install in place.

## CI and download

On push to `master`, `.github/workflows/deploy.yml` builds the release APK, verifies sideload signing, and publishes it on GitHub Pages at `/<repository>/ankidroid-llm/ankidroid-llm.apk` with a small `index.html` landing page.
