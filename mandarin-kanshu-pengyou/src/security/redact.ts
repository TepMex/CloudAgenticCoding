const KEY_PATTERNS = [
  /sk-[a-zA-Z0-9]{8,}/g,
  /Bearer\s+[A-Za-z0-9._\-]+/gi,
  /api[_-]?key["'\s:=]+["']?[^\s"'&,}]+/gi,
];

/** Redact likely API keys / bearer tokens from any string. */
export function redactSecrets(input: string): string {
  let out = input;
  for (const re of KEY_PATTERNS) {
    out = out.replace(re, "[REDACTED]");
  }
  return out;
}

export function safeErrorMessage(err: unknown): string {
  if (err instanceof Error) return redactSecrets(err.message);
  return redactSecrets(String(err));
}

export function assertNoSecretInExport(payload: unknown): void {
  const json = JSON.stringify(payload);
  if (/sk-[a-zA-Z0-9]{20,}/.test(json) || /"apiKey"\s*:\s*"[^"]+"/.test(json)) {
    throw new Error("Refusing to export payload that appears to contain secrets");
  }
}
