import { test, expect, beforeAll } from "bun:test";
import { makeMockEpub } from "../fixtures/mockEpub";
import { createEpubAdapter } from "../../src/reader/epub-adapter";

beforeAll(async () => {
  // happy-dom provides DOMParser, document, etc.
});

test("EPUB adapter opens and parses chapters", async () => {
  const blob = await makeMockEpub();
  const adapter = await createEpubAdapter();
  await adapter.open(blob);
  const meta = adapter.metadata();
  expect(meta.title).toBe("测试小说");
  expect(meta.author).toBe("测试作者");
  const chapters = adapter.chapters();
  expect(chapters.length).toBe(2);
  expect(chapters[0].label).toContain("第一章");
});

test("EPUB adapter extracts chapter text", async () => {
  const blob = await makeMockEpub();
  const adapter = await createEpubAdapter();
  await adapter.open(blob);
  const { paragraphs, text } = await adapter.chapterText("ch1");
  expect(paragraphs.length).toBeGreaterThan(0);
  expect(text).toContain("方源");
  expect(text).toContain("春秋蝉");
});

test("EPUB adapter adjacent chapters", async () => {
  const blob = await makeMockEpub();
  const adapter = await createEpubAdapter();
  await adapter.open(blob);
  const adj = adapter.adjacentChapters("ch1");
  expect(adj.next?.id).toBe("ch2");
  expect(adj.prev).toBeUndefined();
  const adj2 = adapter.adjacentChapters("ch2");
  expect(adj2.prev?.id).toBe("ch1");
  expect(adj2.next).toBeUndefined();
});