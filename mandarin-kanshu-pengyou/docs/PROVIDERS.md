# Provider profiles

OpenAI-compatible Chat Completions only. No arbitrary custom headers in MVP.

```ts
type ProviderProfile = {
  id: string;
  name: string;
  baseUrl: string;
  apiKeyReference: string;
  model: string;
  advanced: {
    temperature?: number;
    maxOutputTokens?: number;
    chatCompletionsPath?: string;
  };
  capabilities?: ProviderCapabilities;
};
```

## Task assignments

- `explainProfileId`
- `assessProfileId`
- `memoryProfileId`
- `fallbackProfileId?` — used only for explicit **Try a fresh explanation** when configured; never silent routing

## Test connection

Checks CORS/network, auth, basic completion, JSON/structured behavior, cancellation (best-effort), and token-usage metadata. Failures surface clearly (including CORS). Usage missing → show “Usage unavailable” (no cost calculator).

## Secrets

- Session map by default
- Persist to `providerSecrets` only with **Remember API keys**
- **Forget key** clears session + IndexedDB
- References dedupe by endpoint fingerprint + key material
- Redaction strips keys from errors/logs/exports
