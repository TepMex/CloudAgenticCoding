const STORAGE = {
  apiBaseUrl: "socratic_reading_api_base_url",
  apiKey: "socratic_reading_api_key",
  model: "socratic_reading_model",
  splitModel: "socratic_reading_split_model",
  includeLlmOpinion: "socratic_reading_include_llm_opinion",
} as const;

export type UserSettings = {
  apiBaseUrl: string;
  apiKey: string;
  /** Q&A generation and answer analytics */
  model: string;
  /** Logical segment splitting (streaming markers); can be a smaller/cheaper model */
  splitModel: string;
  /** When true, the model adds a second field: personal view separate from passage-grounded analysis */
  includeLlmOpinion: boolean;
};

const DEFAULT_MODEL = "gpt-4o-mini";
/** Default splitter: smaller/cheaper ok for marker placement; override in Settings if unavailable */
const DEFAULT_SPLIT_MODEL = "gpt-3.5-turbo";

export function loadSettings(): UserSettings {
  if (typeof localStorage === "undefined") {
    return {
      apiBaseUrl: "",
      apiKey: "",
      model: DEFAULT_MODEL,
      splitModel: DEFAULT_SPLIT_MODEL,
      includeLlmOpinion: false,
    };
  }
  const opinionRaw = localStorage.getItem(STORAGE.includeLlmOpinion);
  return {
    apiBaseUrl: localStorage.getItem(STORAGE.apiBaseUrl) ?? "",
    apiKey: localStorage.getItem(STORAGE.apiKey) ?? "",
    model: localStorage.getItem(STORAGE.model) ?? DEFAULT_MODEL,
    splitModel: localStorage.getItem(STORAGE.splitModel) ?? DEFAULT_SPLIT_MODEL,
    includeLlmOpinion: opinionRaw === "1" || opinionRaw === "true",
  };
}

export function saveSettings(settings: UserSettings): void {
  localStorage.setItem(STORAGE.apiBaseUrl, settings.apiBaseUrl.trim());
  localStorage.setItem(STORAGE.apiKey, settings.apiKey);
  localStorage.setItem(STORAGE.model, settings.model.trim() || DEFAULT_MODEL);
  localStorage.setItem(STORAGE.splitModel, settings.splitModel.trim() || DEFAULT_SPLIT_MODEL);
  localStorage.setItem(STORAGE.includeLlmOpinion, settings.includeLlmOpinion ? "1" : "0");
}
