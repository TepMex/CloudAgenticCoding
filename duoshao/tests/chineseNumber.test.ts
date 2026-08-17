import { describe, expect, test } from "bun:test";
import { toChineseMoney, toChineseNumber } from "../src/chinese/chineseNumber";
import { normalizeTranscript } from "../src/chinese/normalizeTranscript";
import { parseChineseMoney } from "../src/chinese/parseChineseMoney";

const cases: Array<[number, string]> = [
  [1, "一"], [9, "九"], [10, "十"], [11, "十一"], [20, "二十"], [25, "二十五"],
  [100, "一百"], [101, "一百零一"], [105, "一百零五"], [110, "一百一十"],
  [200, "二百"], [250, "二百五十"], [999, "九百九十九"], [1000, "一千"],
  [1001, "一千零一"], [1010, "一千零一十"], [1100, "一千一百"], [2000, "二千"],
  [2010, "二千零一十"], [9999, "九千九百九十九"],
];

describe("Chinese number generation", () => {
  for (const [amount, expected] of cases) {
    test(`${amount} → ${expected}`, () => {
      expect(toChineseNumber(amount)).toBe(expected);
      expect(toChineseMoney(amount)).toBe(`${expected}元`);
    });
  }

  test("rejects values outside the MVP domain", () => {
    expect(() => toChineseNumber(0)).toThrow(RangeError);
    expect(() => toChineseNumber(10_000)).toThrow(RangeError);
    expect(() => toChineseNumber(1.5)).toThrow(RangeError);
  });
});

describe("Chinese money parsing", () => {
  for (const [amount, numeral] of cases) {
    test(`${numeral}元 → ${amount}`, () => expect(parseChineseMoney(`${numeral}元`)?.amount).toBe(amount));
  }

  test.each([
    ["二百元", 200], ["两百元", 200], ["两千块", 2000], ["三百块钱", 300],
    ["300元", 300], ["  三百元。 ", 300], ["三百", 300], ["300", 300],
  ])("accepts %s", (input, expected) => expect(parseChineseMoney(input)?.amount).toBe(expected));

  test.each([
    "", "随便的中文", "我觉得这个应该是三百元", "三百和二百", "3.5元", "三点五元",
    "0元", "10000元", "两十元", "百元", "一百百元", "三百美元",
  ])("rejects %s", (input) => expect(parseChineseMoney(input)).toBeNull());

  test("normalizes harmless edge punctuation and whitespace", () => {
    expect(normalizeTranscript("  “ 三 百 元 ！” ")).toBe("三百元");
  });
});
