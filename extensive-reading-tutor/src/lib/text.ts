import type { Gloss, Token } from "./types.ts";

/** Stable id so the same headword stays one card across lessons. */
export function glossIdFor(hanzi: string): string {
  return (
    "w-" +
    Array.from(hanzi)
      .map(ch => (ch.codePointAt(0) ?? 0).toString(16))
      .join("")
  );
}

export function isHan(text: string): boolean {
  return /[\u3400-\u9fff]/.test(text);
}

/** Longest-match segmentation. Unlisted characters stay single tokens. */
export function tokenize(passage: string, glosses: Pick<Gloss, "id" | "hanzi">[]): Token[] {
  const words = glosses
    .filter(g => g.hanzi.length > 0)
    .slice()
    .sort((a, b) => b.hanzi.length - a.hanzi.length || a.hanzi.localeCompare(b.hanzi));
  const out: Token[] = [];
  let i = 0;
  while (i < passage.length) {
    let matched = false;
    for (const word of words) {
      if (passage.startsWith(word.hanzi, i)) {
        out.push({ t: word.hanzi, glossId: word.id });
        i += word.hanzi.length;
        matched = true;
        break;
      }
    }
    if (matched) continue;
    const ch = passage[i];
    if (ch) out.push({ t: ch });
    i += 1;
  }
  return out;
}

export function passageOf(tokens: Token[]): string {
  return tokens.map(token => token.t).join("");
}

export function plural(n: number, one: string, few: string, many: string): string {
  const n10 = n % 10;
  const n100 = n % 100;
  if (n10 === 1 && n100 !== 11) return one;
  if (n10 >= 2 && n10 <= 4 && (n100 < 12 || n100 > 14)) return few;
  return many;
}

export type MapCell = "done" | "current" | "unread";

/** Squares for the book progress map. A finished book is all done. */
export function readingMap(progress: number, cells = 42): MapCell[] {
  const total = Math.max(8, cells);
  if (progress >= 100) return Array.from({ length: total }, () => "done" as const);
  const doneCount = Math.max(0, Math.min(total - 1, Math.round((Math.max(0, progress) / 100) * total) - 1));
  return Array.from({ length: total }, (_, index) => {
    if (index < doneCount) return "done";
    if (index === doneCount) return "current";
    return "unread";
  });
}
