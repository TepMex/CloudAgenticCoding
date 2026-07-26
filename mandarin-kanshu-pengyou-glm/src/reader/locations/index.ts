import type { ReaderLocation } from "../../shared/domain";

export function locationKey(loc: ReaderLocation): string {
  return [loc.bookId, loc.spineItemId, loc.textQuote.slice(0, 80)].join("|");
}

export function recoverLocationOffset(loc: ReaderLocation, chapterText: string): number | null {
  const norm = (s: string) => s.replace(/\s+/g, "");
  const q = norm(loc.textQuote);
  if (!q) return null;
  const normText = norm(chapterText);
  let idx = normText.indexOf(q);
  if (idx >= 0) return idx;
  if (loc.prefix) {
    const p = norm(loc.prefix).slice(-30);
    idx = normText.indexOf(p + q.slice(0, Math.min(20, q.length)));
    if (idx >= 0) return idx + p.length;
  }
  if (loc.suffix) {
    const s = norm(loc.suffix).slice(0, 30);
    idx = normText.indexOf(q.slice(-Math.min(20, q.length)) + s);
    if (idx >= 0) return idx;
  }
  if (q.length >= 6) {
    let best = -1, bestLen = 0;
    const minLen = Math.ceil(q.length * 0.7);
    for (let i = 0; i + minLen <= normText.length; i++) {
      let match = 0;
      for (let j = 0; j < q.length && i + j < normText.length; j++) {
        if (normText[i + j] === q[j]) match++; else break;
      }
      if (match >= minLen && match > bestLen) { best = i; bestLen = match; }
    }
    if (best >= 0) return best;
  }
  return null;
}

export function locationEquals(a: ReaderLocation, b: ReaderLocation): boolean {
  return a.bookId === b.bookId && a.spineItemId === b.spineItemId && a.textQuote === b.textQuote;
}
