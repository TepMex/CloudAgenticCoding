import { describe, expect, it } from "vitest";
import characters from "../data/characters.json";
import { normalizePinyinForSearch, numberedToMarked, parsePinyin } from "./pinyin";

describe("pinyin normalization", () => {
  it.each([
    ["ya", "", "ia"], ["you", "", "iu"], ["ying", "", "ing"], ["yong", "", "iong"],
    ["wa", "", "ua"], ["wei", "", "ui"], ["wen", "", "un"],
    ["yu", "", "ü"], ["yue", "", "üe"], ["yuan", "", "üan"], ["yun", "", "ün"],
    ["ju", "j", "ü"], ["que", "q", "üe"], ["xuan", "x", "üan"],
    ["zhang", "zh", "ang"], ["chi", "ch", "apical-i"], ["zi", "z", "apical-i"],
  ])("places %s at %s + %s", (input, initial, final) => {
    expect(parsePinyin(input)).toMatchObject({ initial, final, tone: 5 });
  });

  it("accepts marked, numbered, u-colon, v, decomposed Unicode, and neutral tones", () => {
    expect(parsePinyin("lǜ")).toMatchObject({ pinyinNumbered: "lü4", tone: 4, initial: "l", final: "ü" });
    expect(parsePinyin("lu:4")).toMatchObject({ pinyinNumbered: "lü4" });
    expect(parsePinyin("lv4")).toMatchObject({ pinyinNumbered: "lü4" });
    expect(parsePinyin("lu\u03084")).toMatchObject({ pinyinNumbered: "lü4" });
    expect(parsePinyin("ma")).toMatchObject({ tone: 5 });
    expect(numberedToMarked("xiong2")).toBe("xióng");
  });

  it("keeps rare syllabic readings in a special group", () => {
    expect(parsePinyin("m2")).toMatchObject({ initial: "", final: "special:m", special: true });
  });

  it("normalizes search variants for ü", () => {
    expect(normalizePinyinForSearch("lǜ")).toBe("lv");
    expect(normalizePinyinForSearch("lv4")).toBe("lv");
    expect(normalizePinyinForSearch("lu:4")).toBe("lv");
  });
});

describe("known polyphonic characters", () => {
  it.each(["行", "重", "长", "乐", "还", "得", "着"])("preserves multiple readings for %s", (character) => {
    const record = characters.find((item) => item.character === character);
    expect(record?.readings.length).toBeGreaterThan(1);
    expect(new Set(record?.readings.map((reading: { pinyinNumbered: string }) => reading.pinyinNumbered)).size).toBe(record?.readings.length);
  });
});
