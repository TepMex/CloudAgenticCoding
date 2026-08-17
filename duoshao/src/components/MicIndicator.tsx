import type { MicrophoneStatus } from "../speech/types";

const LABELS: Record<MicrophoneStatus, string> = {
  loading: "Loading",
  ready: "Ready",
  listening: "Listening",
  "speech-detected": "Speech detected",
  recognizing: "Recognizing",
  error: "Microphone error",
};

export function MicIndicator({ status }: { status: MicrophoneStatus }) {
  return (
    <div className={`mic mic--${status}`} role="status" aria-live="polite">
      <span className="mic__dot" />
      <span>{LABELS[status]}</span>
    </div>
  );
}
