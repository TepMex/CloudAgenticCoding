export type TargetState = "falling" | "hit" | "pending-game-over";

export interface FallingTarget {
  id: string;
  amount: number;
  spawnedAt: number;
  fallDurationMs: number;
  state: TargetState;
  hitAt?: number;
  boundaryAt: number;
  pendingUntil?: number;
}

export interface GameOverState {
  missedAmount: number;
  endedAt: number;
}

export interface GameSnapshot {
  phase: "idle" | "running" | "game-over";
  pausedAt: number | null;
  score: number;
  hits: number;
  level: number;
  targets: FallingTarget[];
  gameOver: GameOverState | null;
}

export interface SpeechAttempt {
  id: string;
  speechStartedAt: number;
  gameplayStartedAt: number;
  speechEndedAt?: number;
  recognitionCompletedAt?: number;
  resolved: boolean;
}
