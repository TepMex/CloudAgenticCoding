import type { Settings } from "./types.ts";

export const defaultSettings: Settings = {
  apiBaseUrl: "",
  apiKey: "",
  model: "gpt-4o",
  hsk: "HSK 4",
  explainLanguage: "Русский",
  difficulty: "Средняя",
  depth: "Подробно",
  moreComprehension: true,
  reviewOld: true,
};

export function endpointReady(settings: Settings): boolean {
  return Boolean(settings.apiBaseUrl.trim() && settings.model.trim());
}
