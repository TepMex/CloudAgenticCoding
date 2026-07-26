import { test, expect } from "bun:test";
import {
  expandSelection, countSentences, normalizeSelection, gatherContext, locateInParagraphs,
} from "../../src/reader/selection/expand";

test("expandSelection expands backward to 。 and forward to 。", () => {
  const paras = ["天气很好。我们一起去公园走走吧。你想带什么？我带了水。"];
  const expanded = expandSelection(paras, 0, 8, 0, 14, "去公园走走吧");
  expect(expanded.passage).toBe("我们一起去公园走走吧。");
  expect(expanded.sentenceCount).toBe(1);
  expect(expanded.crossesParagraphs).toBe(false);
  expect(expanded.longWarning).toBe(false);
});

test("expandSelection crosses paragraph boundaries", () => {
  const paras = ["第一句。第二句。", "第三句。第四句。第五句。"];
  const expanded = expandSelection(paras, 0, 4, 1, 3, "二句。第三句");
  expect(expanded.crossesParagraphs).toBe(true);
  expect(expanded.passage).toContain("第二句");
  expect(expanded.passage).toContain("第三句");
});

test("countSentences counts 。！？", () => {
  expect(countSentences("你好。再见！")).toBe(2);
  expect(countSentences("一个句子")).toBe(1);
  expect(countSentences("")).toBe(0);
});

test("normalizeSelection collapses whitespace and joins paragraphs", () => {
  expect(normalizeSelection("hello   world\n\nfoo\nbar")).toBe("hello world\n\nfoo\n\nbar");
});

test("gatherContext returns before/after paragraphs", () => {
  const paras = ["a", "b", "c", "d", "e"];
  const ctx = gatherContext(paras, 2, 2, 2, 2);
  expect(ctx.before).toEqual(["a", "b"]);
  expect(ctx.after).toEqual(["d", "e"]);
});

test("locateInParagraphs finds snippet offset", () => {
  const paras = ["你好世界。", "再见。"];
  expect(locateInParagraphs(paras, "你好世界")).toEqual({ para: 0, offset: 0 });
  expect(locateInParagraphs(paras, "不存在")).toBeNull();
});

test("longWarning triggers above 5 sentences", () => {
  const paras = ["一。二。三。四。五。六。七。八。"];
  const expanded = expandSelection(paras, 0, 2, 0, 14, "二。三。四。五。六。七");
  expect(expanded.sentenceCount).toBeGreaterThan(5);
  expect(expanded.longWarning).toBe(true);
});