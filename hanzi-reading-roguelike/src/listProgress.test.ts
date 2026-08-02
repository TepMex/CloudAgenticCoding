import { describe, expect, test } from "bun:test";
import {
  isListComplete,
  pixelsPerTickForList,
  spawnIntervalForList,
  SPAWN_BASE_MS,
  SPAWN_MIN_MS,
} from "./listProgress";

describe("listProgress", () => {
  test("list 0 uses base spawn interval", () => {
    expect(spawnIntervalForList(0)).toBe(SPAWN_BASE_MS);
  });

  test("later lists spawn faster but never below minimum", () => {
    expect(spawnIntervalForList(1)).toBeLessThan(SPAWN_BASE_MS);
    expect(spawnIntervalForList(100)).toBe(SPAWN_MIN_MS);
  });

  test("drift speed rises with list index", () => {
    expect(pixelsPerTickForList(0)).toBe(2.6);
    expect(pixelsPerTickForList(5)).toBe(2.6 + 2);
  });

  test("list completes only when all unique hanzi are cleared", () => {
    expect(isListComplete(3, new Set())).toBe(false);
    expect(isListComplete(3, new Set(["一", "二"]))).toBe(false);
    expect(isListComplete(3, new Set(["一", "二", "三"]))).toBe(true);
    expect(isListComplete(0, new Set())).toBe(false);
  });
});
