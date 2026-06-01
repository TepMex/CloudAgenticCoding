import { pipeline, env } from "@huggingface/transformers";

env.allowLocalModels = false;
env.useBrowserCache = true;

type WorkerRequest =
  | { type: "load"; modelId: string }
  | { type: "embed"; text: string; requestId: number }
  | { type: "analyze"; text: string; deletions: string[] };

type WorkerResponse =
  | { type: "ready" }
  | { type: "loaded" }
  | { type: "embed-result"; requestId: number; embedding: number[] }
  | { type: "progress"; current: number; total: number }
  | { type: "analyze-result"; distances: number[] }
  | { type: "error"; message: string };

// eslint-disable-next-line @typescript-eslint/no-explicit-any
let extractor: any = null;
let currentModelId: string | null = null;

async function loadModel(modelId: string) {
  if (extractor && currentModelId === modelId) return;
  extractor = await pipeline("feature-extraction", modelId, {
    dtype: "fp32",
  });
  currentModelId = modelId;
}

async function embedText(text: string): Promise<number[]> {
  if (!extractor) throw new Error("Model not loaded");
  const output = await extractor(text, { pooling: "mean", normalize: true });
  return Array.from(output.data as Float32Array);
}

function cosineDistance(a: number[], b: number[]): number {
  let dot = 0;
  let normA = 0;
  let normB = 0;
  for (let i = 0; i < a.length; i++) {
    const ai = a[i] ?? 0;
    const bi = b[i] ?? 0;
    dot += ai * bi;
    normA += ai * ai;
    normB += bi * bi;
  }
  const denom = Math.sqrt(normA) * Math.sqrt(normB);
  const sim = denom === 0 ? 0 : dot / denom;
  return 1 - sim;
}

self.onmessage = async (event: MessageEvent<WorkerRequest>) => {
  const msg = event.data;
  try {
    if (msg.type === "load") {
      await loadModel(msg.modelId);
      self.postMessage({ type: "loaded" } satisfies WorkerResponse);
      return;
    }

    if (msg.type === "embed") {
      const embedding = await embedText(msg.text);
      self.postMessage({
        type: "embed-result",
        requestId: msg.requestId,
        embedding,
      } satisfies WorkerResponse);
      return;
    }

    if (msg.type === "analyze") {
      const total = msg.deletions.length + 1;
      self.postMessage({ type: "progress", current: 1, total } satisfies WorkerResponse);
      const e0 = await embedText(msg.text);
      const distances: number[] = [];

      for (let i = 0; i < msg.deletions.length; i++) {
        if ((self as unknown as { cancelled?: boolean }).cancelled) return;
        const deleted = msg.deletions[i]!;
        const ei = await embedText(deleted);
        distances.push(cosineDistance(e0, ei));
        self.postMessage({
          type: "progress",
          current: i + 2,
          total,
        } satisfies WorkerResponse);
      }

      self.postMessage({
        type: "analyze-result",
        distances,
      } satisfies WorkerResponse);
    }
  } catch (err) {
    self.postMessage({
      type: "error",
      message: err instanceof Error ? err.message : String(err),
    } satisfies WorkerResponse);
  }
};

self.postMessage({ type: "ready" } satisfies WorkerResponse);
