import { DifficultyManager, type RandomSource } from "./difficulty";
import type { FallingTarget, GameSnapshot, SpeechAttempt } from "./types";

interface EngineOptions {
  random?: RandomSource;
  pendingTimeoutMs?: number;
  difficulty?: DifficultyManager;
}

export class GameEngine {
  private phase: GameSnapshot["phase"] = "idle";
  private score = 0;
  private hits = 0;
  private targets: FallingTarget[] = [];
  private gameOver: GameSnapshot["gameOver"] = null;
  private pausedAt: number | null = null;
  private attempts = new Map<string, SpeechAttempt>();
  private nextSpawnAt = 0;
  private nextId = 1;
  private usedAmounts = new Set<number>();
  private readonly random: RandomSource;
  private readonly pendingTimeoutMs: number;
  private readonly difficulty: DifficultyManager;

  constructor(options: EngineOptions = {}) {
    this.random = options.random ?? Math.random;
    this.pendingTimeoutMs = options.pendingTimeoutMs ?? 2_500;
    this.difficulty = options.difficulty ?? new DifficultyManager();
  }

  start(now: number): GameSnapshot {
    this.phase = "running";
    this.score = 0;
    this.hits = 0;
    this.targets = [];
    this.gameOver = null;
    this.pausedAt = null;
    this.attempts.clear();
    this.nextId = 1;
    this.usedAmounts.clear();
    this.nextSpawnAt = now;
    this.tick(now);
    return this.snapshot();
  }

  tick(now: number): GameSnapshot {
    if (this.phase !== "running") return this.snapshot();
    if (this.pausedAt !== null) return this.snapshot();
    this.removeExpiredHits(now);

    for (const target of this.targets.filter((item) => item.state !== "hit")) {
      if (target.state === "pending-game-over") {
        if (now >= (target.pendingUntil ?? target.boundaryAt)) this.endGame(target, now);
        continue;
      }
      if (now >= target.boundaryAt) {
        const timelyAttempt = [...this.attempts.values()].some(
          (attempt) => !attempt.resolved && attempt.gameplayStartedAt <= target.boundaryAt,
        );
        if (timelyAttempt) {
          target.state = "pending-game-over";
          target.pendingUntil = now + this.pendingTimeoutMs;
        } else {
          this.endGame(target, now);
        }
      }
      if (this.gameOver) break;
    }

    if (this.phase === "running") this.spawnDueTarget(now);
    return this.snapshot();
  }

  speechStarted(id: string, at: number): void {
    if (this.phase === "running") {
      this.attempts.set(id, { id, speechStartedAt: at, gameplayStartedAt: at, resolved: false });
    }
  }

  speechEnded(id: string, at: number): void {
    const attempt = this.attempts.get(id);
    if (attempt) attempt.speechEndedAt = at;
  }

  pause(at: number): void {
    if (this.phase === "running" && this.pausedAt === null) this.pausedAt = at;
  }

  resume(at: number): void {
    if (this.phase !== "running" || this.pausedAt === null) return;
    const pausedFor = Math.max(0, at - this.pausedAt);
    this.nextSpawnAt += pausedFor;
    for (const target of this.targets) {
      target.spawnedAt += pausedFor;
      target.boundaryAt += pausedFor;
      if (target.hitAt !== undefined) target.hitAt += pausedFor;
      if (target.pendingUntil !== undefined) target.pendingUntil += pausedFor;
    }
    for (const attempt of this.attempts.values()) {
      if (!attempt.resolved && attempt.gameplayStartedAt >= this.pausedAt) {
        attempt.gameplayStartedAt += pausedFor;
      }
    }
    this.pausedAt = null;
  }

  recognitionResult(id: string, amount: number | null, completedAt: number): { hit: boolean } {
    const attempt = this.attempts.get(id);
    if (attempt) {
      attempt.recognitionCompletedAt = completedAt;
      attempt.resolved = true;
    }
    if (this.phase !== "running") return { hit: false };

    const matching = amount === null ? undefined : this.targets.find(
      (target) => target.amount === amount && target.state !== "hit" &&
        (!attempt || attempt.gameplayStartedAt <= target.boundaryAt),
    );
    if (matching) {
      matching.state = "hit";
      matching.hitAt = completedAt;
      this.hits += 1;
      const reactionMs = attempt ? Math.max(0, attempt.gameplayStartedAt - matching.spawnedAt) : matching.fallDurationMs;
      this.score += 100 + Math.max(0, Math.round(50 * (1 - reactionMs / matching.fallDurationMs)));
      this.resolveUnprotectedPending(completedAt);
      this.attempts.delete(id);
      return { hit: true };
    }

    this.resolveUnprotectedPending(completedAt);
    this.attempts.delete(id);
    return { hit: false };
  }

  position(target: FallingTarget, now: number): number {
    const effectiveNow = this.pausedAt === null ? now : Math.min(now, this.pausedAt);
    return Math.min(1, Math.max(0, (effectiveNow - target.spawnedAt) / target.fallDurationMs));
  }

  snapshot(): GameSnapshot {
    return {
      phase: this.phase,
      pausedAt: this.pausedAt,
      score: this.score,
      hits: this.hits,
      level: this.difficulty.settingsForHits(this.hits).level,
      targets: this.targets.map((target) => ({ ...target })),
      gameOver: this.gameOver ? { ...this.gameOver } : null,
    };
  }

  private spawnDueTarget(now: number): void {
    const settings = this.difficulty.settingsForHits(this.hits);
    const active = this.targets.filter((target) => target.state !== "hit").length;
    if (now < this.nextSpawnAt || active >= settings.maxConcurrentTargets) return;
    const amount = this.difficulty.generateUnusedAmount(settings.level, this.random, this.usedAmounts);
    if (amount === null) return;
    const target: FallingTarget = {
      id: `target-${this.nextId++}`,
      amount,
      spawnedAt: now,
      fallDurationMs: settings.fallDurationMs,
      boundaryAt: now + settings.fallDurationMs,
      state: "falling",
    };
    this.targets.push(target);
    this.usedAmounts.add(amount);
    this.nextSpawnAt = now + settings.spawnIntervalMs;
  }

  private removeExpiredHits(now: number): void {
    this.targets = this.targets.filter((target) => target.state !== "hit" || now - (target.hitAt ?? now) < 360);
  }

  private resolveUnprotectedPending(now: number): void {
    for (const target of this.targets.filter((item) => item.state === "pending-game-over")) {
      const stillProtected = [...this.attempts.values()].some(
        (attempt) => !attempt.resolved && attempt.gameplayStartedAt <= target.boundaryAt,
      );
      if (!stillProtected) {
        this.endGame(target, now);
        return;
      }
    }
  }

  private endGame(target: FallingTarget, now: number): void {
    this.phase = "game-over";
    this.gameOver = { missedAmount: target.amount, endedAt: now };
  }
}
