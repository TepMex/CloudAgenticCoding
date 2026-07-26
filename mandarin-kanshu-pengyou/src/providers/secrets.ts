/** Session and optional persistent API key storage with deduplication. */

import { db } from "../db/database";
import type { ProviderSecret } from "../shared/domain";

const sessionKeys = new Map<string, string>();

export function fingerprintEndpoint(baseUrl: string): string {
  try {
    const u = new URL(baseUrl);
    return `${u.protocol}//${u.host}${u.pathname}`.replace(/\/$/, "");
  } catch {
    return baseUrl.trim().replace(/\/$/, "");
  }
}

export function secretReference(baseUrl: string, apiKey: string): string {
  const fp = fingerprintEndpoint(baseUrl);
  // Stable reference without storing the key in the reference id itself
  let h = 0;
  const material = `${fp}::${apiKey}`;
  for (let i = 0; i < material.length; i++) h = (h * 31 + material.charCodeAt(i)) | 0;
  return `sec_${fp.replace(/[^a-zA-Z0-9]/g, "_").slice(0, 40)}_${(h >>> 0).toString(16)}`;
}

export async function setApiKey(
  reference: string,
  baseUrl: string,
  apiKey: string,
  persist: boolean,
): Promise<void> {
  sessionKeys.set(reference, apiKey);
  if (persist) {
    const record: ProviderSecret = {
      reference,
      apiKey,
      baseUrlFingerprint: fingerprintEndpoint(baseUrl),
      updatedAt: Date.now(),
    };
    await db.providerSecrets.put(record);
  } else {
    await db.providerSecrets.delete(reference);
  }
}

export async function getApiKey(reference: string): Promise<string | undefined> {
  if (sessionKeys.has(reference)) return sessionKeys.get(reference);
  const stored = await db.providerSecrets.get(reference);
  if (stored?.apiKey) {
    sessionKeys.set(reference, stored.apiKey);
    return stored.apiKey;
  }
  return undefined;
}

export async function forgetApiKey(reference: string): Promise<void> {
  sessionKeys.delete(reference);
  await db.providerSecrets.update(reference, { apiKey: undefined });
  await db.providerSecrets.delete(reference);
}

export async function forgetAllApiKeys(): Promise<void> {
  sessionKeys.clear();
  await db.providerSecrets.clear();
}

export function clearSessionKeys(): void {
  sessionKeys.clear();
}
