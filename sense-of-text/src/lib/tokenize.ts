export const MAX_TOKENS = 120;

export type Token = {
  text: string;
  start: number;
  end: number;
};

const HAN_REGEX = /\p{Script=Han}/u;

function isHanChar(char: string): boolean {
  return HAN_REGEX.test(char);
}

function tokenizeRunWithSegmenter(text: string, start: number, end: number): Token[] {
  const slice = text.slice(start, end);
  if (!slice.trim()) return [];

  try {
    const locale = typeof navigator !== "undefined" ? navigator.language : "en";
    const segmenter = new Intl.Segmenter(locale, { granularity: "word" });
    const tokens: Token[] = [];
    for (const { segment, index, isWordLike } of segmenter.segment(slice)) {
      if (!isWordLike || !segment.trim()) continue;
      tokens.push({
        text: segment,
        start: start + index,
        end: start + index + segment.length,
      });
    }
    if (tokens.length > 0) return tokens;
  } catch {
    /* fallback below */
  }

  const tokens: Token[] = [];
  const re = /\S+/g;
  let match: RegExpExecArray | null;
  while ((match = re.exec(slice)) !== null) {
    tokens.push({
      text: match[0],
      start: start + match.index,
      end: start + match.index + match[0].length,
    });
  }
  return tokens;
}

export function tokenize(text: string): Token[] {
  if (!text.trim()) return [];

  const tokens: Token[] = [];
  let i = 0;

  while (i < text.length) {
    const char = text[i]!;
    if (isHanChar(char)) {
      tokens.push({ text: char, start: i, end: i + 1 });
      i += 1;
      continue;
    }

    let j = i;
    while (j < text.length && !isHanChar(text[j]!)) {
      j += 1;
    }
    tokens.push(...tokenizeRunWithSegmenter(text, i, j));
    i = j;
  }

  return tokens;
}

export function deleteToken(text: string, tokens: Token[], index: number): string {
  const token = tokens[index];
  if (!token) return text;
  return text.slice(0, token.start) + text.slice(token.end);
}

export function exceedsTokenLimit(tokens: Token[]): boolean {
  return tokens.length > MAX_TOKENS;
}

export function tokenLimitMessage(count: number): string {
  return `Text has ${count} tokens; maximum is ${MAX_TOKENS} for analysis.`;
}
