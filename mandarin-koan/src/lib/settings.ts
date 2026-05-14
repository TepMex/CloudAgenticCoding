const STORAGE = {
  apiBaseUrl: "mandarin_koan_api_base_url",
  apiKey: "mandarin_koan_api_key",
  model: "mandarin_koan_model",
  storyPrompt: "mandarin_koan_story_prompt",
} as const;

const DEFAULT_MODEL = "gpt-4o-mini";

export const DEFAULT_STORY_PROMPT = `You write short Mandarin Chinese stories for vocabulary cloze practice.

Target word (the learner will NOT see it yet; you must hide it): {{VOCAB}}

Rules:
- Write 3–6 sentences in **Mandarin Chinese** (Simplified characters preferred unless the target word clearly needs Traditional).
- The story must naturally use the target word **once** in the correct sense.
- Replace that single occurrence with exactly three underscores: ___
- Do not put the target word anywhere else in the story (no pinyin of the full word as a hint in parentheses).
- Keep tone calm and concrete; avoid meta commentary.

Respond with **JSON only**:
{"story": "<string with ___ for the blank>"}`;

export type KoanSettings = {
  apiBaseUrl: string;
  apiKey: string;
  model: string;
  /** User-editable instructions sent to the model (use {{VOCAB}} for the hidden word). */
  storyPrompt: string;
};

export function loadSettings(): KoanSettings {
  if (typeof localStorage === "undefined") {
    return {
      apiBaseUrl: "",
      apiKey: "",
      model: DEFAULT_MODEL,
      storyPrompt: DEFAULT_STORY_PROMPT,
    };
  }
  return {
    apiBaseUrl: localStorage.getItem(STORAGE.apiBaseUrl) ?? "",
    apiKey: localStorage.getItem(STORAGE.apiKey) ?? "",
    model: localStorage.getItem(STORAGE.model) ?? DEFAULT_MODEL,
    storyPrompt: localStorage.getItem(STORAGE.storyPrompt) ?? DEFAULT_STORY_PROMPT,
  };
}

export function saveSettings(settings: KoanSettings): void {
  localStorage.setItem(STORAGE.apiBaseUrl, settings.apiBaseUrl.trim());
  localStorage.setItem(STORAGE.apiKey, settings.apiKey);
  localStorage.setItem(STORAGE.model, settings.model.trim() || DEFAULT_MODEL);
  localStorage.setItem(STORAGE.storyPrompt, settings.storyPrompt.trim() || DEFAULT_STORY_PROMPT);
}

export function isSettingsReady(s: KoanSettings): boolean {
  return Boolean(s.apiBaseUrl.trim() && s.apiKey.trim());
}
