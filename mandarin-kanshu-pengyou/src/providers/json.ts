import { z } from "zod";

/** Extract JSON object/array from raw model text, including fenced blocks. */
export function extractJsonText(raw: string): string {
  const trimmed = raw.trim();
  const fence = trimmed.match(/```(?:json)?\s*([\s\S]*?)```/i);
  if (fence?.[1]) return fence[1].trim();

  const objStart = trimmed.indexOf("{");
  const arrStart = trimmed.indexOf("[");
  let start = -1;
  if (objStart >= 0 && arrStart >= 0) start = Math.min(objStart, arrStart);
  else start = Math.max(objStart, arrStart);
  if (start < 0) throw new Error("No JSON found in model response");

  const opener = trimmed[start];
  const closer = opener === "{" ? "}" : "]";
  let depth = 0;
  let inString = false;
  let escape = false;
  for (let i = start; i < trimmed.length; i++) {
    const ch = trimmed[i]!;
    if (inString) {
      if (escape) escape = false;
      else if (ch === "\\") escape = true;
      else if (ch === '"') inString = false;
      continue;
    }
    if (ch === '"') {
      inString = true;
      continue;
    }
    if (ch === opener) depth++;
    else if (ch === closer) {
      depth--;
      if (depth === 0) return trimmed.slice(start, i + 1);
    }
  }
  throw new Error("Unbalanced JSON in model response");
}

export function parseJsonLoose(raw: string): unknown {
  const jsonText = extractJsonText(raw);
  return JSON.parse(jsonText);
}

export function validateWithSchema<T>(schema: z.ZodType<T>, raw: string): T {
  const data = parseJsonLoose(raw);
  return schema.parse(data);
}

export type ParseResult<T> =
  | { ok: true; data: T }
  | { ok: false; error: string; raw: string };

export function tryParseWithSchema<T>(schema: z.ZodType<T>, raw: string): ParseResult<T> {
  try {
    return { ok: true, data: validateWithSchema(schema, raw) };
  } catch (e) {
    return {
      ok: false,
      error: e instanceof Error ? e.message : String(e),
      raw,
    };
  }
}
