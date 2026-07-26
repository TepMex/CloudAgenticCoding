import type { ReaderLocation } from "../../shared/domain";

const DEFAULT_AFFIX = 24;

export function buildReaderLocation(input: {
  bookId: string;
  spineItemId: string;
  chapterText: string;
  start: number;
  end: number;
  epubCfi?: string;
  approximateProgress?: number;
  affixLength?: number;
}): ReaderLocation {
  const affix = input.affixLength ?? DEFAULT_AFFIX;
  const quote = input.chapterText.slice(input.start, input.end);
  const prefix = input.chapterText.slice(Math.max(0, input.start - affix), input.start);
  const suffix = input.chapterText.slice(input.end, Math.min(input.chapterText.length, input.end + affix));
  return {
    bookId: input.bookId,
    spineItemId: input.spineItemId,
    epubCfi: input.epubCfi,
    textQuote: quote,
    prefix,
    suffix,
    approximateProgress: input.approximateProgress,
  };
}

export function serializeLocation(loc: ReaderLocation): string {
  return JSON.stringify(loc);
}

export function deserializeLocation(raw: string): ReaderLocation {
  const parsed = JSON.parse(raw) as ReaderLocation;
  if (!parsed.bookId || !parsed.spineItemId || typeof parsed.textQuote !== "string") {
    throw new Error("Invalid ReaderLocation");
  }
  return parsed;
}

/**
 * Recover a character range in chapter plain text from quote + prefix/suffix.
 * Prefers exact prefix+quote+suffix, then quote-only unique match, then first quote.
 */
export function recoverRangeFromLocation(
  chapterText: string,
  location: ReaderLocation,
): { start: number; end: number; method: "exact" | "quote_unique" | "quote_first" | "failed" } {
  const { textQuote, prefix, suffix } = location;
  if (!textQuote) return { start: -1, end: -1, method: "failed" };

  const exact = prefix + textQuote + suffix;
  if (prefix || suffix) {
    const idx = chapterText.indexOf(exact);
    if (idx >= 0) {
      const start = idx + prefix.length;
      return { start, end: start + textQuote.length, method: "exact" };
    }
  }

  const positions: number[] = [];
  let from = 0;
  while (from <= chapterText.length) {
    const i = chapterText.indexOf(textQuote, from);
    if (i < 0) break;
    positions.push(i);
    from = i + 1;
  }

  if (positions.length === 1) {
    const start = positions[0]!;
    return { start, end: start + textQuote.length, method: "quote_unique" };
  }
  if (positions.length > 1) {
    // Prefer match whose nearby prefix/suffix best resembles stored affixes
    let best = positions[0]!;
    let bestScore = -1;
    for (const p of positions) {
      const pre = chapterText.slice(Math.max(0, p - prefix.length), p);
      const suf = chapterText.slice(p + textQuote.length, p + textQuote.length + suffix.length);
      let score = 0;
      if (pre.endsWith(prefix) || prefix.endsWith(pre)) score += 2;
      if (suf.startsWith(suffix) || suffix.startsWith(suf)) score += 2;
      if (score > bestScore) {
        bestScore = score;
        best = p;
      }
    }
    return { start: best, end: best + textQuote.length, method: "quote_first" };
  }

  return { start: -1, end: -1, method: "failed" };
}
