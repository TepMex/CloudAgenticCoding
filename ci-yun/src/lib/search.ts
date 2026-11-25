import type { Word } from "./types";

function fold(value: string): string {
  return value
    .trim()
    .toLowerCase()
    .normalize("NFD")
    .replace(/\p{M}/gu, "")
    .replace(/[\s·.'’]+/g, "");
}

/** Rank senses from the cloud. Exact simplified wins, then prefix, pinyin, gloss. */
export function rankWords(words: readonly Word[], query: string, limit = 8): Word[] {
  const raw = query.trim();
  if (!raw) return [];
  const needle = raw.toLowerCase();
  const flat = fold(raw);
  const scored: { score: number; word: Word }[] = [];

  for (const word of words) {
    const simplified = word.simplified;
    const pinyin = fold(word.pinyin);
    let score = 0;
    if (simplified === raw) score = 1000;
    else if (simplified.startsWith(raw)) score = 800;
    else if (simplified.includes(raw)) score = 600;
    else if (flat.length > 0 && pinyin.startsWith(flat)) score = 500;
    else if (flat.length >= 2 && pinyin.includes(flat)) score = 400;
    else if (needle.length >= 2 && word.meaningRu.toLowerCase().includes(needle)) score = 300;
    else if (needle.length >= 3 && word.meaning.toLowerCase().includes(needle)) score = 200;
    if (score > 0) scored.push({ score, word });
  }

  scored.sort(
    (left, right) =>
      right.score - left.score ||
      left.word.simplified.length - right.word.simplified.length ||
      left.word.id - right.word.id,
  );
  return scored.slice(0, limit).map((item) => item.word);
}
