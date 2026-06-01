export type CachedAnalysis = {
  scores: number[];
};

const cache = new Map<string, CachedAnalysis>();

export async function hashText(text: string): Promise<string> {
  const data = new TextEncoder().encode(text);
  const digest = await crypto.subtle.digest("SHA-256", data);
  return Array.from(new Uint8Array(digest))
    .map(b => b.toString(16).padStart(2, "0"))
    .join("");
}

export function buildCacheKey(textHash: string, modelId: string): string {
  return `${textHash}:${modelId}`;
}

export function getCachedAnalysis(key: string): CachedAnalysis | undefined {
  return cache.get(key);
}

export function setCachedAnalysis(key: string, entry: CachedAnalysis): void {
  cache.set(key, entry);
}
