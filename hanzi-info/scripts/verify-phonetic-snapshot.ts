#!/usr/bin/env bun
/**
 * Verifies that HanziJS phonetic tables (after HanziCraft filters: >2 members, scales 1–2)
 * match the MHTML snapshot `HanziCraft - Chinese Character Phonetic Sets`.
 */
import fs from "node:fs";
import path from "node:path";
import { createRequire } from "node:module";

const require = createRequire(import.meta.url);
const one = require("hanzi/lib/data/phonetic_sets_regularity_one.js").regularity_one as Record<string, string[]>;
const two = require("hanzi/lib/data/phonetic_sets_regularity_two.js").regularity_two as Record<string, string[]>;

const MIN = 3;
const snapPath = path.join(import.meta.dir, "..", "HanziCraft - Chinese Character Phonetic Sets");
const raw = fs.readFileSync(snapPath, "utf8");

const s1 = raw.slice(raw.indexOf("Regularity Degree One"), raw.indexOf("Regularity Degree Two"));
const s2 = raw.slice(
  raw.indexOf("Regularity Degree Two"),
  raw.indexOf(`</div></li></ol><div style="clear:both"></div></div><div id="footer">`),
);
const re =
  /<div class="list_head">[^<]*<\/div><div class="list_details"><a href="https:\/\/hanzicraft\.com\/character\/([^"]+)"/g;

function parseLists(html: string): string[][] {
  const lists: string[][] = [];
  let m;
  while ((m = re.exec(html))) {
    lists.push(m[1].split(",").map(p => decodeURIComponent(p)));
  }
  return lists;
}

function norm(a: string[]) {
  return [...a].sort().join("");
}

function multisetFromHanzi(obj: Record<string, string[]>) {
  const s = new Set<string>();
  for (const arr of Object.values(obj)) {
    if (arr.length < MIN) continue;
    s.add(norm(arr));
  }
  return s;
}

const snap1 = new Set(parseLists(s1).map(norm));
const snap2 = new Set(parseLists(s2).map(norm));
const h1 = multisetFromHanzi(one);
const h2 = multisetFromHanzi(two);

function diffLine(name: string, a: Set<string>, b: Set<string>) {
  const onlyA = [...a].filter(x => !b.has(x));
  const onlyB = [...b].filter(x => !a.has(x));
  if (onlyA.length || onlyB.length) {
    console.error(`${name}: snapshot ${a.size}, hanzi ${b.size}`);
    console.error(`  only in snapshot: ${onlyA.slice(0, 5).join(" | ")}`);
    console.error(`  only in HanziJS: ${onlyB.slice(0, 5).join(" | ")}`);
    process.exit(1);
  }
  console.log(`${name}: OK (${a.size} sets)`);
}

diffLine("Regularity degree one", snap1, h1);
diffLine("Regularity degree two", snap2, h2);
console.log("Phonetic snapshot matches HanziJS with HanziCraft filters.");
