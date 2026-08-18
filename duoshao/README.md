# DuoShaoGame · 多少

A mobile-first browser game for turning Arabic prices into spoken Chinese quickly. A price falls down the screen; hold the talk button, say the amount in Chinese, and release when you finish. Falling and spawn timers pause while push-to-talk is held, and recognition latency remains protected after release. The MVP supports whole amounts from 1 through 9999, non-repeating amounts within a run, sudden-death rounds, six learning stages, push-to-talk capture, and an entirely local Paraformer integration boundary.

No backend is required. Speech audio stays in the browser.

## Run the playable MVP

Requirements: [Bun](https://bun.sh/) 1.3 or newer and a current Chromium, Safari, or Firefox browser.

```bash
bun install
bun run dev --host
```

Open the URL Vite prints. For the fully playable developer mode, add `?stt=mock`:

```text
http://localhost:5173/?stt=mock
```

Enter the Chinese transcript displayed by a falling card and press Enter. It travels through the same normalizer, parser, numeric matcher, score logic, and pending-recognition path as real speech.

To emulate slow on-device inference:

```text
http://localhost:5173/?stt=mock&latency=800
```

Try submitting an answer shortly before a card crosses the line. The engine holds game over until that recognition request resolves (or its 2.5-second safety timeout expires).

## Checks and production build

```bash
bun test
bun run typecheck
bun run build
bun run preview --host
```

`dist/` is a static site and can be served by any static host. Microphone capture requires HTTPS except on `localhost`.

## Speech architecture

The layers are deliberately independent:

```text
push-to-talk button → MicrophoneCapture → short Float32 utterance
                                             ↓ transferable buffer
                                   speech.worker.ts / Paraformer
                                             ↓ transcript + timing
                               normalize → parse Chinese money
                                             ↓ numeric amount
                                        GameEngine
```

React components only use `SpeechRecognizer`; they never import sherpa-onnx. `MockSpeechRecognizer` and `ParaformerSpeechRecognizer` both produce the same timing/result events. The worker owns model initialization and inference, so animation and React stay on the main thread. `MicrophoneCapture` uses Web Audio and records only between button press and release. Audio is never uploaded or persisted.

Every attempt carries `speechStartedAt`, `speechEndedAt`, and `recognitionCompletedAt`. If speech began before a target's `boundaryAt`, that target becomes `pending-game-over`. A correct late result hits it; a wrong result ends the game; a stuck request ends it after 2500 ms. In development, utterance/inference/result latency is logged in the console.

## Configure Paraformer INT8

Large model/WASM artifacts are intentionally excluded from git. One command downloads the official Chinese-English `sherpa-onnx-paraformer-zh-small-2024-03-09` INT8 weights, installs a pinned sherpa-onnx 1.13.5 WebAssembly runtime plus DuoShao's browser ESM bridge under `public/speech/`, and writes the managed speech block in `.env.local`:

```bash
bun run setup:speech
```

The runtime is generated reproducibly from the official npm tarball: the setup script converts its Emscripten Node wrapper into browser ESM, retains the in-memory filesystem used for the model, and installs the matching WASM binary. The command is idempotent: existing weights and runtime are kept unless `--force` is passed. Inspect its actions without writing or downloading anything:

```bash
bun run setup:speech -- --dry-run
```

Useful options:

```bash
# Replace existing weights
bun run setup:speech -- --force

# Install only the model and tokens without changing .env.local
bun run setup:speech -- --weights-only

# Use another compatible Paraformer .tar.bz2 archive
bun run setup:speech -- --model-url https://example.com/model.tar.bz2

# Override the bundled bridge with another compatible ESM bridge
bun run setup:speech -- --runtime-source /path/to/sherpa-paraformer-bridge.js

# Or configure a bridge already hosted elsewhere with CORS enabled
bun run setup:speech -- --runtime-url https://cdn.example.com/sherpa-paraformer-bridge.js
```

Run `bun run setup:speech -- --help` for the complete CLI reference. The generated configuration is equivalent to:

```dotenv
VITE_SHERPA_RUNTIME_URL=/speech/sherpa-paraformer-bridge.js
VITE_PARAFORMER_MODEL_URL=/speech/model.int8.onnx
VITE_PARAFORMER_TOKENS_URL=/speech/tokens.txt
# VITE_PARAFORMER_CONFIG_URL=/speech/config.json
```

The generated model, runtime, and bridge files are ignored by git. `--weights-only` deliberately leaves `.env.local` unchanged.

On first load the worker fetches all configured URLs, reports progress, and attempts to store the model responses in Cache Storage under `duoshao-paraformer-v3`. Cache quota errors are non-fatal; the already downloaded model still initializes. Later sessions reuse the cache when the browser allows it. The Start button remains disabled until the bridge creates the recognizer. Download, initialization, missing WebAssembly, CORS, worker, and microphone errors are surfaced on the start screen with Retry.

### Runtime bridge contract

Sherpa's generated JavaScript filenames and wrapper surface can change between releases, so DuoShao pins a tiny stable ESM bridge at `VITE_SHERPA_RUNTIME_URL`. It must export exactly this function:

```ts
export async function createParaformerRecognizer(options: {
  modelUrl: string;
  tokensUrl: string;
  modelConfigUrl?: string;
  onProgress?: (progress: number, label: string) => void;
}): Promise<{
  recognize(samples: Float32Array, sampleRate: number): Promise<string>;
  dispose(): void | Promise<void>;
}>;
```

The bridge loads the generated sherpa-onnx Emscripten module, creates one offline/Paraformer recognizer, feeds each supplied utterance, returns `result.text`, deletes the stream after every utterance, and deletes the recognizer in `dispose`. This small adapter is the only release-specific code; game, audio, UI, and worker protocols do not change.

### Build an alternative upstream runtime

The sherpa-onnx project documents WebAssembly builds and ships a Paraformer-ready WASM example. Use a pinned upstream tag rather than `master`:

```bash
git clone https://github.com/k2-fsa/sherpa-onnx.git
cd sherpa-onnx
git checkout v1.12.34
git submodule update --init --recursive
```

Then follow the upstream [WebAssembly build instructions](https://k2-fsa.github.io/sherpa/onnx/wasm/index.html) to install Emscripten and build the ASR target. The current upstream flow prepares a model under `wasm/asr/assets` and runs:

```bash
./build-wasm-simd-asr.sh
```

The generated directory is `build-wasm-simd-asr/install/bin/wasm/asr/` and contains the Emscripten `.js`, `.wasm`, `.data`, and sherpa wrapper JavaScript. The official example's `createOnlineRecognizer(Module)` plus stream methods (`acceptWaveform`, `isReady`, `decode`, `getResult`, and stream deletion/reset) are the reference for implementing the bridge contract above.

For a separate non-streaming INT8 model, choose an official Paraformer archive from the [sherpa-onnx Paraformer model catalog](https://k2-fsa.github.io/sherpa/onnx/pretrained_models/offline-paraformer/paraformer-models.html) and pass it through `--model-url`. At deployment time, include the pinned build output, your bridge, and the generated `/speech/` model files in the static host. Verify the model's license before redistribution.

The repository does not include generated artifacts because model/runtime bundles are large. For UI-only development without those assets, use `?stt=mock`; opening production mode without configuration intentionally shows an actionable setup error rather than a blank screen.

## Recognition rules

Accepted forms include `300`, `三百`, `三百元`, `三百块`, `三百块钱`, `二百元`, and `两百元`. Harmless edge punctuation/whitespace is normalized. The parser accepts only a complete short numeral or money expression; sentences, multiple values, decimals, malformed numerals, and values outside 1–9999 are rejected.

Canonical answers use `元`, for example `101 → 一百零一元` and `2010 → 二千零一十元`.

## Project map

- `src/game/` — time-based engine and staged difficulty
- `src/chinese/` — pure canonical number generation, normalization, and strict parser
- `src/speech/` — recognizer interface, mock and Paraformer adapters, worker
- `src/audio/` — microphone lifecycle and push-to-talk capture
- `src/components/` — responsive DOM UI
- `tests/` — number/parser, difficulty, scoring, spawning, loss, fairness, timeout, and reset tests

The game uses CSS/DOM animation for the falling cards and engine timestamps for authoritative state, so behavior is independent of frame rate. On game over, spawning and movement stop, microphone resources are released, and Play again creates a clean engine round.
