interface StartScreenProps {
  progress: number;
  progressLabel: string;
  ready: boolean;
  error: string | null;
  mode: "mock" | "paraformer";
  onStart(): void;
  onRetry(): void;
}

export function StartScreen({ progress, progressLabel, ready, error, mode, onStart, onRetry }: StartScreenProps) {
  return (
    <main className="start-screen">
      <div className="start-screen__halo" />
      <section className="start-card">
        <div className="brand-mark" aria-hidden="true">¥</div>
        <p className="eyebrow">Chinese number reflexes</p>
        <h1>DuoShaoGame</h1>
        <div className="chinese-title">多少</div>
        <p className="intro">{mode === "mock"
          ? "Enter the falling price in Chinese before it reaches the bottom."
          : "Hold the talk button, say the falling price in Chinese, then release."}</p>

        <div className="model-status" role="status">
          <div className="model-status__row">
            <span>{error ? "Speech setup failed" : progressLabel}</span>
            {!error && <strong>{Math.round(progress * 100)}%</strong>}
          </div>
          <div className="progress"><span style={{ width: `${progress * 100}%` }} /></div>
          <small>{mode === "mock" ? "Developer mock mode · no microphone" : "Runs locally · audio never leaves this device"}</small>
        </div>

        {error && <div className="error-box">{error}</div>}
        {error ? (
          <button className="primary-button" onClick={onRetry}>Retry setup</button>
        ) : (
          <button className="primary-button" disabled={!ready} onClick={onStart}>{ready ? "Start game" : "Preparing speech…"}</button>
        )}
        <p className="hint">Try <b>七元</b> for ¥7. One miss ends the run.</p>
      </section>
    </main>
  );
}
