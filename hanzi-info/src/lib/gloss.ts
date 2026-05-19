import type { AppLanguage } from "./settings";
import type { HanziRow } from "./hanzi-types";
import type { UiKey } from "./ui-strings";

/** Display gloss for the active UI language; falls back to English if Russian is missing. */
export function glossText(lang: AppLanguage, row: HanziRow): string {
  if (lang === "ru") return row.meaning_ru || row.meaning_en || "—";
  return row.meaning_en || "—";
}

export function glossLabelKey(lang: AppLanguage, row: HanziRow): UiKey {
  if (lang === "ru") {
    if (row.meaning_ru) return "glossRu";
    if (row.meaning_en) return "glossEn";
    return "glossPrimary";
  }
  return "glossEn";
}
