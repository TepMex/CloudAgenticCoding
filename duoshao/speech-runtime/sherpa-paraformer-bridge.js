// Browser bridge for the Apache-2.0 sherpa-onnx WebAssembly package.
import createSherpaModule from "./sherpa-onnx-wasm.js";
import { OfflineRecognizer } from "./sherpa-onnx-asr.js";

async function fetchBytes(url, label) {
  const cache = await caches.open("duoshao-paraformer-v3").catch(() => undefined);
  let response = await cache?.match(url).catch(() => undefined);
  const wasCached = Boolean(response);
  if (!response) response = await fetch(url);
  if (!response.ok) throw new Error(`Could not load ${label} (${response.status}): ${url}`);
  const cacheWrite = wasCached || !cache ? Promise.resolve() : cache.put(url, response.clone()).catch(() => undefined);
  const bytes = new Uint8Array(await response.arrayBuffer());
  await cacheWrite;
  return bytes;
}

export async function createParaformerRecognizer({ modelUrl, tokensUrl, onProgress = () => undefined }) {
  const wasmUrl = new URL("./sherpa-onnx-wasm.wasm", import.meta.url);
  onProgress(0.1, "Loading sherpa WebAssembly");
  const wasmBinary = await fetchBytes(wasmUrl.href, "sherpa WebAssembly runtime");
  onProgress(0.25, "Loading Paraformer model");
  const model = await fetchBytes(modelUrl, "Paraformer model");
  onProgress(0.82, "Loading Paraformer tokens");
  const tokens = await fetchBytes(tokensUrl, "Paraformer tokens");

  onProgress(0.92, "Initializing Paraformer");
  const module = await createSherpaModule({ wasmBinary });
  module.FS_createDataFile("/", "model.int8.onnx", model, true, false, true);
  module.FS_createDataFile("/", "tokens.txt", tokens, true, false, true);

  const recognizer = new OfflineRecognizer({
    featConfig: { sampleRate: 16_000, featureDim: 80 },
    modelConfig: {
      paraformer: { model: "/model.int8.onnx" },
      tokens: "/tokens.txt",
      numThreads: 1,
      debug: 0,
      provider: "cpu",
      modelingUnit: "cjkchar",
    },
    decodingMethod: "greedy_search",
    maxActivePaths: 4,
  }, module);

  if (!recognizer.handle) throw new Error("sherpa-onnx could not initialize the Paraformer model.");

  return {
    async recognize(samples, sampleRate) {
      const stream = recognizer.createStream();
      try {
        stream.acceptWaveform(sampleRate, samples);
        recognizer.decode(stream);
        return recognizer.getResult(stream).text ?? "";
      } finally {
        stream.free();
      }
    },
    dispose() {
      recognizer.free();
      module.FS_unlink("/model.int8.onnx");
      module.FS_unlink("/tokens.txt");
    },
  };
}
