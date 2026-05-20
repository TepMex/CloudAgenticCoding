# Local TTS

Proof-of-concept Android app: type text and synthesize speech **on device** using the [Vosk TTS](https://github.com/alphacep/vosk-tts) ONNX model (`vosk-model-tts-ru-0.9-multi`).

## Stack

- Kotlin + Material UI
- [ONNX Runtime Android](https://onnxruntime.ai/docs/get-started/with-java.html) for `model.onnx` and `bert/model.onnx`
- Kotlin port of vosk-tts `multistream_v1` pipeline (tokenizer, G2P, inference)
- Model downloaded on first run from [alphacephei.com](https://alphacephei.com/vosk/models/)

## Build

```bash
cd local-tts
./gradlew assembleRelease
```

APK: `app/build/outputs/apk/release/app-release.apk`

## Notes

- PoC targets the Russian multi-speaker model (5 voices). English models are not bundled yet.
- Requires network on first launch to download the model (~50 MB).
- Same sideload signing pattern as `ankidroid-llm` / `chesswatch` for CI and GitHub Pages APK hosting.
