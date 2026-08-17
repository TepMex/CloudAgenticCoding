export type MicrophoneStatus = "loading" | "ready" | "listening" | "speech-detected" | "recognizing" | "error";

export interface SpeechTimingEvent {
  utteranceId: string;
  speechStartedAt: number;
  speechEndedAt?: number;
}

export interface SpeechRecognitionResult extends SpeechTimingEvent {
  transcript: string;
  recognitionCompletedAt: number;
  utteranceDurationMs: number;
  inferenceDurationMs: number;
  resultLatencyMs: number;
}

export interface SpeechRecognizer {
  initialize(onProgress?: (progress: number, label: string) => void): Promise<void>;
  start(): Promise<void>;
  stop(): Promise<void>;
  dispose(): Promise<void>;
  onSpeechStart?: (event: SpeechTimingEvent) => void;
  onSpeechEnd?: (event: SpeechTimingEvent) => void;
  onResult?: (result: SpeechRecognitionResult) => void;
  onError?: (error: Error) => void;
  onStatusChange?: (status: MicrophoneStatus) => void;
}
