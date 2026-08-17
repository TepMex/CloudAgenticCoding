import type { ParaformerConfig } from "../config";

interface SherpaBridge {
  createParaformerRecognizer(options: {
    modelUrl: string;
    tokensUrl: string;
    modelConfigUrl?: string;
    onProgress?: (progress: number, label: string) => void;
  }): Promise<{ recognize(samples: Float32Array, sampleRate: number): Promise<string>; dispose(): void | Promise<void> }>;
}

let recognizer: Awaited<ReturnType<SherpaBridge["createParaformerRecognizer"]>> | undefined;

function send(message: unknown): void {
  self.postMessage(message);
}

async function validateRuntime(url: string): Promise<void> {
  send({ type: "progress", progress: 0.05, label: "Loading sherpa runtime" });
  const response = await fetch(url);
  if (!response.ok) throw new Error(`Could not download sherpa runtime (${response.status}): ${url}`);
  const contentType = response.headers.get("content-type")?.toLowerCase() ?? "";
  if (!contentType.includes("javascript") && !contentType.includes("ecmascript")) {
    throw new Error(`Sherpa runtime bridge is missing or is not JavaScript: ${url}. Run bun run setup:speech.`);
  }
}

self.addEventListener("message", async (event: MessageEvent) => {
  try {
    if (event.data.type === "initialize") {
      const config = event.data.config as ParaformerConfig;
      await validateRuntime(config.runtimeUrl);
      const runtime = await import(/* @vite-ignore */ config.runtimeUrl) as unknown as SherpaBridge;
      if (typeof runtime.createParaformerRecognizer !== "function") {
        throw new Error("The sherpa runtime bridge does not export createParaformerRecognizer(). See README.md.");
      }
      recognizer = await runtime.createParaformerRecognizer({
        ...config,
        onProgress: (progress, label) => send({ type: "progress", progress, label }),
      });
      send({ type: "progress", progress: 1, label: "Speech model ready" });
      send({ type: "ready" });
    }
    if (event.data.type === "recognize") {
      if (!recognizer) throw new Error("Speech recognizer is not initialized.");
      const startedAt = performance.now();
      const transcript = await recognizer.recognize(event.data.samples, event.data.sampleRate);
      send({ type: "result", id: event.data.id, transcript, inferenceDurationMs: performance.now() - startedAt });
    }
    if (event.data.type === "dispose") {
      await recognizer?.dispose();
      recognizer = undefined;
    }
  } catch (reason) {
    send({ type: "error", message: reason instanceof Error ? reason.message : "Unknown speech worker error" });
  }
});
