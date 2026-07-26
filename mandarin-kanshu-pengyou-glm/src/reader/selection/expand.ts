const SENTENCE_END = new Set(["。", "！", "？", "!", "?", "…"]);
const SOFT_BREAK = new Set(["，", ",", "；", ";", "：", ":"]);

export type ExpandedPassage = {
  passage: string; manualSelection: string; sentenceCount: number;
  crossesParagraphs: boolean; longWarning: boolean;
};

export function expandSelection(
  paragraphs: string[], startPara: number, startOffset: number,
  endPara: number, endOffset: number, manualText: string
): ExpandedPassage {
  if (paragraphs.length === 0) return { passage: manualText, manualSelection: manualText, sentenceCount: 1, crossesParagraphs: false, longWarning: false };
  const sp = Math.max(0, Math.min(startPara, paragraphs.length - 1));
  const ep = Math.max(sp, Math.min(endPara, paragraphs.length - 1));
  const startP = paragraphs[sp];
  const endP = paragraphs[ep];
  const expandedStart = expandBackward(startP, startOffset);
  const expandedEnd = expandForward(endP, endOffset);
  let passage: string;
  if (sp === ep) passage = startP.slice(expandedStart, expandedEnd);
  else {
    const parts = [startP.slice(expandedStart)];
    for (let i = sp + 1; i < ep; i++) parts.push(paragraphs[i]);
    parts.push(endP.slice(0, expandedEnd));
    passage = parts.join("\n\n");
  }
  passage = passage.trim();
  const sentenceCount = countSentences(passage);
  return { passage, manualSelection: manualText, sentenceCount, crossesParagraphs: ep > sp, longWarning: sentenceCount > 5 };
}

function expandBackward(text: string, offset: number): number {
  let i = Math.min(offset, text.length);
  while (i > 0) {
    const ch = text[i - 1];
    if (SENTENCE_END.has(ch)) return i;
    if (SOFT_BREAK.has(ch) && i < offset - 1) return i;
    i--;
  }
  return 0;
}

function expandForward(text: string, offset: number): number {
  let i = Math.max(offset, 0);
  while (i < text.length) {
    const ch = text[i];
    if (SENTENCE_END.has(ch)) return i + 1;
    i++;
  }
  return text.length;
}

export function countSentences(text: string): number {
  if (!text) return 0;
  let count = 0, inSentence = false;
  for (const ch of text) {
    if (SENTENCE_END.has(ch)) { count++; inSentence = false; }
    else if (!/\s/.test(ch) && !SOFT_BREAK.has(ch)) inSentence = true;
  }
  if (inSentence) count++;
  return Math.max(1, count);
}

export function normalizeSelection(rawText: string): string {
  return rawText.split(/\n+/).map((p) => p.replace(/\s+/g, " ").trim()).filter(Boolean).join("\n\n");
}

export function gatherContext(
  paragraphs: string[], startPara: number, endPara: number,
  beforeCount: number, afterCount: number
): { before: string[]; after: string[] } {
  return {
    before: paragraphs.slice(Math.max(0, startPara - beforeCount), startPara),
    after: paragraphs.slice(endPara + 1, Math.min(paragraphs.length, endPara + 1 + afterCount)),
  };
}

export function locateInParagraphs(paragraphs: string[], snippet: string): { para: number; offset: number } | null {
  const norm = (s: string) => s.replace(/\s+/g, "");
  const target = norm(snippet);
  if (!target) return null;
  for (let i = 0; i < paragraphs.length; i++) {
    const off = norm(paragraphs[i]).indexOf(target);
    if (off >= 0) return { para: i, offset: off };
  }
  return null;
}
