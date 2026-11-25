import {
  Backend,
  EmbeddingEngine,
  InputOverflowStrategy,
  loadLiteRtLm,
} from "@litert-lm/core";
import { appUrl } from "./app-url.ts";
import { EMBEDDING_CONTRACT } from "./contract.ts";

const CACHE = "ffmpeg-commands-model-v1";

export type Embedder = {
  embed(text: string): Promise<Float32Array>;
  backend: string;
};

async function cachedModelBlob(
  url: string,
  onProgress: (loaded: number, total: number) => void,
): Promise<Blob> {
  const cache = await caches.open(CACHE);
  const cached = await cache.match(url);
  if (cached) {
    const blob = await cached.blob();
    onProgress(blob.size, blob.size || EMBEDDING_CONTRACT.approximateBytes);
    return blob;
  }

  const response = await fetch(url);
  if (!response.ok || !response.body) {
    throw new Error(`Не удалось скачать модель (${response.status}). Нужен доступ к huggingface.co.`);
  }
  const total = Number(response.headers.get("content-length") || EMBEDDING_CONTRACT.approximateBytes);
  const reader = response.body.getReader();
  const parts: Uint8Array[] = [];
  let loaded = 0;
  while (true) {
    const step = await reader.read();
    if (step.done) break;
    if (!step.value) continue;
    parts.push(step.value);
    loaded += step.value.byteLength;
    onProgress(loaded, total);
  }
  const blob = new Blob(parts, { type: "application/octet-stream" });
  await cache.put(
    url,
    new Response(blob, {
      headers: {
        "Content-Type": "application/octet-stream",
        "Content-Length": String(blob.size),
      },
    }),
  );
  onProgress(blob.size, blob.size);
  return blob;
}

async function openEngine(model: Blob, backend: Backend): Promise<EmbeddingEngine> {
  return EmbeddingEngine.create({
    model,
    backend,
    maxInputLength: EMBEDDING_CONTRACT.maxInputLength,
  });
}

export async function loadEmbedder(onProgress: (loaded: number, total: number) => void): Promise<Embedder> {
  await loadLiteRtLm(appUrl("/vendor/litertlm/"));
  const model = await cachedModelBlob(EMBEDDING_CONTRACT.modelUrl, onProgress);
  let engine: EmbeddingEngine;
  let backendName = "GPU";
  try {
    engine = await openEngine(model, Backend.GPU);
  } catch (gpuError) {
    console.warn("GPU embedder failed, trying CPU", gpuError);
    backendName = "CPU";
    engine = await openEngine(model, Backend.CPU);
  }

  return {
    backend: backendName,
    async embed(text: string) {
      const response = await engine.computeEmbedding(text, {
        normalize: true,
        outputSize: EMBEDDING_CONTRACT.outputSize,
        inputOverflowStrategy: InputOverflowStrategy.TRUNCATE,
      });
      return Float32Array.from(response.embedding);
    },
  };
}
