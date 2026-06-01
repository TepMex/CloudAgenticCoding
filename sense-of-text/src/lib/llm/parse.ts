import { z } from "zod";
import type { Token } from "@/lib/tokenize";

const LlmResponseSchema = z.object({
  scores: z.array(
    z.object({
      index: z.number().int().nonnegative(),
      importance: z.number(),
    }),
  ),
});

function extractJsonObject(raw: string): string {
  const trimmed = raw.trim();
  const fenceMatch = trimmed.match(/```(?:json)?\s*([\s\S]*?)```/);
  if (fenceMatch?.[1]) return fenceMatch[1].trim();
  const start = trimmed.indexOf("{");
  const end = trimmed.lastIndexOf("}");
  if (start >= 0 && end > start) return trimmed.slice(start, end + 1);
  return trimmed;
}

function normalizeImportance(value: number): number {
  if (value > 1) return Math.min(1, value / 100);
  if (value < 0) return 0;
  return value;
}

export function parseLlmScores(
  content: string,
  tokenCount: number,
): number[] {
  const jsonStr = extractJsonObject(content);
  const parsed = LlmResponseSchema.parse(JSON.parse(jsonStr));
  const scores = new Array<number>(tokenCount).fill(0);
  for (const item of parsed.scores) {
    if (item.index >= 0 && item.index < tokenCount) {
      scores[item.index] = normalizeImportance(item.importance);
    }
  }
  return scores;
}

export function alignScoresToTokens(scores: number[], tokens: Token[]): number[] {
  if (scores.length === tokens.length) return scores;
  const out = new Array<number>(tokens.length).fill(0);
  for (let i = 0; i < Math.min(scores.length, tokens.length); i++) {
    out[i] = scores[i] ?? 0;
  }
  return out;
}
