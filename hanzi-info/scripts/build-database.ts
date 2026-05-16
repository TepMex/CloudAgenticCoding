#!/usr/bin/env bun
/**
 * Builds `public/hanzi-db.json` from HanziJS, filtered to match the HanziCraft phonetic-sets page:
 * regularity degrees 1–2 and only sets with more than two characters (see on-page copy there).
 * Also merges CC-CEDICT, frequency data, and IDS decomposition.
 */
import { createRequire } from "node:module";
import { mkdirSync, writeFileSync } from "node:fs";
import path from "node:path";

const require = createRequire(import.meta.url);

type HanziType = "Phonetic" | "Ideographic" | "Radical";

type HanziRow = {
  id: number;
  hanzi: string;
  type: HanziType;
  radical_name_en?: string;
  meaning_en: string;
  meaning_ru: string;
  reading: string;
  initiale: string;
  finale: string;
  tone: number;
};

type PhoneticSeries = {
  component: string;
  component_reading_numbered: string;
  regularity_scale: 1 | 2;
  set_key: string;
  members: string[];
};

type HanziDatabase = {
  version: 1;
  about: string;
  hanzi: HanziRow[];
  hanzi2radicals: [hanzi_id: number, radical_id: number][];
  /** Lookup by single character (UTF-16 code unit string) */
  by_hanzi: Record<string, number>;
  /** Characters that appear in HanziCraft-style phonetic sets */
  phonetic_series_by_hanzi: Record<string, PhoneticSeries[]>;
};

const hanziMod: typeof import("hanzi") = require("hanzi");
hanziMod.start();

const regularity_one = require("hanzi/lib/data/phonetic_sets_regularity_one.js").regularity_one as Record<
  string,
  string[]
>;
const regularity_two = require("hanzi/lib/data/phonetic_sets_regularity_two.js").regularity_two as Record<
  string,
  string[]
>;
const radicalListWithMeaning = require("hanzi/lib/data/radicalListWithMeaning.js").radicalListWithMeaning as Record<
  string,
  string
>;
const frequencyRaw = require("hanzi/lib/data/frequency_with_script_variants_removed.txt.js") as string;
const cedictRaw = require("hanzi/lib/data/cedict_ts.u8.js") as string;

function parsePhoneticKey(key: string) {
  const tone = parseInt(key.at(-1) ?? "", 10);
  if (Number.isNaN(tone) || tone < 1 || tone > 5) return null;
  const rest = key.slice(0, -1);
  const m = rest.match(/^(\p{sc=Han}+)([a-zü:.]+)$/iu);
  if (!m?.[1] || !m[2]) return null;
  return { component: m[1], pinyin: m[2].replace(/:/g, "ü"), tone, rawKey: key };
}

function splitNumberedPinyin(raw: string): { initiale: string; finale: string; tone: number } {
  const tone = parseInt(raw.at(-1) ?? "0", 10);
  const syll = raw.slice(0, -1).replace(/:/g, "ü");
  if (![1, 2, 3, 4, 5].includes(tone)) {
    return { initiale: "", finale: syll, tone: 0 };
  }
  let initiale = "";
  let body = syll;
  for (const d of ["zh", "ch", "sh"] as const) {
    if (syll.startsWith(d)) {
      initiale = d;
      body = syll.slice(2);
      break;
    }
  }
  if (!initiale && syll.length > 0) {
    initiale = syll[0] ?? "";
    body = syll.slice(1);
  }
  return { initiale, finale: body, tone };
}

/** Single-syllable numbered pinyin → toned (rough, good enough for display). */
function numberedSyllableToMarked(syllable: string, tone: number): string {
  if (tone === 5 || tone === 0 || !syllable) return syllable;
  const s = syllable.replace(/v/g, "ü").toLowerCase();
  const idx = pickVowelIndex(s);
  if (idx === -1) return syllable + tone;
  const ch = s[idx] ?? "";
  const table: Record<string, [string, string, string, string]> = {
    a: ["ā", "á", "ǎ", "à"],
    e: ["ē", "é", "ě", "è"],
    i: ["ī", "í", "ǐ", "ì"],
    o: ["ō", "ó", "ǒ", "ò"],
    u: ["ū", "ú", "ǔ", "ù"],
    ü: ["ǖ", "ǘ", "ǚ", "ǜ"],
  };
  const row = table[ch];
  if (!row || tone < 1 || tone > 4) return syllable;
  const out = row[tone - 1] ?? ch;
  return s.slice(0, idx) + out + s.slice(idx + ch.length);
}

function pickVowelIndex(s: string): number {
  const lower = s.toLowerCase();
  const ia = lower.indexOf("a");
  if (ia !== -1) return ia;
  const ie = lower.indexOf("e");
  if (ie !== -1) return ie;
  if (lower.includes("ou")) return lower.indexOf("o") + 1;
  if (lower.includes("uo")) return lower.indexOf("o");
  if (lower.includes("iu")) return lower.indexOf("u");
  if (lower.includes("ui")) return lower.indexOf("i");
  const io = lower.indexOf("o");
  if (io !== -1) return io;
  for (let i = lower.length - 1; i >= 0; i--) {
    const c = lower[i];
    if (c && "iouü".includes(c)) return i;
  }
  return -1;
}

function parseCedictLine(line: string) {
  if (!line || line.startsWith("#")) return null;
  const open = line.indexOf("[");
  const close = line.indexOf("]");
  const slashOpen = line.indexOf("/", close);
  const slashClose = line.lastIndexOf("/");
  if (open === -1 || close === -1 || slashOpen === -1 || slashClose <= slashOpen) return null;
  const head = line.slice(0, open).trim();
  const bits = head.split(/\s+/);
  if (bits.length < 2) return null;
  const trad = bits[0] ?? "";
  const simp = bits[1] ?? "";
  if ([...simp].length !== 1) return null;
  const pinyinBracket = line.slice(open + 1, close);
  const pinyinMarked = (pinyinBracket.split(/\s*\/\s*/)[0] ?? pinyinBracket).trim();
  const def = line.slice(slashOpen + 1, slashClose).trim();
  return { trad, simp, pinyinMarked, def };
}

function build() {
  const freq = new Map<string, { py: string; gloss: string }>();
  for (const line of frequencyRaw.split(/\r?\n/)) {
    const p = line.split("\t");
    if (p.length < 6) continue;
    const ch = p[1];
    const pyField = p[4];
    const gloss = p[5];
    if (!ch || !pyField) continue;
    freq.set(ch, { py: pyField.split("/")[0] ?? pyField, gloss: gloss ?? "" });
  }

  const cedictSimp = new Map<string, { trad: string; pinyinMarked: string; def: string }>();
  for (const line of cedictRaw.split(/\r?\n/)) {
    const row = parseCedictLine(line);
    if (!row) continue;
    cedictSimp.set(row.simp, { trad: row.trad, pinyinMarked: row.pinyinMarked, def: row.def });
  }

  const phoneticKeyComponents = new Set<string>();
  const phoneticSeriesByChar = new Map<string, PhoneticSeries[]>();

  /** HanziCraft only publishes sets with more than two characters (see on-page copy). */
  const MIN_PHONETIC_SET_SIZE = 3;

  function ingestPhonetic(scale: 1 | 2, obj: Record<string, string[]>) {
    for (const [key, members] of Object.entries(obj)) {
      if (members.length < MIN_PHONETIC_SET_SIZE) continue;
      const parsed = parsePhoneticKey(key);
      if (!parsed) continue;
      phoneticKeyComponents.add(parsed.component);
      const series: PhoneticSeries = {
        component: parsed.component,
        component_reading_numbered: `${parsed.pinyin}${parsed.tone}`,
        regularity_scale: scale,
        set_key: key,
        members: [...members],
      };
      for (const ch of members) {
        const arr = phoneticSeriesByChar.get(ch) ?? [];
        arr.push(series);
        phoneticSeriesByChar.set(ch, arr);
      }
    }
  }

  ingestPhonetic(1, regularity_one);
  ingestPhonetic(2, regularity_two);

  for (const [ch, list] of [...phoneticSeriesByChar.entries()]) {
    const seen = new Set<string>();
    phoneticSeriesByChar.set(
      ch,
      list.filter((e) => {
        const sid = `${e.set_key}:${e.regularity_scale}`;
        if (seen.has(sid)) return false;
        seen.add(sid);
        return true;
      }),
    );
  }

  const charSet = new Set<string>();
  for (const ch of freq.keys()) charSet.add(ch);
  for (const ch of phoneticSeriesByChar.keys()) charSet.add(ch);
  for (const k of Object.keys(regularity_one)) {
    const arr = regularity_one[k];
    if (!arr || arr.length < MIN_PHONETIC_SET_SIZE) continue;
    const p = parsePhoneticKey(k);
    if (p) charSet.add(p.component);
    for (const x of arr) charSet.add(x);
  }
  for (const k of Object.keys(regularity_two)) {
    const arr = regularity_two[k];
    if (!arr || arr.length < MIN_PHONETIC_SET_SIZE) continue;
    const p = parsePhoneticKey(k);
    if (p) charSet.add(p.component);
    for (const x of arr) charSet.add(x);
  }

  let grew = true;
  while (grew) {
    grew = false;
    for (const ch of [...charSet]) {
      const d = hanziMod.decompose(ch);
      if (typeof d === "string") continue;
      for (const part of [...d.components1, ...d.components2]) {
        if (!part || part === "No glyph available" || /^\d+$/.test(part)) continue;
        if (!charSet.has(part)) {
          charSet.add(part);
          grew = true;
        }
      }
    }
  }

  const sorted = [...charSet].sort((a, b) => a.localeCompare(b, "zh-Hans"));

  function classify(ch: string): HanziType {
    if (phoneticKeyComponents.has(ch)) return "Phonetic";
    if (radicalListWithMeaning[ch]) return "Radical";
    return "Ideographic";
  }

  const rows: HanziRow[] = [];
  const byHanzi: Record<string, number> = {};

  let id = 0;
  for (const h of sorted) {
    id += 1;
    const ce = cedictSimp.get(h);
    const fq = freq.get(h);
    const numbered = fq?.py ?? "";
    const parts = numbered ? splitNumberedPinyin(numbered) : { initiale: "", finale: "", tone: 0 };
    let reading = ce?.pinyinMarked ?? "";
    if (!reading && numbered && parts.tone > 0) {
      const syl = numbered.slice(0, -1).replace(/:/g, "ü");
      reading = numberedSyllableToMarked(syl, parts.tone);
    }
    if (!reading && numbered) reading = numbered;

    const meaning_en = ce?.def ?? fq?.gloss ?? "";
    const type = classify(h);
    const radicalName = radicalListWithMeaning[h];

    rows.push({
      id,
      hanzi: h,
      type,
      radical_name_en: type === "Radical" && radicalName ? radicalName : undefined,
      meaning_en,
      meaning_ru: "",
      reading,
      initiale: parts.initiale,
      finale: parts.finale,
      tone: parts.tone,
    });
    byHanzi[h] = id;
  }

  const hanzi2radicals: [number, number][] = [];
  for (const h of sorted) {
    const hid = byHanzi[h];
    if (hid === undefined) continue;
    const d = hanziMod.decompose(h);
    if (typeof d === "string") continue;
    const parts = [...new Set([...d.components1, ...d.components2])].filter(
      p => p && p !== "No glyph available" && !/^\d+$/.test(p) && p !== h,
    );
    for (const part of parts) {
      const rid = byHanzi[part];
      if (rid !== undefined) hanzi2radicals.push([hid, rid]);
    }
  }

  const phonOut: Record<string, PhoneticSeries[]> = {};
  for (const [ch, list] of phoneticSeriesByChar) {
    if (byHanzi[ch] !== undefined) phonOut[ch] = list;
  }

  const db: HanziDatabase = {
    version: 1,
    about:
      "Phonetic-set lists follow HanziCraft (https://hanzicraft.com/lists/phonetic-sets): regularity degree one " +
      "(exact pronunciation including tone) and degree two (same syllable, different tone), and only sets with " +
      "more than two characters—the same filters described on that page. Rows are taken from HanziJS " +
      "`phonetic_sets_regularity_one` / `phonetic_sets_regularity_two` and verified against the repo snapshot " +
      "`HanziCraft - Chinese Character Phonetic Sets`. English glosses and readings come from CC-CEDICT and " +
      "frequency data bundled with HanziJS. Structural parts use HanziJS IDS decomposition.",
    hanzi: rows,
    hanzi2radicals,
    by_hanzi: byHanzi,
    phonetic_series_by_hanzi: phonOut,
  };

  const outDir = path.join(import.meta.dir, "..", "public");
  mkdirSync(outDir, { recursive: true });
  const outFile = path.join(outDir, "hanzi-db.json");
  writeFileSync(outFile, JSON.stringify(db), "utf8");
  const mb = (JSON.stringify(db).length / (1024 * 1024)).toFixed(2);
  console.log(`Wrote ${outFile} (${mb} MiB), ${rows.length} hanzi rows, ${hanzi2radicals.length} hanzi2radicals links.`);
}

build();
