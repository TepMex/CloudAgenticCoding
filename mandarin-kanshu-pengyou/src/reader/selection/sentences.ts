/** Sentence-boundary expansion and multi-paragraph normalization for Chinese text. */

const SENTENCE_END = /[。！？]/;

export type TextRange = {
  start: number;
  end: number;
};

export type ExpandedSelection = {
  manualStart: number;
  manualEnd: number;
  expandedStart: number;
  expandedEnd: number;
  manualText: string;
  expandedText: string;
  sentenceCount: number;
  exceedsSoftLimit: boolean;
};

function clamp(n: number, min: number, max: number): number {
  return Math.max(min, Math.min(max, n));
}

/** Count Chinese sentences in a span (by 。！？). Empty → 0. */
export function countSentences(text: string): number {
  const trimmed = text.trim();
  if (!trimmed) return 0;
  const matches = trimmed.match(/[。！？]/g);
  if (!matches) return 1;
  // If text doesn't end with terminator, last fragment still counts
  const endsWithTerm = SENTENCE_END.test(trimmed[trimmed.length - 1] ?? "");
  return endsWithTerm ? matches.length : matches.length + 1;
}

/**
 * Expand [start, end) to full sentence boundaries within `text`.
 * Sentences end at 。！？. Crosses paragraph boundaries (\\n\\n or \\n).
 */
export function expandToSentenceBoundaries(
  text: string,
  start: number,
  end: number,
): TextRange {
  const s = clamp(start, 0, text.length);
  let e = clamp(end, 0, text.length);
  if (e < s) {
    const t = s;
    // swap
    return expandToSentenceBoundaries(text, e, t);
  }
  if (s === e) {
    // expand around caret-like empty selection to nearest sentence
    let left = s;
    while (left > 0 && !SENTENCE_END.test(text[left - 1]!)) left--;
    let right = e;
    while (right < text.length && !SENTENCE_END.test(text[right]!)) right++;
    if (right < text.length) right++;
    return { start: left, end: right };
  }

  let expandedStart = s;
  while (expandedStart > 0 && !SENTENCE_END.test(text[expandedStart - 1]!)) {
    expandedStart--;
  }

  let expandedEnd = e;
  // If we already end mid-sentence after a terminator at end-1, keep; else advance
  if (expandedEnd > 0 && SENTENCE_END.test(text[expandedEnd - 1]!)) {
    // already on boundary
  } else {
    while (expandedEnd < text.length && !SENTENCE_END.test(text[expandedEnd]!)) {
      expandedEnd++;
    }
    if (expandedEnd < text.length) expandedEnd++;
  }

  return { start: expandedStart, end: expandedEnd };
}

/** Normalize selection text: trim outer whitespace per paragraph, keep internal structure. */
export function normalizeMultiParagraph(text: string): string {
  const paragraphs = text.split(/\n{2,}|\r\n{2,}/);
  return paragraphs
    .map((p) =>
      p
        .split(/\n|\r\n/)
        .map((line) => line.trim())
        .filter(Boolean)
        .join(""),
    )
    .filter(Boolean)
    .join("\n\n");
}

export function buildExpandedSelection(
  text: string,
  start: number,
  end: number,
  softSentenceLimit = 5,
): ExpandedSelection {
  const range = expandToSentenceBoundaries(text, start, end);
  const manualText = text.slice(start, end);
  const expandedRaw = text.slice(range.start, range.end);
  const expandedText = normalizeMultiParagraph(expandedRaw);
  const sentenceCount = countSentences(expandedText);
  return {
    manualStart: start,
    manualEnd: end,
    expandedStart: range.start,
    expandedEnd: range.end,
    manualText,
    expandedText,
    sentenceCount,
    exceedsSoftLimit: sentenceCount > softSentenceLimit,
  };
}

/** Extract N paragraphs before/after a character range in plain text. */
export function nearbyParagraphs(
  text: string,
  start: number,
  end: number,
  beforeCount = 2,
  afterCount = 2,
): { before: string; after: string; paragraphs: string[] } {
  const parts = text.split(/\n{2,}|\r\n{2,}|\n/);
  // Map offsets
  const spans: { text: string; start: number; end: number }[] = [];
  let cursor = 0;
  for (const part of parts) {
    const idx = text.indexOf(part, cursor);
    if (idx < 0) continue;
    spans.push({ text: part, start: idx, end: idx + part.length });
    cursor = idx + part.length;
  }

  let first = spans.findIndex((s) => start >= s.start && start < s.end);
  let last = spans.findIndex((s) => end > s.start && end <= s.end);
  if (first < 0) first = 0;
  if (last < 0) last = spans.length - 1;

  const beforeSpans = spans.slice(Math.max(0, first - beforeCount), first);
  const afterSpans = spans.slice(last + 1, last + 1 + afterCount);

  return {
    before: beforeSpans.map((s) => s.text.trim()).filter(Boolean).join("\n\n"),
    after: afterSpans.map((s) => s.text.trim()).filter(Boolean).join("\n\n"),
    paragraphs: spans.map((s) => s.text),
  };
}
