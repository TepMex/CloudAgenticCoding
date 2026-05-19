#!/usr/bin/env bun
/**
 * Regenerates `data/wiki_zh_ru_single_char.json` from open-dict-data Wikipedia
 * interwiki dumps (Chinese article title ↔ Russian title, single-character keys only).
 * Run occasionally if you want to refresh interwiki pairs; requires network.
 */
import { mkdirSync, writeFileSync } from "node:fs";
import path from "node:path";

async function pairs(mode: "zh-left" | "zh-right", text: string): Promise<[string, string][]> {
  const out: [string, string][] = [];
  for (const line of text.split(/\n/)) {
    if (!line) continue;
    const tab = line.indexOf("\t");
    if (tab <= 0) continue;
    const zh = mode === "zh-left" ? line.slice(0, tab) : line.slice(tab + 1).trim();
    const ru = mode === "zh-left" ? line.slice(tab + 1).trim() : line.slice(0, tab);
    if ([...zh].length === 1 && ru) out.push([zh, ru]);
  }
  return out;
}

const u1 = "https://raw.githubusercontent.com/open-dict-data/wikidict-ru/master/data/zh-ru_wiki.txt";
const u2 = "https://raw.githubusercontent.com/open-dict-data/wikidict-zh/master/data/ru-zh_wiki.txt";

const [t1, t2] = await Promise.all([(await fetch(u1)).text(), (await fetch(u2)).text()]);
const map: Record<string, string> = {};
for (const [zh, ru] of await pairs("zh-left", t1)) map[zh] ??= ru;
for (const [zh, ru] of await pairs("zh-right", t2)) map[zh] ??= ru;

const outDir = path.join(import.meta.dir, "..", "data");
mkdirSync(outDir, { recursive: true });
const outPath = path.join(outDir, "wiki_zh_ru_single_char.json");
writeFileSync(outPath, JSON.stringify(map), "utf8");
console.log(`Wrote ${outPath}, ${Object.keys(map).length} single-character entries.`);
