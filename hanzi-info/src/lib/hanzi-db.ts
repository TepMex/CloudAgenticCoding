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
  const t = text.trim();
  if (!t) return null;
  const seg = new Intl.Segmenter("zh-Hans", { granularity: "grapheme" });
  const first = [...seg.segment(t)][0]?.segment;
  return first ?? [...t][0] ?? null;
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
