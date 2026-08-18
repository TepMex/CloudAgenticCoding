import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { parseChineseMoney } from "../chinese/parseChineseMoney";
import { GameScreen, type TranscriptState } from "../components/GameScreen";
import { StartScreen } from "../components/StartScreen";
import { GameEngine } from "../game/engine";
import type { GameSnapshot } from "../game/types";
import { createSpeechRecognizer } from "../speech/createSpeechRecognizer";
import type { MicrophoneStatus } from "../speech/types";

type AppScreen = "start" | "game";

export function App() {
  const [bootKey, setBootKey] = useState(0);
  const speech = useMemo(() => createSpeechRecognizer(), [bootKey]);
  const engine = useRef(new GameEngine());
  const [screen, setScreen] = useState<AppScreen>("start");
  const [snapshot, setSnapshot] = useState<GameSnapshot>(() => engine.current.snapshot());
  const [now, setNow] = useState(() => performance.now());
  const [progress, setProgress] = useState(0);
  const [progressLabel, setProgressLabel] = useState("Loading speech model");
  const [ready, setReady] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [micStatus, setMicStatus] = useState<MicrophoneStatus>("loading");
  const [transcript, setTranscript] = useState<TranscriptState | null>(null);
  const stoppedForGameOver = useRef(false);

  useEffect(() => {
    let active = true;
    setReady(false);
    setError(null);
    setProgress(0);
    speech.recognizer.onStatusChange = (status) => active && setMicStatus(status);
    speech.recognizer.onError = (reason) => active && setError(reason.message);
    speech.recognizer.onSpeechStart = (event) => engine.current.speechStarted(event.utteranceId, event.speechStartedAt);
    speech.recognizer.onSpeechEnd = (event) => engine.current.speechEnded(event.utteranceId, event.speechEndedAt ?? performance.now());
    speech.recognizer.onResult = (result) => {
      const answer = parseChineseMoney(result.transcript);
      const outcome = engine.current.recognitionResult(result.utteranceId, answer?.amount ?? null, result.recognitionCompletedAt);
      setTranscript({ text: result.transcript, kind: outcome.hit ? "hit" : "miss" });
      setSnapshot(engine.current.snapshot());
    };
    speech.recognizer.initialize((value, label) => {
      if (!active) return;
      setProgress(value);
      setProgressLabel(label);
    }).then(() => {
      if (active) setReady(true);
    }).catch((reason: unknown) => {
      if (active) {
        setMicStatus("error");
        setError(reason instanceof Error ? reason.message : "Speech setup failed.");
      }
    });
    return () => {
      active = false;
      void speech.recognizer.dispose();
    };
  }, [speech]);

  useEffect(() => {
    if (screen !== "game" || snapshot.phase !== "running") return;
    let frame = 0;
    let lastUpdate = 0;
    const update = (timestamp: number) => {
      if (timestamp - lastUpdate >= 50) {
        const next = engine.current.tick(timestamp);
        setSnapshot(next);
        setNow(timestamp);
        lastUpdate = timestamp;
      }
      frame = requestAnimationFrame(update);
    };
    frame = requestAnimationFrame(update);
    return () => cancelAnimationFrame(frame);
  }, [screen, snapshot.phase]);

  useEffect(() => {
    if (snapshot.phase !== "game-over" || stoppedForGameOver.current) return;
    stoppedForGameOver.current = true;
    void speech.recognizer.stop();
  }, [snapshot.phase, speech]);

  const start = useCallback(async () => {
    setError(null);
    setTranscript(null);
    try {
      await speech.recognizer.start();
      const timestamp = performance.now();
      stoppedForGameOver.current = false;
      setSnapshot(engine.current.start(timestamp));
      setNow(timestamp);
      setScreen("game");
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : "Could not start the game.");
    }
  }, [speech]);

  const playAgain = useCallback(async () => {
    setTranscript(null);
    try {
      await speech.recognizer.start();
      const timestamp = performance.now();
      stoppedForGameOver.current = false;
      setSnapshot(engine.current.start(timestamp));
      setNow(timestamp);
    } catch (reason) {
      setScreen("start");
      setError(reason instanceof Error ? reason.message : "Could not restart the game.");
    }
  }, [speech]);

  if (screen === "start") {
    return <StartScreen progress={progress} progressLabel={progressLabel} ready={ready} error={error} mode={speech.mode} onStart={start} onRetry={() => setBootKey((value) => value + 1)} />;
  }
  return (
    <GameScreen
      snapshot={snapshot}
      now={now}
      micStatus={micStatus}
      transcript={transcript}
      isMock={speech.mode === "mock"}
      onMockSubmit={(value) => speech.mock?.submit(value)}
      onTalkStart={() => speech.recognizer.beginUtterance()}
      onTalkEnd={() => speech.recognizer.endUtterance()}
      onPlayAgain={playAgain}
    />
  );
}
