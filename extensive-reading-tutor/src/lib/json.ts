/** Pull a JSON object or array out of model text, including fenced blocks. */
export function extractJsonText(raw: string): string {
  const trimmed = raw.trim();
  const fence = trimmed.match(/```(?:json)?\s*([\s\S]*?)```/i);
  const source = fence?.[1]?.trim() || trimmed;

  const objStart = source.indexOf("{");
  const arrStart = source.indexOf("[");
  let start = -1;
  if (objStart >= 0 && arrStart >= 0) start = Math.min(objStart, arrStart);
  else start = Math.max(objStart, arrStart);
  if (start < 0) throw new Error("В ответе модели нет JSON.");

  const opener = source[start];
  const closer = opener === "{" ? "}" : "]";
  let depth = 0;
  let inString = false;
  let escape = false;
  for (let i = start; i < source.length; i++) {
    const ch = source[i]!;
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
      if (depth === 0) return source.slice(start, i + 1);
    }
  }
  throw new Error("JSON в ответе модели оборван.");
}

export function parseJsonLoose(raw: string): unknown {
  return JSON.parse(extractJsonText(raw)) as unknown;
}
