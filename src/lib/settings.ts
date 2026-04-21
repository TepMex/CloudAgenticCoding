const STORAGE = {
  apiBaseUrl: "socratic_reading_api_base_url",
  apiKey: "socratic_reading_api_key",
  model: "socratic_reading_model",
} as const;

export type UserSettings = {
  apiBaseUrl: string;
  apiKey: string;
  model: string;
};

const DEFAULT_MODEL = "gpt-4o-mini";

export function loadSettings(): UserSettings {
  if (typeof localStorage === "undefined") {
    return { apiBaseUrl: "", apiKey: "", model: DEFAULT_MODEL };
  }
  return {
    apiBaseUrl: localStorage.getItem(STORAGE.apiBaseUrl) ?? "",
    apiKey: localStorage.getItem(STORAGE.apiKey) ?? "",
    model: localStorage.getItem(STORAGE.model) ?? DEFAULT_MODEL,
  };
}

export function saveSettings(settings: UserSettings): void {
  localStorage.setItem(STORAGE.apiBaseUrl, settings.apiBaseUrl.trim());
  localStorage.setItem(STORAGE.apiKey, settings.apiKey);
  localStorage.setItem(STORAGE.model, settings.model.trim() || DEFAULT_MODEL);
}
