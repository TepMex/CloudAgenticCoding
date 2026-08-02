import { describe, expect, test } from "bun:test";
import {
  matchesMeaning,
  matchesPinyin,
  normalizeMeaning,
  normalizePinyin,
} from "./answerMatching";

describe("answerMatching", () => {
  test("matches toneless pinyin", () => {
    expect(matchesPinyin("Yi", ["yi"])).toBe(true);
    expect(matchesPinyin("yī", ["yi"])).toBe(true);
    expect(matchesPinyin("er", ["yi"])).toBe(false);
  });

  test("normalizes meaning punctuation and case", () => {
    expect(normalizeMeaning("  Ten Thousand! ")).toBe("ten thousand");
    expect(normalizeMeaning("go fishin’")).toBe("go fishin");
    expect(normalizePinyin("YĪ")).toBe("yi");
  });

  test("matches keywords including parenthetical notes", () => {
    expect(matchesMeaning("I (literary)", "I (literary)")).toBe(true);
    expect(matchesMeaning("i", "I (literary)")).toBe(true);
    expect(matchesMeaning("how many", "how many?")).toBe(true);
    expect(matchesMeaning("ten thousand", "ten thousand")).toBe(true);
    expect(matchesMeaning("two", "one")).toBe(false);
  });
});
