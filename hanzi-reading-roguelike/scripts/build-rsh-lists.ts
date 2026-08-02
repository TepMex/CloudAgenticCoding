/**
 * Build slim RSH/RTH list data for the game from the knowledge-base JSON
 * plus pinyin readings from map-of-chinese characters.json.
 *
 * Usage (from hanzi-reading-roguelike/):
 *   bun run scripts/build-rsh-lists.ts [path-to-rsh_knowledge_base.json]
 */
import { readFileSync, writeFileSync, mkdirSync } from "node:fs";
import { dirname, join, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const __dirname = dirname(fileURLToPath(import.meta.url));
const root = join(__dirname, "..");
const repoRoot = join(root, "..");

type RawEntry = {
  frame: number;
  hanzi: string;
  keyword: string;
  rth_list: string;
  lesson: number;
  rth_list_name?: string;
};

type MapReading = {
  baseSyllable?: string;
  preferred?: boolean;
};

type MapChar = {
  character: string;
  simplified?: string;
  readings?: MapReading[];
};

/** Hand-filled toneless pinyin for chars missing from map-of-chinese. */
const FALLBACK_PINYIN: Record<string, string[]> = {
  尹: ["yin"],
  廿: ["nian"],
  酋: ["qiu"],
  襄: ["xiang"],
  韦: ["wei"],
  彦: ["yan"],
  炯: ["jiong"],
  亨: ["heng"],
  嘎: ["ga"],
  稣: ["su"],
  耶: ["ye"],
  奕: ["yi"],
};

function parseListId(full: string): { id: string; name: string } {
  const idx = full.indexOf(":");
  if (idx === -1) return { id: full.trim(), name: full.trim() };
  return {
    id: full.slice(0, idx).trim(),
    name: full.slice(idx + 1).trim(),
  };
}

function listSortKey(id: string): [number, number] {
  // RSH-L01 before RSH2-L01; numeric lesson within book
  const m = /^(RSH2?)-L(\d+)/i.exec(id);
  if (!m) return [99, 9999];
  const book = m[1]!.toUpperCase() === "RSH2" ? 2 : 1;
  return [book, Number(m[2])];
}

function readingsFor(hanzi: string, byChar: Map<string, MapChar>): string[] {
  const fallback = FALLBACK_PINYIN[hanzi];
  if (fallback) return fallback;

  const entry = byChar.get(hanzi);
  const readings = entry?.readings ?? [];
  const preferred = readings.filter((r) => r.preferred);
  const use = preferred.length > 0 ? preferred : readings;
  const bases = [
    ...new Set(
      use
        .map((r) => r.baseSyllable?.trim().toLowerCase())
        .filter((s): s is string => Boolean(s)),
    ),
  ];
  return bases;
}

const sourceArg = process.argv[2];
const sourcePath = resolve(
  sourceArg ?? join(root, "data/rsh_knowledge_base.slim.json"),
);

const mapPath = join(repoRoot, "map-of-chinese/src/data/characters.json");
const outPath = join(root, "src/data/rshLists.json");

const raw = JSON.parse(readFileSync(sourcePath, "utf8")) as RawEntry[];
const mapChars = JSON.parse(readFileSync(mapPath, "utf8")) as MapChar[];

const byChar = new Map<string, MapChar>();
for (const c of mapChars) {
  byChar.set(c.character, c);
  if (c.simplified) byChar.set(c.simplified, c);
}

type OutEntry = {
  frame: number;
  hanzi: string;
  keyword: string;
  pinyin: string[];
};

type OutList = {
  id: string;
  name: string;
  label: string;
  lesson: number;
  entries: OutEntry[];
};

const listMap = new Map<string, OutList>();
let skipped = 0;

for (const e of raw) {
  const pinyin = readingsFor(e.hanzi, byChar);
  if (pinyin.length === 0) {
    skipped += 1;
    console.warn(`No pinyin for ${e.hanzi} (frame ${e.frame}), skipping`);
    continue;
  }

  const { id, name } = parseListId(e.rth_list);
  let list = listMap.get(e.rth_list);
  if (!list) {
    list = {
      id,
      name: e.rth_list_name?.trim() || name,
      label: e.rth_list,
      lesson: e.lesson,
      entries: [],
    };
    listMap.set(e.rth_list, list);
  }

  // Dedupe by hanzi within a list (keep first frame)
  if (list.entries.some((x) => x.hanzi === e.hanzi)) continue;

  list.entries.push({
    frame: e.frame,
    hanzi: e.hanzi,
    keyword: e.keyword,
    pinyin,
  });
}

const lists = [...listMap.values()].sort((a, b) => {
  const [ab, al] = listSortKey(a.id);
  const [bb, bl] = listSortKey(b.id);
  return ab - bb || al - bl || a.id.localeCompare(b.id);
});

for (const list of lists) {
  list.entries.sort((a, b) => a.frame - b.frame);
}

mkdirSync(dirname(outPath), { recursive: true });
const payload = {
  source: "Remembering Simplified Hanzi (RSH) lists",
  generatedAt: new Date().toISOString(),
  listCount: lists.length,
  entryCount: lists.reduce((n, l) => n + l.entries.length, 0),
  lists,
};

writeFileSync(outPath, `${JSON.stringify(payload)}\n`, "utf8");
console.log(
  `Wrote ${outPath}: ${payload.listCount} lists, ${payload.entryCount} entries (skipped ${skipped})`,
);
