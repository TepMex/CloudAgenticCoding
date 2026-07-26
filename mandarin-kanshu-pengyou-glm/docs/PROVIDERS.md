# Provider profiles

Each profile is OpenAI-compatible (Chat Completions endpoint). No arbitrary custom headers in the MVP.

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

```ts
type TaskModelAssignments = {
  bookId: string;
  explainProfileId: string;
  assessProfileId: string;
  memoryProfileId: string;
  fallbackProfileId?: string;
};
```

Simplification and assessment may use different models. The memory role works with models in the ~4B–32B range, but capability testing (not size) determines fitness.

## Capabilities test

`Test connection` probes: browser CORS access, chat completions compatibility, authentication, basic text completion, JSON-mode, JSON-schema structured output, token-usage metadata. Results are stored on the profile. If structured output is unsupported, the client falls back to JSON mode, then plain text + defensive JSON extraction + one repair attempt.

## Behavior

- No visible model requests run concurrently by default. The visible Explain/Understand request runs first; only after it succeeds may a memory update begin.
- Never silently switch providers. "Try a fresh explanation" uses the fallback profile only when one is explicitly configured.
- No streaming. No simulated typing. Calm loading state.

## Presets (editable examples)

- **Local small** — `http://localhost:11434`, `qwen2.5:7b`
- **Hosted lite** — `https://api.openai.com/v1`, `gpt-4o-mini`
- **Strong primary** — `https://api.openai.com/v1`, `gpt-4o`