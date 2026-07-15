import { z } from "zod";

export const toneSchema = z.union([
  z.literal(1),
  z.literal(2),
  z.literal(3),
  z.literal(4),
  z.literal(5),
]);

export const hsk3LevelSchema = z.union([
  z.literal(1),
  z.literal(2),
  z.literal(3),
  z.literal(4),
  z.literal(5),
  z.literal(6),
  z.literal("7-9"),
]);

export const readingSourceSchema = z.enum([
  "unihan-kHanyuPinyin",
  "unihan-kMandarin",
  "unihan-kTGHZ2013",
  "cc-cedict",
]);

export const characterReadingSchema = z.object({
  pinyinMarked: z.string().min(1),
  pinyinNumbered: z.string().regex(/^[a-zü:-]+[1-5]$/u),
  baseSyllable: z.string().min(1),
  initial: z.string(),
  final: z.string().min(1),
  tone: toneSchema,
  preferred: z.boolean(),
  sources: z.array(readingSourceSchema).min(1),
});

export const exampleWordSchema = z.object({
  simplified: z.string().min(1),
  traditional: z.string(),
  pinyin: z.string(),
  definition: z.string(),
  source: z.enum(["cc-cedict", "hsk2_2015"]),
});

export const characterRecordSchema = z.object({
  character: z.string().min(1),
  codePoint: z.string().regex(/^U\+[0-9A-F]{4,6}$/),
  standardRank: z.number().int().min(1).max(3500).nullable(),
  inBasic3500: z.boolean(),
  simplified: z.string().min(1),
  traditional: z.array(z.string()),
  readings: z.array(characterReadingSchema),
  definitions: z.array(z.string()),
  exampleWords: z.array(exampleWordSchema),
  hsk2Level: z.union([z.literal(1), z.literal(2), z.literal(3), z.literal(4), z.literal(5), z.literal(6)]).nullable(),
  hsk2EvidenceWords: z.array(z.string()),
  hsk3_2026Level: hsk3LevelSchema.nullable(),
});

export const charactersSchema = z.array(characterRecordSchema);

export type Tone = z.infer<typeof toneSchema>;
export type Hsk3Level = z.infer<typeof hsk3LevelSchema>;
export type CharacterReading = z.infer<typeof characterReadingSchema>;
export type CharacterRecord = z.infer<typeof characterRecordSchema>;
export type ExampleWord = z.infer<typeof exampleWordSchema>;

export interface CellCharacterReading {
  character: string;
  tone: Tone;
  preferred: boolean;
  pinyinMarked: string;
  pinyinNumbered: string;
}

export interface SyllableCell {
  key: string;
  initial: string;
  final: string;
  baseSyllable: string;
  entries: CellCharacterReading[];
}

export interface SearchEntry {
  character: string;
  searchable: string;
  cellKeys: string[];
}
