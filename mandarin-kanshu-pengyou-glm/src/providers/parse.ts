export function extractJson(raw: string): unknown | null {
  if (!raw) return null;
  const trimmed = raw.trim();
  const direct = tryJson(trimmed);
  if (direct !== undefined) return direct;
  const fence = trimmed.match(/```(?:json)?\s*([\s\S]*?)```/i);
  if (fence) {
    const v = tryJson(fence[1].trim());
    if (v !== undefined) return v;
  }
  const obj = extractBalanced(trimmed, "{", "}");
  if (obj !== null) {
    const v = tryJson(obj);
    if (v !== undefined) return v;
  }
  const arr = extractBalanced(trimmed, "[", "]");
  if (arr !== null) {
    const v = tryJson(arr);
    if (v !== undefined) return v;
  }
  return null;
}

function tryJson(s: string): unknown | undefined {
  try { return JSON.parse(s); } catch { return undefined; }
}

function extractBalanced(s: string, open: string, close: string): string | null {
  const start = s.indexOf(open);
  if (start < 0) return null;
  let depth = 0, inStr = false, escape = false;
  for (let i = start; i < s.length; i++) {
    const ch = s[i];
    if (inStr) {
      if (escape) escape = false;
      else if (ch === "\\") escape = true;
      else if (ch === '"') inStr = false;
    } else {
      if (ch === '"') inStr = true;
      else if (ch === open) depth++;
      else if (ch === close) { depth--; if (depth === 0) return s.slice(start, i + 1); }
    }
  }
  return null;
}

export function validateWithSchema<T>(
  parsed: unknown,
  schema: { safeParse: (x: unknown) => { success: boolean; data?: T; error?: { issues: unknown[] } } }
): { ok: boolean; data?: T; error?: string } {
  const r = schema.safeParse(parsed);
  if (r.success) return { ok: true, data: r.data };
  return { ok: false, error: JSON.stringify(r.error?.issues ?? "validation error") };
}
