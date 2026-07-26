import { test, expect } from "bun:test";
import { extractJson, validateWithSchema } from "../../src/providers/parse";
import { z } from "zod";

test("extractJson direct", () => {
  expect(extractJson('{"a":1}')).toEqual({ a: 1 });
});

test("extractJson fenced", () => {
  expect(extractJson('```json\n{"a":1}\n```')).toEqual({ a: 1 });
  expect(extractJson('```\n{"a":1}\n```')).toEqual({ a: 1 });
});

test("extractJson embedded object", () => {
  expect(extractJson('text before {"a":1} text after')).toEqual({ a: 1 });
});

test("extractJson handles nested braces in strings", () => {
  expect(extractJson('{"a":"} {"}')).toEqual({ a: "} {" });
});

test("extractJson returns null for non-json", () => {
  expect(extractJson("not json")).toBeNull();
});

test("extractJson array", () => {
  expect(extractJson("[1,2,3]")).toEqual([1, 2, 3]);
});

const schema = z.object({ a: z.number() });

test("validateWithSchema ok", () => {
  const r = validateWithSchema({ a: 1 }, schema);
  expect(r.ok).toBe(true);
  expect(r.data).toEqual({ a: 1 });
});

test("validateWithSchema fail", () => {
  const r = validateWithSchema({ a: "x" }, schema);
  expect(r.ok).toBe(false);
  expect(r.error).toBeTruthy();
});