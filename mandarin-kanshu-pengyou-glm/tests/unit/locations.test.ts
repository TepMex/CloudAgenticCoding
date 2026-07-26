import { test, expect } from "bun:test";
import { locationKey, recoverLocationOffset, locationEquals } from "../../src/reader/locations";
import type { ReaderLocation } from "../../src/shared/domain";

const loc = (q: string, prefix = "", suffix = ""): ReaderLocation => ({
  bookId: "b", spineItemId: "s", textQuote: q, prefix, suffix,
});

test("locationKey is stable", () => {
  expect(locationKey(loc("abc"))).toBe("b|s|abc");
});

test("recoverLocationOffset exact match", () => {
  expect(recoverLocationOffset(loc("你好"), "前面你好后面")).toBe(2);
});

test("recoverLocationOffset prefix+quote", () => {
  expect(recoverLocationOffset(loc("你好", "前面", ""), "前面你好后面")).toBe(2);
});

test("recoverLocationOffset quote+suffix", () => {
  expect(recoverLocationOffset(loc("你好", "", "后面"), "前面你好后面")).toBe(2);
});

test("recoverLocationOffset fuzzy", () => {
  expect(recoverLocationOffset(loc("你好世界朋友"), "你好世界朋友啊")).toBe(0);
});

test("recoverLocationOffset no match returns null", () => {
  expect(recoverLocationOffset(loc("zzz"), "abc")).toBeNull();
});

test("locationEquals", () => {
  expect(locationEquals(loc("a"), loc("a"))).toBe(true);
  expect(locationEquals(loc("a"), loc("b"))).toBe(false);
});