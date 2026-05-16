export type HanziType = "Phonetic" | "Ideographic" | "Radical";

export type HanziRow = {
  id: number;
  hanzi: string;
  type: HanziType;
  /** Kangxi-style English radical gloss from HanziJS `radicalListWithMeaning` (Radical rows only). */
  radical_name_en?: string;
  meaning_en: string;
  meaning_ru: string;
  reading: string;
  initiale: string;
  finale: string;
  tone: number;
};

export type PhoneticSeries = {
  component: string;
  component_reading_numbered: string;
  regularity_scale: 1 | 2;
  set_key: string;
  members: string[];
};

export type HanziDatabase = {
  version: 1;
  about: string;
  hanzi: HanziRow[];
  hanzi2radicals: [hanzi_id: number, radical_id: number][];
  by_hanzi: Record<string, number>;
  phonetic_series_by_hanzi: Record<string, PhoneticSeries[]>;
};
