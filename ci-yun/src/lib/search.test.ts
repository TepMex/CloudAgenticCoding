import { describe, expect, it } from "vitest";
import { rankWords } from "./search";
import type { Word } from "./types";

function word(patch: Partial<Word> & Pick<Word, "id" | "simplified">): Word {
  return {
    traditional: patch.simplified,
    pinyin: "",
    meaning: "",
    meaningRu: "",
    pos: "",
    sources: ["hsk"],
    hsk: [],
    cluster: 0,
    x2: 0,
    y2: 0,
    x: 0,
    y: 0,
    z: 0,
    neighbors: [],
    ...patch,
  };
}

const words = [
  word({ id: 0, simplified: "经济", pinyin: "jīngjì", meaning: "economy", meaningRu: "экономика" }),
  word({ id: 1, simplified: "经理", pinyin: "jīnglǐ", meaning: "manager", meaningRu: "менеджер" }),
  word({ id: 2, simplified: "还", pinyin: "hái", meaning: "still", meaningRu: "ещё" }),
  word({ id: 3, simplified: "还", pinyin: "huán", meaning: "to return", meaningRu: "возвращать" }),
  word({ id: 4, simplified: "我", pinyin: "wǒ", meaning: "I; me", meaningRu: "я; меня" }),
];

describe("rankWords", () => {
  it("returns nothing for a blank query", () => {
    expect(rankWords(words, "  ")).toEqual([]);
  });

  it("puts the exact simplified form above a longer word that contains it", () => {
    const found = rankWords(words, "经");
    expect(found.map((item) => item.simplified)).toEqual(["经济", "经理"]);
  });

  it("lists both senses of a shared simplified form, stable by id", () => {
    const found = rankWords(words, "还");
    expect(found.map((item) => item.pinyin)).toEqual(["hái", "huán"]);
  });

  it("matches toneless pinyin", () => {
    expect(rankWords(words, "jingji").map((item) => item.id)).toEqual([0]);
  });

  it("matches a Russian gloss", () => {
    expect(rankWords(words, "яблок")).toEqual([]);
    expect(rankWords(words, "эконом").map((item) => item.id)).toEqual([0]);
  });

  it("does not match a string outside the list", () => {
    expect(rankWords(words, "компьютер")).toEqual([]);
  });
});
