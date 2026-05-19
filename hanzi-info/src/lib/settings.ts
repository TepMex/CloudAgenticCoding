const STORAGE_KEY = "hanzi_info_ui_language";

export type AppLanguage = "en" | "ru";

export function loadAppLanguage(): AppLanguage {
  if (typeof localStorage === "undefined") return "en";
  const v = localStorage.getItem(STORAGE_KEY);
  return v === "ru" ? "ru" : "en";
}

export function saveAppLanguage(lang: AppLanguage): void {
  localStorage.setItem(STORAGE_KEY, lang);
}
