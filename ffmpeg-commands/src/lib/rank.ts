import type { Chunk, SearchHit } from "./types.ts";

export function cosineSimilarity(left: ArrayLike<number>, right: ArrayLike<number>): number {
  const length = left.length;
  if (length === 0 || right.length !== length) return 0;
  let dot = 0;
  let leftNorm = 0;
  let rightNorm = 0;
  for (let index = 0; index < length; index++) {
    const a = left[index] ?? 0;
    const b = right[index] ?? 0;
    dot += a * b;
    leftNorm += a * a;
    rightNorm += b * b;
  }
  const denom = Math.sqrt(leftNorm) * Math.sqrt(rightNorm);
  return denom === 0 ? 0 : dot / denom;
}

export function rankChunks(
  query: ArrayLike<number>,
  chunks: Chunk[],
  vectors: ArrayLike<number>[],
  k: number,
): SearchHit[] {
  const scored: SearchHit[] = [];
  const limit = Math.min(chunks.length, vectors.length);
  for (let index = 0; index < limit; index++) {
    const chunk = chunks[index];
    const vector = vectors[index];
    if (!chunk || !vector) continue;
    scored.push(hitFrom(chunk, cosineSimilarity(query, vector)));
  }
  scored.sort((a, b) => b.score - a.score);
  return diversify(scored, k);
}

export function diversify(hits: SearchHit[], k: number): SearchHit[] {
  const seen = new Set<string>();
  const unique: SearchHit[] = [];
  for (const hit of hits) {
    const key = (hit.command ?? hit.signature ?? hit.title).replace(/\s+/g, " ").trim();
    if (seen.has(key)) continue;
    seen.add(key);
    unique.push(hit);
    if (unique.length === k) break;
  }
  return unique;
}

function hitFrom(chunk: Chunk, score: number): SearchHit {
  return {
    id: chunk.id,
    title: chunk.title,
    summary: chunk.summary,
    command: chunk.command,
    signature: chunk.signature,
    synthesized: chunk.synthesized,
    url: chunk.url,
    page: chunk.page,
    family: chunk.family,
    kind: chunk.kind,
    score,
  };
}
