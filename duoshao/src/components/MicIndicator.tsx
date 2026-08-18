import type { MicrophoneStatus } from "../speech/types";

const LABELS: Record<MicrophoneStatus, string> = {
  loading: "Loading",
  ready: "Ready",
  idle: "Hold the button to talk",
  recording: "Recording",
  recognizing: "Recognizing",
  error: "Microphone error",
};

export function MicIndicator({ status, isMock = false }: { status: MicrophoneStatus; isMock?: boolean }) {
  return (
    <div className={`mic mic--${status}`} role="status" aria-live="polite">
      <span className="mic__dot" />
      <span>{isMock && status === "idle" ? "Mock input ready" : LABELS[status]}</span>
    </div>
  );
}
