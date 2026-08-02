/** Normalize typed pinyin for comparison (ASCII, lowercase, trim). */
export function normalizePinyin(s: string): string {
  return s
    .trim()
    .toLowerCase()
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "");
}

/**
 * Normalize English keyword answers: casefold, curly quotes → straight,
 * drop most punctuation, collapse whitespace.
 */
export function normalizeMeaning(s: string): string {
  return s
    .trim()
    .toLowerCase()
    .normalize("NFKC")
    .replace(/[’‘]/g, "'")
    .replace(/[“”]/g, '"')
    .replace(/[.?!,;:()/\\[\]{}]/g, " ")
    .replace(/['"]/g, "")
    .replace(/\s+/g, " ")
    .trim();
}

export function matchesPinyin(answer: string, expected: string[]): boolean {
  const n = normalizePinyin(answer);
  if (!n) return false;
  return expected.some((p) => normalizePinyin(p) === n);
}

export function matchesMeaning(answer: string, keyword: string): boolean {
  const n = normalizeMeaning(answer);
  if (!n) return false;
  const expected = normalizeMeaning(keyword);
  if (n === expected) return true;

  // Also accept the keyword with parenthetical notes removed, e.g. "I (literary)" → "i"
  const withoutParens = normalizeMeaning(keyword.replace(/\([^)]*\)/g, " "));
  return withoutParens.length > 0 && n === withoutParens;
}
