import type { SpeechRecognizer, SpeechRecognitionResult, SpeechTimingEvent } from "./types";

export class MockSpeechRecognizer implements SpeechRecognizer {
  onSpeechStart?: SpeechRecognizer["onSpeechStart"];
  onSpeechEnd?: SpeechRecognizer["onSpeechEnd"];
  onResult?: SpeechRecognizer["onResult"];
  onError?: SpeechRecognizer["onError"];
  onStatusChange?: SpeechRecognizer["onStatusChange"];
  private sequence = 0;
  private started = false;

  constructor(private readonly latencyMs = 0) {}

  async initialize(onProgress?: (progress: number, label: string) => void): Promise<void> {
    this.onStatusChange?.("loading");
    onProgress?.(1, "Mock recognizer ready");
    this.onStatusChange?.("ready");
  }

  async start(): Promise<void> {
    this.started = true;
    this.onStatusChange?.("idle");
  }

  beginUtterance(): void {}

  endUtterance(): void {}

  async stop(): Promise<void> {
    this.started = false;
    this.onStatusChange?.("ready");
  }

  async dispose(): Promise<void> {
    this.started = false;
  }

  submit(transcript: string): void {
    if (!this.started || !transcript.trim()) return;
    const utteranceId = `mock-${++this.sequence}`;
    const speechStartedAt = performance.now();
    const speechEndedAt = speechStartedAt + 80;
    const timing: SpeechTimingEvent = { utteranceId, speechStartedAt, speechEndedAt };
    this.onStatusChange?.("recording");
    this.onSpeechStart?.(timing);
    this.onSpeechEnd?.(timing);
    this.onStatusChange?.("recognizing");
    window.setTimeout(() => {
      if (!this.started) return;
      const recognitionCompletedAt = performance.now();
      const result: SpeechRecognitionResult = {
        ...timing,
        transcript,
        recognitionCompletedAt,
        utteranceDurationMs: 80,
        inferenceDurationMs: Math.max(0, recognitionCompletedAt - speechEndedAt),
        resultLatencyMs: Math.max(0, recognitionCompletedAt - speechEndedAt),
      };
      this.onResult?.(result);
      this.onStatusChange?.("idle");
    }, this.latencyMs);
  }
}
