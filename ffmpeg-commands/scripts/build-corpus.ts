import { createHash } from "node:crypto";
import { mkdir, readFile, writeFile } from "node:fs/promises";
import path from "node:path";
import { DOC_PAGES, EMBEDDING_CONTRACT } from "../src/lib/contract.ts";
import { parseFfmpegPage } from "../src/lib/parse-docs.ts";
import { recipeChunks } from "../src/lib/recipes.ts";
import type { Chunk, CorpusFile } from "../src/lib/types.ts";

const root = path.join(import.meta.dir, "..");
const rawDir = path.join(root, "data/raw");
const outPath = path.join(root, "data/corpus.json");
const refresh = process.argv.includes("--refresh");

async function loadPage(page: string, url: string): Promise<string> {
  const file = path.join(rawDir, `${page}.html`);
  if (!refresh) {
    const cached = await readFile(file, "utf8").catch(() => "");
    if (cached) return cached;
  }
  const response = await fetch(url);
  if (!response.ok) throw new Error(`Failed to download ${url}: ${response.status}`);
  const html = await response.text();
  await writeFile(file, html);
  return html;
}

const chunks: Chunk[] = [];
await mkdir(rawDir, { recursive: true });

for (const doc of DOC_PAGES) {
  const html = await loadPage(doc.page, doc.url);
  const parsed = parseFfmpegPage({ html, page: doc.page, url: doc.url });
  console.log(`${doc.page}: ${parsed.length} chunks`);
  chunks.push(...parsed);
}
chunks.push(...recipeChunks());

const seen = new Set<string>();
const unique: Chunk[] = [];
for (const chunk of chunks) {
  if (seen.has(chunk.id)) continue;
  seen.add(chunk.id);
  if (!chunk.embedText.trim()) continue;
  unique.push(chunk);
}

const fingerprint = unique.map(chunk => `${chunk.id}\n${chunk.embedText}`).join("\n");
const corpus: CorpusFile = {
  id: createHash("sha256").update(fingerprint).digest("hex").slice(0, 16),
  builtAt: new Date().toISOString(),
  source: "https://ffmpeg.org/documentation.html",
  model: {
    repo: EMBEDDING_CONTRACT.modelRepo,
    file: EMBEDDING_CONTRACT.modelFile,
    url: EMBEDDING_CONTRACT.modelUrl,
    documentPrefix: EMBEDDING_CONTRACT.documentPrefix,
    queryPrefix: EMBEDDING_CONTRACT.queryPrefix,
    outputSize: EMBEDDING_CONTRACT.outputSize,
    maxInputLength: EMBEDDING_CONTRACT.maxInputLength,
  },
  chunks: unique,
};

await mkdir(path.dirname(outPath), { recursive: true });
await writeFile(outPath, JSON.stringify(corpus));

const byKind = unique.reduce<Record<string, number>>((counts, chunk) => {
  counts[chunk.kind] = (counts[chunk.kind] ?? 0) + 1;
  return counts;
}, {});
console.log(`wrote ${unique.length} chunks (${corpus.id})`, byKind);
console.log(`bytes ${Buffer.byteLength(JSON.stringify(corpus))}`);
