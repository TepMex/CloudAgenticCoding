import { MicrophoneCapture } from "../audio/microphone";
import { readParaformerConfig, type ParaformerConfig } from "./config";
import type { SpeechRecognizer, SpeechRecognitionResult } from "./types";

type WorkerMessage =
  | { type: "progress"; progress: number; label: string }
  | { type: "ready" }
  | { type: "result"; id: string; transcript: string; inferenceDurationMs: number }
  | { type: "error"; message: string };

export class ParaformerSpeechRecognizer implements SpeechRecognizer {
  onSpeechStart?: SpeechRecognizer["onSpeechStart"];
  onSpeechEnd?: SpeechRecognizer["onSpeechEnd"];
  onResult?: SpeechRecognizer["onResult"];
  onError?: SpeechRecognizer["onError"];
  onStatusChange?: SpeechRecognizer["onStatusChange"];
  private worker?: Worker;
  private microphone?: MicrophoneCapture;
  private progress?: (progress: number, label: string) => void;
  private pending = new Map<string, { speechStartedAt: number; speechEndedAt: number }>();
  private sequence = 0;
  private readyPromise?: Promise<void>;

  async initialize(onProgress?: (progress: number, label: string) => void): Promise<void> {
    this.progress = onProgress;
    this.onStatusChange?.("loading");
    const config = readParaformerConfig();
    this.worker = new Worker(new URL("./worker/speech.worker.ts", import.meta.url), { type: "module" });
    this.readyPromise = new Promise<void>((resolve, reject) => {
      const onReady = (event: MessageEvent<WorkerMessage>) => {
        if (event.data.type === "ready") {
          this.worker?.removeEventListener("message", onReady);
          resolve();
        } else if (event.data.type === "error") {
          this.worker?.removeEventListener("message", onReady);
          reject(new Error(event.data.message));
        }
      };
      this.worker?.addEventListener("message", onReady);
    });
    this.worker.addEventListener("message", (event: MessageEvent<WorkerMessage>) => this.handleWorkerMessage(event.data));
    this.worker.addEventListener("error", () => this.fail(new Error("The speech worker crashed.")));
    this.worker.postMessage({ type: "initialize", config: config satisfies ParaformerConfig });
    await this.readyPromise;
    this.onStatusChange?.("ready");
  }

  async start(): Promise<void> {
    await this.readyPromise;
    this.microphone = new MicrophoneCapture({
      onSpeechStart: (speechStartedAt) => {
        const utteranceId = `speech-${++this.sequence}`;
        this.pending.set(utteranceId, { speechStartedAt, speechEndedAt: 0 });
        this.onStatusChange?.("speech-detected");
        this.onSpeechStart?.({ utteranceId, speechStartedAt });
      },
      onSpeechEnd: (samples, speechStartedAt, speechEndedAt, sampleRate) => {
        const entry = [...this.pending.entries()].find(([, item]) => item.speechStartedAt === speechStartedAt);
        if (!entry || !this.worker) return;
        const [id, timing] = entry;
        timing.speechEndedAt = speechEndedAt;
        this.onSpeechEnd?.({ utteranceId: id, speechStartedAt, speechEndedAt });
        this.onStatusChange?.("recognizing");
        this.worker.postMessage({ type: "recognize", id, samples, sampleRate }, [samples.buffer]);
      },
      onError: (error) => this.fail(error),
    });
    try {
      await this.microphone.start();
      this.onStatusChange?.("listening");
    } catch (reason) {
      const error = reason instanceof Error ? reason : new Error("The microphone could not be started.");
      this.fail(error);
      throw error;
    }
  }

  async stop(): Promise<void> {
    await this.microphone?.stop();
    this.microphone = undefined;
    this.pending.clear();
    this.onStatusChange?.("ready");
  }

  async dispose(): Promise<void> {
    await this.stop();
    this.worker?.postMessage({ type: "dispose" });
    this.worker?.terminate();
    this.worker = undefined;
  }

  private handleWorkerMessage(message: WorkerMessage): void {
    if (message.type === "progress") this.progress?.(message.progress, message.label);
    if (message.type === "result") {
      const timing = this.pending.get(message.id);
      if (!timing) return;
      const recognitionCompletedAt = performance.now();
      const result: SpeechRecognitionResult = {
        utteranceId: message.id,
        transcript: message.transcript,
        speechStartedAt: timing.speechStartedAt,
        speechEndedAt: timing.speechEndedAt,
        recognitionCompletedAt,
        utteranceDurationMs: timing.speechEndedAt - timing.speechStartedAt,
        inferenceDurationMs: message.inferenceDurationMs,
        resultLatencyMs: recognitionCompletedAt - timing.speechEndedAt,
      };
      this.pending.delete(message.id);
      this.onResult?.(result);
      this.onStatusChange?.("listening");
      if (import.meta.env.DEV) console.debug("[speech metrics]", result);
    }
    if (message.type === "error") this.fail(new Error(message.message));
  }

  private fail(error: Error): void {
    this.onStatusChange?.("error");
    this.onError?.(error);
  }
}
