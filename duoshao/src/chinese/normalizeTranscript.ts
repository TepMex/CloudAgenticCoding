const EDGE_PUNCTUATION = /^[\s，。！？、；：,.!?;:'"“”‘’（）()【】\[\]《》<>]+|[\s，。！？、；：,.!?;:'"“”‘’（）()【】\[\]《》<>]+$/gu;

export function normalizeTranscript(raw: string): string {
  return raw.normalize("NFKC").trim().replace(EDGE_PUNCTUATION, "").replace(/\s+/gu, "");
}
