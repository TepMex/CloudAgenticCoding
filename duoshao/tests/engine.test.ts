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
    expect(state.targets[0].amount).toBe(1);
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
    const amount = engine.start(0).targets[0].amount;
    engine.speechStarted("answer", 200);
    engine.speechEnded("answer", 500);
    expect(engine.recognitionResult("answer", amount, 700)).toEqual({ hit: true });
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
    const amount = engine.start(0).targets[0].amount;
    const state = engine.tick(12_001);
    expect(state.phase).toBe("running");
    const gameOver = engine.tick(24_001);
    expect(gameOver.phase).toBe("game-over");
    expect(gameOver.gameOver?.missedAmount).toBe(amount);
  });

  test("speech started before boundary protects a late correct result", () => {
    const engine = new GameEngine({ pendingTimeoutMs: 2_500 });
    const amount = engine.start(0).targets[0].amount;
    engine.speechStarted("answer", 23_900);
    engine.speechEnded("answer", 24_100);
    expect(engine.tick(24_200).targets[0].state).toBe("pending-game-over");
    expect(engine.recognitionResult("answer", amount, 24_800)).toEqual({ hit: true });
    expect(engine.snapshot().phase).toBe("running");
  });

  test("a late incorrect result resolves pending state as game over", () => {
    const engine = new GameEngine({ pendingTimeoutMs: 2_500 });
    const amount = engine.start(0).targets[0].amount;
    engine.speechStarted("answer", 23_900);
    engine.tick(24_100);
    engine.recognitionResult("answer", amount === 8 ? 9 : 8, 24_400);
    expect(engine.snapshot().phase).toBe("game-over");
    expect(engine.snapshot().gameOver?.missedAmount).toBe(amount);
  });

  test("speech started after boundary cannot rescue the target", () => {
    const engine = new GameEngine();
    engine.start(0);
    engine.speechStarted("late", 24_010);
    expect(engine.tick(24_020).phase).toBe("game-over");
  });

  test("a stuck recognition request times out", () => {
    const engine = new GameEngine({ pendingTimeoutMs: 500 });
    engine.start(0);
    engine.speechStarted("stuck", 23_900);
    expect(engine.tick(24_000).phase).toBe("running");
    expect(engine.tick(24_499).phase).toBe("running");
    expect(engine.tick(24_500).phase).toBe("game-over");
  });

  test("time-based position does not depend on frame count", () => {
    const engine = new GameEngine();
    const target = engine.start(100).targets[0];
    expect(engine.position(target, 100)).toBe(0);
    expect(engine.position(target, 12_100)).toBeCloseTo(0.5);
    expect(engine.position(target, 24_100)).toBe(1);
  });

  test("pauses falling and spawn timers while push-to-talk is held", () => {
    const engine = new GameEngine({ difficulty: new TestDifficulty() });
    const target = engine.start(0).targets[0];

    engine.pause(400);
    expect(engine.tick(1_200).pausedAt).toBe(400);
    expect(engine.position(target, 1_200)).toBeCloseTo(0.4);

    engine.resume(1_400);
    const resumed = engine.tick(1_999);
    expect(resumed.phase).toBe("running");
    expect(resumed.targets).toHaveLength(2);
    expect(engine.position(resumed.targets[0], 1_999)).toBeCloseTo(0.999);
    expect(engine.tick(2_001).phase).toBe("game-over");
  });

  test("a speech attempt keeps its pre-pause reaction time", () => {
    const engine = new GameEngine({ difficulty: new TestDifficulty() });
    const amount = engine.start(0).targets[0].amount;

    engine.pause(900);
    engine.speechStarted("answer", 900);
    engine.speechEnded("answer", 1_900);
    engine.resume(1_900);

    expect(engine.tick(2_001).targets[0].state).toBe("pending-game-over");
    expect(engine.recognitionResult("answer", amount, 2_100)).toEqual({ hit: true });
    expect(engine.snapshot().score).toBe(105);
  });

  test("does not reuse an amount during the same run", () => {
    const engine = new GameEngine({ random: () => 0 });
    const first = engine.start(0).targets[0].amount;
    engine.speechStarted("first", 1);
    engine.recognitionResult("first", first, 2);
    const second = engine.tick(8_000).targets.find((target) => target.state === "falling");
    expect(second?.amount).not.toBe(first);
  });

  test("start fully resets a completed run", () => {
    const engine = new GameEngine();
    engine.start(0);
    engine.tick(24_001);
    const reset = engine.start(20_000);
    expect(reset.phase).toBe("running");
    expect(reset.score).toBe(0);
    expect(reset.hits).toBe(0);
    expect(reset.gameOver).toBeNull();
    expect(reset.targets).toHaveLength(1);
  });
});
