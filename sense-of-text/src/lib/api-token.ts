export function normalizeApiToken(raw: string): string {
  return raw.trim().replace(/^Bearer\s+/i, "");
}

export function validateApiToken(raw: string): string | null {
  const token = normalizeApiToken(raw);

  if (!token) {
    return "API token is required for LLM analysis. Add it in Settings.";
  }
  if (/\s/.test(token)) {
    return "API token must not contain spaces or line breaks. Paste only the key from your provider.";
  }
  if (token.length > 512) {
    return "API token is too long. Check Settings — you may have pasted text instead of an API key.";
  }
  if (/[^\t\x20-\x7e]/.test(token)) {
    return "API token must use ASCII characters only (e.g. sk-...). Check Settings.";
  }

  return null;
}
