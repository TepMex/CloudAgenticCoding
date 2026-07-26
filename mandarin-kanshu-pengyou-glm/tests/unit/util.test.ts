import { test, expect } from "bun:test";
import { redactKeys, uuid, approxTokens, clamp } from "../../src/shared/util";

test("redactKeys redacts sk- keys", () => {
  expect(redactKeys("my key is sk-abcdefghijklmnopqrstuvwxyz")).toBe("my key is [redacted]");
});

test("redactKeys redacts Bearer tokens", () => {
  expect(redactKeys("Authorization: Bearer abcdefghijklmnop123456")).toContain("[redacted]");
});

test("redactKeys leaves normal text alone", () => {
  expect(redactKeys("hello world")).toBe("hello world");
});

test("uuid is unique-ish and 36 chars", () => {
  const a = uuid();
  const b = uuid();
  expect(a).toHaveLength(36);
  expect(a).not.toEqual(b);
});

test("approxTokens counts CJK and latin", () => {
  expect(approxTokens("你好世界")).toBe(2);
  expect(approxTokens("hello")).toBeGreaterThan(0);
});

test("clamp", () => {
  expect(clamp(5, 0, 10)).toBe(5);
  expect(clamp(-1, 0, 10)).toBe(0);
  expect(clamp(11, 0, 10)).toBe(10);
});