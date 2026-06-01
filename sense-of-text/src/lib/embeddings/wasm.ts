import { minMaxNormalize } from "@/lib/math";
import { deleteToken, type Token } from "@/lib/tokenize";

type ProgressCallback = (current: number, total: number, label: string) => void;

type WorkerInbound =
  | { type: "ready" }
  | { type: "loaded" }
  | { type: "progress"; current: number; total: number }
  | { type: "analyze-result"; distances: number[] }
  | { type: "error"; message: string };

/** Served by Bun at `/embeddings-worker.js` (see src/index.ts). */
const WORKER_SCRIPT_URL = "/embeddings-worker.js";

let worker: Worker | null = null;
let workerReady = false;

function getWorker(): Worker {
  if (!worker) {
    worker = new Worker(WORKER_SCRIPT_URL, { type: "module" });
  }
  return worker;
}

function waitForReady(w: Worker, signal?: AbortSignal): Promise<void> {
  return new Promise((resolve, reject) => {
    const onAbort = () => {
      cleanup();
      reject(new DOMException("Aborted", "AbortError"));
    };
    const onMessage = (e: MessageEvent<WorkerInbound>) => {
      if (e.data.type === "ready") {
        workerReady = true;
        cleanup();
        resolve();
      }
      if (e.data.type === "error") {
        cleanup();
        reject(new Error(e.data.message));
      }
    };
    const cleanup = () => {
      w.removeEventListener("message", onMessage);
      signal?.removeEventListener("abort", onAbort);
    };
    w.addEventListener("message", onMessage);
    signal?.addEventListener("abort", onAbort);
    if (workerReady) {
      cleanup();
      resolve();
    }
  });
}

function loadModel(w: Worker, modelId: string, signal?: AbortSignal): Promise<void> {
  return new Promise((resolve, reject) => {
    const onAbort = () => {
      cleanup();
      reject(new DOMException("Aborted", "AbortError"));
    };
    const onMessage = (e: MessageEvent<WorkerInbound>) => {
      if (e.data.type === "loaded") {
        cleanup();
        resolve();
      }
      if (e.data.type === "error") {
        cleanup();
        reject(new Error(e.data.message));
      }
    };
    const cleanup = () => {
      w.removeEventListener("message", onMessage);
      signal?.removeEventListener("abort", onAbort);
    };
    w.addEventListener("message", onMessage);
    signal?.addEventListener("abort", onAbort);
    w.postMessage({ type: "load", modelId });
  });
}

export async function analyzeWithEmbeddings(
  text: string,
  tokens: Token[],
  modelId: string,
  onProgress: ProgressCallback,
  signal?: AbortSignal,
): Promise<number[]> {
  const w = getWorker();
  await waitForReady(w, signal);
  onProgress(0, tokens.length + 1, "Loading model…");
  await loadModel(w, modelId, signal);

  const deletions = tokens.map((_, i) => deleteToken(text, tokens, i));

  return new Promise((resolve, reject) => {
    const onAbort = () => {
      cleanup();
      reject(new DOMException("Aborted", "AbortError"));
    };
    const onMessage = (e: MessageEvent<WorkerInbound>) => {
      if (e.data.type === "progress") {
        onProgress(
          e.data.current,
          e.data.total,
          `Embedding ${e.data.current}/${e.data.total}…`,
        );
      }
      if (e.data.type === "analyze-result") {
        cleanup();
        resolve(minMaxNormalize(e.data.distances));
      }
      if (e.data.type === "error") {
        cleanup();
        reject(new Error(e.data.message));
      }
    };
    const cleanup = () => {
      w.removeEventListener("message", onMessage);
      signal?.removeEventListener("abort", onAbort);
    };
    w.addEventListener("message", onMessage);
    signal?.addEventListener("abort", onAbort);
    w.postMessage({ type: "analyze", text, deletions });
  });
}

export function terminateEmbeddingWorker(): void {
  worker?.terminate();
  worker = null;
  workerReady = false;
}
