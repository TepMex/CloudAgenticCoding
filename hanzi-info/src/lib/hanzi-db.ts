import type { HanziDatabase, HanziRow } from "./hanzi-types";

let cache: HanziDatabase | null = null;

export async function loadHanziDatabase(): Promise<HanziDatabase> {
  if (cache) return cache;
  const u = new URL(window.location.href);
  u.hash = "";
  u.search = "";
  let dir = u.pathname;
  if (!dir.endsWith("/")) {
    if (dir.endsWith(".html")) {
      dir = dir.replace(/[^/]+$/, "");
    } else {
      dir = `${dir}/`;
    }
  }
  u.pathname = `${dir}hanzi-db.json`;
  const res = await fetch(u);
  if (!res.ok) throw new Error(`Failed to load hanzi-db.json (${res.status})`);
  cache = (await res.json()) as HanziDatabase;
  return cache;
}

export function firstGrapheme(text: string): string | null {
  const all = allHanziGraphemes(text);
  return all[0] ?? null;
}

/** Ordered Han script graphemes (CJK unified + compatibility ideographs); skips spaces and Latin digits, etc. */
export function allHanziGraphemes(text: string): string[] {
  const t = text.trim().normalize("NFC");
  if (!t) return [];
  const han = /^\p{Script=Han}$/u;
  try {
    const seg = new Intl.Segmenter("und", { granularity: "grapheme" });
    const out: string[] = [];
    for (const { segment } of seg.segment(t)) {
      const s = segment.normalize("NFC");
      if (han.test(s)) out.push(s);
    }
    return out;
  } catch {
    return [...t].filter(ch => han.test(ch.normalize("NFC")));
  }
}

const HASH_RE = /^#\/?hanzi\/([^/]+)$/;

export function parseHanziHash(hash: string): string | null {
  const m = hash.trim().match(HASH_RE);
  if (!m?.[1]) return null;
  return decodeURIComponent(m[1]);
}

export function canonicalHanziHash(hanzi: string): string {
  return `#/hanzi/${encodeURIComponent(hanzi)}`;
}

const SETTINGS_HASH = /^#\/?settings\/?$/i;

export function isSettingsHash(hash: string): boolean {
  return SETTINGS_HASH.test(hash.trim());
}

export function canonicalSettingsHash(): string {
  return "#/settings";
}

export function radicalsForCharacter(db: HanziDatabase, hanzi: string): HanziRow[] {
  const hid = db.by_hanzi[hanzi];
  if (hid === undefined) return [];
  const out: HanziRow[] = [];
  const seen = new Set<number>();
  for (const [from, to] of db.hanzi2radicals) {
    if (from !== hid) continue;
    if (seen.has(to)) continue;
    seen.add(to);
    const row = db.hanzi[to - 1];
    if (row) out.push(row);
  }
  return out;
}
