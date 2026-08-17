import { describe, expect, test } from "bun:test";
import { DifficultyManager, type DifficultySettings } from "../src/game/difficulty";
import { GameEngine } from "../src/game/engine";

class TestDifficulty extends DifficultyManager {
  private amount = 20;
  override settingsForHits(hits: number): DifficultySettings {
    return { level: Math.min(6, Math.floor(hits / 2) + 1), fallDurationMs: 1_000, spawnIntervalMs: 200, maxConcurrentTargets: 3 };
  }
  override generateAmount(): number { return this.amount++; }
}

describe("GameEngine", () => {
  test("starts by spawning one target", () => {
    const engine = new GameEngine({ random: () => 0 });
    const state = engine.start(0);
    expect(state.phase).toBe("running");
    expect(state.targets).toHaveLength(1);
    expect(state.targets[0].amount).toBe(7);
  });

  test("spawns without duplicate active amounts", () => {
    const engine = new GameEngine({ difficulty: new TestDifficulty() });
    engine.start(0);
    const state = engine.tick(200);
    const amounts = state.targets.map((target) => target.amount);
    expect(amounts).toHaveLength(2);
    expect(new Set(amounts).size).toBe(amounts.length);
  });

  test("a matching result hits a target and awards score", () => {
    const engine = new GameEngine();
    engine.start(0);
    engine.speechStarted("answer", 200);
    engine.speechEnded("answer", 500);
    expect(engine.recognitionResult("answer", 7, 700)).toEqual({ hit: true });
    const state = engine.snapshot();
    expect(state.hits).toBe(1);
    expect(state.score).toBeGreaterThanOrEqual(100);
    expect(state.targets[0].state).toBe("hit");
  });

  test("a miss does not alter targets or score", () => {
    const engine = new GameEngine();
    engine.start(0);
    engine.speechStarted("answer", 200);
    expect(engine.recognitionResult("answer", 8, 300)).toEqual({ hit: false });
    expect(engine.snapshot().targets[0].state).toBe("falling");
    expect(engine.snapshot().score).toBe(0);
  });

  test("crossing the bottom causes sudden-death game over", () => {
    const engine = new GameEngine();
    engine.start(0);
    const state = engine.tick(12_001);
    expect(state.phase).toBe("game-over");
    expect(state.gameOver?.missedAmount).toBe(7);
  });

  test("speech started before boundary protects a late correct result", () => {
    const engine = new GameEngine({ pendingTimeoutMs: 2_500 });
    engine.start(0);
    engine.speechStarted("answer", 11_900);
    engine.speechEnded("answer", 12_100);
    expect(engine.tick(12_200).targets[0].state).toBe("pending-game-over");
    expect(engine.recognitionResult("answer", 7, 12_800)).toEqual({ hit: true });
    expect(engine.snapshot().phase).toBe("running");
  });

  test("a late incorrect result resolves pending state as game over", () => {
    const engine = new GameEngine({ pendingTimeoutMs: 2_500 });
    engine.start(0);
    engine.speechStarted("answer", 11_900);
    engine.tick(12_100);
    engine.recognitionResult("answer", 8, 12_400);
    expect(engine.snapshot().phase).toBe("game-over");
    expect(engine.snapshot().gameOver?.missedAmount).toBe(7);
  });

  test("speech started after boundary cannot rescue the target", () => {
    const engine = new GameEngine();
    engine.start(0);
    engine.speechStarted("late", 12_010);
    expect(engine.tick(12_020).phase).toBe("game-over");
  });

  test("a stuck recognition request times out", () => {
    const engine = new GameEngine({ pendingTimeoutMs: 500 });
    engine.start(0);
    engine.speechStarted("stuck", 11_900);
    expect(engine.tick(12_000).phase).toBe("running");
    expect(engine.tick(12_499).phase).toBe("running");
    expect(engine.tick(12_500).phase).toBe("game-over");
  });

  test("time-based position does not depend on frame count", () => {
    const engine = new GameEngine();
    const target = engine.start(100).targets[0];
    expect(engine.position(target, 100)).toBe(0);
    expect(engine.position(target, 6_100)).toBeCloseTo(0.5);
    expect(engine.position(target, 12_100)).toBe(1);
  });

  test("start fully resets a completed run", () => {
    const engine = new GameEngine();
    engine.start(0);
    engine.tick(12_001);
    const reset = engine.start(20_000);
    expect(reset.phase).toBe("running");
    expect(reset.score).toBe(0);
    expect(reset.hits).toBe(0);
    expect(reset.gameOver).toBeNull();
    expect(reset.targets).toHaveLength(1);
  });
});
