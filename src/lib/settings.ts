const STORAGE = {
  apiBaseUrl: "socratic_reading_api_base_url",
  apiKey: "socratic_reading_api_key",
  model: "socratic_reading_model",
  includeLlmOpinion: "socratic_reading_include_llm_opinion",
} as const;

export type UserSettings = {
  apiBaseUrl: string;
  apiKey: string;
  model: string;
  /** When true, the model adds a second field: personal view separate from passage-grounded analysis */
  includeLlmOpinion: boolean;
};

const DEFAULT_MODEL = "gpt-4o-mini";

export function loadSettings(): UserSettings {
  if (typeof localStorage === "undefined") {
    return { apiBaseUrl: "", apiKey: "", model: DEFAULT_MODEL, includeLlmOpinion: false };
  }
  const opinionRaw = localStorage.getItem(STORAGE.includeLlmOpinion);
  return {
    apiBaseUrl: localStorage.getItem(STORAGE.apiBaseUrl) ?? "",
    apiKey: localStorage.getItem(STORAGE.apiKey) ?? "",
    model: localStorage.getItem(STORAGE.model) ?? DEFAULT_MODEL,
    includeLlmOpinion: opinionRaw === "1" || opinionRaw === "true",
  };
}

export function saveSettings(settings: UserSettings): void {
  localStorage.setItem(STORAGE.apiBaseUrl, settings.apiBaseUrl.trim());
  localStorage.setItem(STORAGE.apiKey, settings.apiKey);
  localStorage.setItem(STORAGE.model, settings.model.trim() || DEFAULT_MODEL);
  localStorage.setItem(STORAGE.includeLlmOpinion, settings.includeLlmOpinion ? "1" : "0");
}
