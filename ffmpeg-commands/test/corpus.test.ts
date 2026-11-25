import { expect, test } from "bun:test";
import { readFileSync } from "node:fs";
import path from "node:path";
import type { CorpusFile, EmbeddingMeta } from "../src/lib/types.ts";

const root = path.join(import.meta.dir, "..");
const corpus = JSON.parse(readFileSync(path.join(root, "data/corpus.json"), "utf8")) as CorpusFile;
const meta = JSON.parse(readFileSync(path.join(root, "data/embeddings.json"), "utf8")) as EmbeddingMeta;
const vectors = readFileSync(path.join(root, "data/embeddings.f32"));

test("corpus covers the manual and common tasks", () => {
  expect(new Set(corpus.chunks.map(chunk => chunk.id)).size).toBe(corpus.chunks.length);
  expect(corpus.chunks.length).toBeGreaterThan(1000);
  const pages = new Set(corpus.chunks.map(chunk => chunk.page));
  for (const page of ["ffmpeg", "ffprobe", "ffplay", "filters", "codecs", "formats", "protocols", "bsf", "devices", "faq"]) {
    expect(pages.has(page)).toBe(true);
  }
  for (const chunk of corpus.chunks) {
    expect(chunk.embedText.length).toBeGreaterThan(10);
    expect(chunk.embedText.length).toBeLessThanOrEqual(900);
    expect(chunk.url.startsWith("https://ffmpeg.org/")).toBe(true);
    if (chunk.command) expect(chunk.command).toMatch(/^(ffmpeg|ffprobe|ffplay)\b/);
  }
  const blob = corpus.chunks.map(chunk => `${chunk.title}\n${chunk.embedText}\n${chunk.command ?? ""}`).join("\n");
  for (const needle of ["volume", "scale=", "-ss ", "concat", "subtitles", "libx264", "вырезать", "громкость", "gif"]) {
    expect(blob).toContain(needle);
  }
});

test("shipped embeddings match the corpus contract", () => {
  expect(meta.corpusId).toBe(corpus.id);
  expect(meta.dim).toBe(256);
  expect(meta.count).toBe(corpus.chunks.length);
  expect(meta.normalized).toBe(true);
  expect(vectors.byteLength).toBe(meta.count * meta.dim * 4);
  const sample = new Float32Array(vectors.buffer, vectors.byteOffset, meta.dim);
  let norm = 0;
  for (const value of sample) norm += value * value;
  expect(norm).toBeGreaterThan(0.98);
  expect(norm).toBeLessThan(1.02);
});
