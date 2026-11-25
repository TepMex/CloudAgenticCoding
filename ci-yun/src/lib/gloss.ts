import { SOURCE_LABEL, type Word } from "./types";

export function glossOf(word: Word): string {
  return word.meaningRu || word.meaning;
}

export function sourceLine(word: Word): string {
  const names = word.sources.map((source) => SOURCE_LABEL[source] ?? source);
  const levels = word.hsk.join(" ");
  return levels ? `${names.join(" · ")} · ${levels}` : names.join(" · ");
}
