/** Base64url (UTF-8) so the target word does not appear as plain text in the address bar. */

function utf8ToBase64Url(s: string): string {
  const bytes = new TextEncoder().encode(s);
  let bin = "";
  for (let i = 0; i < bytes.length; i++) {
    bin += String.fromCharCode(bytes[i]!);
  }
  const b64 = btoa(bin);
  return b64.replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/g, "");
}

function base64UrlToUtf8(token: string): string {
  const padded = token.replace(/-/g, "+").replace(/_/g, "/") + "===".slice((token.length + 3) % 4);
  const bin = atob(padded);
  const bytes = new Uint8Array(bin.length);
  for (let i = 0; i < bin.length; i++) {
    bytes[i] = bin.charCodeAt(i) as number;
  }
  return new TextDecoder().decode(bytes);
}

export function encodeVocabForUrl(vocab: string): string {
  return utf8ToBase64Url(vocab.trim());
}

function looksLikeBase64UrlToken(segment: string): boolean {
  return /^[A-Za-z0-9_-]+$/.test(segment) && segment.length >= 2;
}

export function decodeVocabToken(segment: string): string {
  const raw = segment.trim();
  if (!raw) throw new Error("Empty vocabulary token");
  if (looksLikeBase64UrlToken(raw)) {
    try {
      const decoded = base64UrlToUtf8(raw).trim();
      if (decoded) return decoded;
    } catch {
      // fall through to literal
    }
  }
  try {
    return decodeURIComponent(raw).trim();
  } catch {
    return raw;
  }
}

export type ParsedVocabHash = {
  vocab: string;
  /** Canonical hash so plain-text tokens are replaced in the navbar. */
  canonicalHash: string;
};

/** Expect `#vocab/<token>` where `<token>` is base64url(UTF-8) or a legacy plain/percent-encoded word. */
export function parseVocabHash(hash: string): ParsedVocabHash | null {
  const h = hash.startsWith("#") ? hash.slice(1) : hash;
  const trimmed = h.trim();
  if (!trimmed) return null;
  const parts = trimmed.split("/").filter(p => p.length > 0);
  if (parts[0] !== "vocab" || parts.length < 2) return null;
  const segment = parts.slice(1).join("/");
  const vocab = decodeVocabToken(segment);
  if (!vocab) return null;
  const encoded = encodeVocabForUrl(vocab);
  return { vocab, canonicalHash: `#vocab/${encoded}` };
}
