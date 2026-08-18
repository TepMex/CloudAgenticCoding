import { describe, expect, test } from "bun:test";
import { DifficultyManager } from "../src/game/difficulty";

describe("DifficultyManager", () => {
  const manager = new DifficultyManager();

  test("progresses only after ten hits and caps at stage 6", () => {
    expect(manager.settingsForHits(0).level).toBe(1);
    expect(manager.settingsForHits(9).level).toBe(1);
    expect(manager.settingsForHits(10).level).toBe(2);
    expect(manager.settingsForHits(50).level).toBe(6);
    expect(manager.settingsForHits(500).level).toBe(6);
  });

  test("gradually speeds up and adds concurrency", () => {
    const first = manager.settingsForHits(0);
    const last = manager.settingsForHits(50);
    expect(last.fallDurationMs).toBeLessThan(first.fallDurationMs);
    expect(last.spawnIntervalMs).toBeLessThan(first.spawnIntervalMs);
    expect(last.maxConcurrentTargets).toBeGreaterThan(first.maxConcurrentTargets);
  });

  test("uses fall durations that are twice as long as the original speed", () => {
    expect(manager.settingsForHits(0).fallDurationMs).toBe(24_000);
    expect(manager.settingsForHits(50).fallDurationMs).toBe(15_000);
  });

  test("generates amounts inside each stage domain", () => {
    for (let level = 1; level <= 6; level += 1) {
      for (const random of [0, 0.25, 0.5, 0.99]) {
        const amount = manager.generateAmount(level, () => random);
        expect(amount).toBeGreaterThanOrEqual(level === 1 ? 1 : level === 2 ? 11 : level <= 4 ? 100 : 1000);
        expect(amount).toBeLessThanOrEqual(level === 1 ? 10 : level === 2 ? 99 : level <= 4 ? 999 : 9999);
      }
    }
  });

  test("falls back to an unused amount when random keeps repeating", () => {
    expect(manager.generateUnusedAmount(1, () => 0, new Set([1]))).toBe(2);
  });
});
