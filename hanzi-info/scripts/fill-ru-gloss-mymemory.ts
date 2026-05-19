#!/usr/bin/env bun
/**
 * Builds `data/gloss_en_to_ru.json`: English CC-CEDICT gloss string → Russian (MyMemory API).
 * Run manually when glosses change; `scripts/build-database.ts` reads the result.
 */
import { mkdirSync, readFileSync, writeFileSync } from "node:fs";
import path from "node:path";

const dbPath = path.join(import.meta.dir, "..", "public", "hanzi-db.json");
const outDir = path.join(import.meta.dir, "..", "data");
const outPath = path.join(outDir, "gloss_en_to_ru.json");

const db = JSON.parse(readFileSync(dbPath, "utf8")) as { hanzi: { meaning_en: string }[] };
const uniq = new Set<string>();
for (const r of db.hanzi) {
  if (r.meaning_en) uniq.add(r.meaning_en);
}

mkdirSync(outDir, { recursive: true });
let map: Record<string, string> = {};
try {
  map = JSON.parse(readFileSync(outPath, "utf8")) as Record<string, string>;
} catch {
  /* empty */
}

const todo = [...uniq].filter((e) => !map[e]);
console.log(`Unique English glosses: ${uniq.size}, already cached: ${uniq.size - todo.length}, to fetch: ${todo.length}`);

const BATCH = 22;
for (let i = 0; i < todo.length; i += BATCH) {
  const part = todo.slice(i, i + BATCH);
  const results = await Promise.all(
    part.map(async (q) => {
      const u =
        "https://api.mymemory.translated.net/get?" +
        new URLSearchParams({ q, langpair: "en|ru" });
      const res = await fetch(u);
      const j = (await res.json()) as {
        responseStatus: number;
        responseData?: { translatedText?: string };
        quotaFinished?: boolean;
      };
      return { q, j };
    }),
  );
  for (const { q, j } of results) {
    if (j.responseStatus !== 200 || j.quotaFinished) {
      console.warn("API issue for gloss:", q.slice(0, 60), j);
      continue;
    }
    const t = j.responseData?.translatedText?.trim();
    if (!t || t.includes("MYMEMORY")) continue;
  }
  writeFileSync(outPath, JSON.stringify(map), "utf8");
  console.log(`Wrote progress ${Math.min(i + BATCH, todo.length)} / ${todo.length}`);
  await new Promise(r => setTimeout(r, 280));
}

console.log(`Done. Wrote ${outPath} (${Object.keys(map).length} entries).`);
