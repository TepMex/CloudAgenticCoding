import { MockSpeechRecognizer } from "./MockSpeechRecognizer";
import { ParaformerSpeechRecognizer } from "./ParaformerSpeechRecognizer";
import type { SpeechRecognizer } from "./types";

export interface RecognizerSelection {
  recognizer: SpeechRecognizer;
  mock: MockSpeechRecognizer | null;
  mode: "mock" | "paraformer";
}

export function createSpeechRecognizer(search = window.location.search): RecognizerSelection {
  const params = new URLSearchParams(search);
  if (params.get("stt") === "mock") {
    const latency = Math.max(0, Math.min(10_000, Number(params.get("latency")) || 0));
    const mock = new MockSpeechRecognizer(latency);
    return { recognizer: mock, mock, mode: "mock" };
  }
  return { recognizer: new ParaformerSpeechRecognizer(), mock: null, mode: "paraformer" };
}
