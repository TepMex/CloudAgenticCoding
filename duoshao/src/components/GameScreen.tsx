import type { GameSnapshot } from "../game/types";
import type { MicrophoneStatus } from "../speech/types";
import { FallingMoney } from "./FallingMoney";
import { GameOver } from "./GameOver";
import { Hud } from "./Hud";
import { MockConsole } from "./MockConsole";

export interface TranscriptState { text: string; kind: "hit" | "miss" | "neutral" }

export function GameScreen({ snapshot, now, micStatus, transcript, isMock, onMockSubmit, onPlayAgain }: {
  snapshot: GameSnapshot;
  now: number;
  micStatus: MicrophoneStatus;
  transcript: TranscriptState | null;
  isMock: boolean;
  onMockSubmit(value: string): void;
  onPlayAgain(): void;
}) {
  return (
    <main className={`game-screen${isMock ? " game-screen--mock" : ""}`}>
      <Hud score={snapshot.score} hits={snapshot.hits} level={snapshot.level} micStatus={micStatus} />
      <section className="game-board" aria-label="Falling prices game board">
        <div className="game-board__grid" />
        <div className="level-chip">LEVEL {snapshot.level}</div>
        {snapshot.targets.map((target) => <FallingMoney key={target.id} target={target} now={now} />)}
        <div className="danger-line"><span>TOO LATE</span></div>
        {transcript && <div className={`transcript transcript--${transcript.kind}`}>{transcript.kind === "hit" ? "✓" : transcript.kind === "miss" ? "×" : "…"} {transcript.text}</div>}
      </section>
      {isMock && snapshot.phase === "running" && <MockConsole onSubmit={onMockSubmit} />}
      {snapshot.phase === "game-over" && snapshot.gameOver && (
        <GameOver score={snapshot.score} hits={snapshot.hits} missedAmount={snapshot.gameOver.missedAmount} onPlayAgain={onPlayAgain} />
      )}
    </main>
  );
}
