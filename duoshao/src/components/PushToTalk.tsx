import { useRef, type KeyboardEvent, type PointerEvent } from "react";

export function PushToTalk({ canStart, recording, onStart, onEnd }: {
  canStart: boolean;
  recording: boolean;
  onStart(): void;
  onEnd(): void;
}) {
  const pointerId = useRef<number | null>(null);
  const keyboardActive = useRef(false);

  const startPointer = (event: PointerEvent<HTMLButtonElement>) => {
    if (!canStart || pointerId.current !== null || (event.pointerType === "mouse" && event.button !== 0)) return;
    event.preventDefault();
    pointerId.current = event.pointerId;
    event.currentTarget.setPointerCapture(event.pointerId);
    onStart();
  };

  const endPointer = (event: PointerEvent<HTMLButtonElement>) => {
    if (pointerId.current !== event.pointerId) return;
    pointerId.current = null;
    onEnd();
  };

  const startKeyboard = (event: KeyboardEvent<HTMLButtonElement>) => {
    if (!canStart || event.repeat || keyboardActive.current || (event.key !== " " && event.key !== "Enter")) return;
    event.preventDefault();
    keyboardActive.current = true;
    onStart();
  };

  const endKeyboard = (event: KeyboardEvent<HTMLButtonElement>) => {
    if (!keyboardActive.current || (event.key !== " " && event.key !== "Enter")) return;
    event.preventDefault();
    keyboardActive.current = false;
    onEnd();
  };

  const endKeyboardOnBlur = () => {
    if (!keyboardActive.current) return;
    keyboardActive.current = false;
    onEnd();
  };

  return (
    <button
      className={`push-to-talk${recording ? " push-to-talk--recording" : ""}`}
      type="button"
      disabled={!canStart && !recording}
      aria-label={recording ? "Release to recognize speech" : "Hold to talk"}
      aria-pressed={recording}
      onPointerDown={startPointer}
      onPointerUp={endPointer}
      onPointerCancel={endPointer}
      onLostPointerCapture={endPointer}
      onKeyDown={startKeyboard}
      onKeyUp={endKeyboard}
      onBlur={endKeyboardOnBlur}
      onContextMenu={(event) => event.preventDefault()}
    >
      <span className="push-to-talk__icon" aria-hidden="true">●</span>
      <span>{recording ? "Release" : canStart ? "Hold to talk" : "Recognizing…"}</span>
    </button>
  );
}
