export function uuid(): string {
  if (typeof crypto !== "undefined" && "randomUUID" in crypto) return crypto.randomUUID();
  return "xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx".replace(/[xy]/g, (c) => {
    const r = (Math.random() * 16) | 0;
    const v = c === "x" ? r : (r & 0x3) | 0x8;
    return v.toString(16);
  });
}

export function clamp(n: number, min: number, max: number): number { return Math.max(min, Math.min(max, n)); }

const KEY_PATTERNS: RegExp[] = [
  /sk-[A-Za-z0-9_-]{16,}/g,
  /sk-ant-[A-Za-z0-9_-]{16,}/g,
  /Bearer\s+[A-Za-z0-9_.~+/=-]{16,}/gi,
  /["'`](?:api[_-]?key|apikey|authorization|secret|token)["'`]\s*[:=]\s*["'`]([A-Za-z0-9_.~+/=-]{12,})["'`]/gi,
];

export function redactKeys(input: string): string {
  let out = input;
  for (const re of KEY_PATTERNS) out = out.replace(re, "[redacted]");
  return out;
}

export function isOnline(): boolean { return typeof navigator !== "undefined" ? navigator.onLine : true; }

export function approxTokens(text: string): number {
  if (!text) return 0;
  let cjk = 0, other = 0;
  for (const ch of text) {
    const code = ch.codePointAt(0)!;
    if ((code >= 0x4e00 && code <= 0x9fff) || (code >= 0x3400 && code <= 0x4dbf) || (code >= 0xf900 && code <= 0xfaff)) cjk++;
    else other++;
  }
  return Math.ceil(cjk / 2 + other / 4);
}
