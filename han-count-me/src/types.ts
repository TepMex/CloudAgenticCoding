export interface SeedMeta {
  language: string;
  description: string;
  metrics: Record<string, string>;
}

export interface Noun {
  noun_id: string;
  noun_ru: string;
  noun_zh: string;
  noun_pinyin: string;
  category: string;
  icon_key: string;
  difficulty: 1 | 2 | 3;
  gameplay_eligible: 'да' | 'нет';
  ge_naturalness: number;
  note: string;
}

export interface Classifier {
  classifier_id: string;
  hanzi: string;
  pinyin: string;
  category: string;
  core_usage_ru: string;
  unlock_level: 1 | 2 | 3;
  note: string;
  source_ids: string;
}

export interface NounClassifierPair {
  pair_id: string;
  noun_id: string;
  classifier_id: string;
  classifier_hanzi: string;
  linguistic_fit: number;
  game_damage: number;
  relation_type: string;
  example: string;
  note: string;
  source_ids: string;
}

export interface Source {
  source_id: string;
  [key: string]: unknown;
}

export interface GameSeed {
  meta: SeedMeta;
  nouns: Noun[];
  classifiers: Classifier[];
  pairs: NounClassifierPair[];
  sources: Source[];
}

export interface SpriteFrameMapping {
  nounId: string;
  sheetIndex: number;
  cellIndex: number;
  row: number;
  column: number;
}
