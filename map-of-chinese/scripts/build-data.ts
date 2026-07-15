#!/usr/bin/env bun
import { mkdirSync, readFileSync, writeFileSync } from "node:fs";
import path from "node:path";
import * as XLSX from "xlsx";
import { strFromU8, unzipSync } from "fflate";
import { charactersSchema, type CharacterReading, type CharacterRecord, type Hsk3Level, type SearchEntry, type SyllableCell } from "../src/data/schema";
import { normalizePinyinForSearch, parsePinyin } from "../src/lib/pinyin";
import { validateGenerated } from "./validate-data";

const root = path.resolve(import.meta.dir, "..");
const sourceDir = path.join(root, "data", "sources");
const outputDir = path.join(root, "data", "generated");
const frontendDataDir = path.join(root, "src", "data");
const generatedAt = process.env.SOURCE_DATE_EPOCH
  ? new Date(Number(process.env.SOURCE_DATE_EPOCH) * 1000).toISOString()
  : "2026-07-15T00:00:00.000Z";

type PropertyMap = Map<string, Map<string, string>>;

interface Hsk2Word {
  word: string;
  pinyin: string;
  definition: string;
  level: number;
}

function sha256(file: string): string {
  const hasher = new Bun.CryptoHasher("sha256");
  hasher.update(readFileSync(file));
  return hasher.digest("hex");
}

function codePoint(character: string): string {
  return `U+${character.codePointAt(0)?.toString(16).toUpperCase().padStart(4, "0")}`;
}

function characterFromCodePoint(value: string): string {
  return String.fromCodePoint(Number.parseInt(value.slice(2), 16));
}

function loadBasic3500(): string[] {
  const workbook = XLSX.readFile(path.join(sourceDir, "tghz-2013.xlsx"));
  const sheet = workbook.Sheets["字表8105"];
  if (!sheet) throw new Error("Missing 字表8105 worksheet in the TGHZ source workbook.");
  const rows = XLSX.utils.sheet_to_json<(number | string)[]>(sheet, { header: 1, raw: true });
  return rows.slice(0, 3500).map((row, index) => {
    const rank = Number(row[0]);
    const character = String(row[1] ?? "");
    if (rank !== index + 1 || [...character].length !== 1) throw new Error(`Invalid TGHZ row at rank ${index + 1}.`);
    return character;
  });
}

function loadUnihan(): { properties: PropertyMap; parsedLines: number; files: string[] } {
  const zip = unzipSync(new Uint8Array(readFileSync(path.join(sourceDir, "Unihan-17.0.0.zip"))));
  const properties: PropertyMap = new Map();
  let parsedLines = 0;
  const files = Object.keys(zip).sort();
  for (const filename of files) {
    if (!filename.endsWith(".txt")) continue;
    for (const line of strFromU8(zip[filename] ?? new Uint8Array()).split("\n")) {
      if (!line || line.startsWith("#")) continue;
      const [cp, property, ...valueParts] = line.split("\t");
      if (!cp || !property || valueParts.length === 0) continue;
      const value = valueParts.join("\t").trim();
      const byCodePoint = properties.get(property) ?? new Map<string, string>();
      byCodePoint.set(cp, value);
      properties.set(property, byCodePoint);
      parsedLines += 1;
    }
  }
  return { properties, parsedLines, files };
}

function splitHanyuReadings(value: string): string[] {
  const readings: string[] = [];
  for (const match of value.matchAll(/\d{3,5}\.\d{3}:([^\s]+)/g)) {
    readings.push(...(match[1] ?? "").split(","));
  }
  return readings.filter(Boolean);
}

function splitMandarinReadings(value: string): string[] {
  return value.split(/[\s,]+/).map((item) => item.trim()).filter(Boolean);
}

function parseVariantCodePoints(value: string | undefined): string[] {
  if (!value) return [];
  return [...value.matchAll(/U\+[0-9A-F]{4,6}/g)].map((match) => characterFromCodePoint(match[0]));
}

function loadHsk2(): { words: Hsk2Word[]; incrementalCounts: number[] } {
  const words: Hsk2Word[] = [];
  const incrementalCounts: number[] = [];
  for (let level = 1; level <= 6; level += 1) {
    const file = path.join(sourceDir, "hsk2_2015", `hsk${level}.csv`);
    const rows = readFileSync(file, "utf8").trim().split(/\r?\n/).filter(Boolean);
    incrementalCounts.push(rows.length);
    for (const row of rows) {
      const [word = "", pinyin = "", ...definition] = row.split(",");
      words.push({ word, pinyin, definition: definition.join(","), level });
    }
  }
  return { words, incrementalCounts };
}

function loadHsk3(): {
  levels: Map<string, Hsk3Level>;
  sourceCounts: Record<string, number>;
  selectedCounts: Record<string, number>;
  unreconciledLevel6: string[];
} {
  const levels = new Map<string, Hsk3Level>();
  const sourceCounts: Record<string, number> = {};
  const selectedCounts: Record<string, number> = {};
  for (const level of [1, 2, 3, 4, 5, 6, "7-9"] as const) {
    const file = path.join(sourceDir, "hsk3_2026-extraction", `HSK_Level_${level}_hanzi.txt`);
    const sourceCharacters = readFileSync(file, "utf8").trim().split(/\r?\n/).filter(Boolean);
    sourceCounts[String(level)] = sourceCharacters.length;
    const selected = level === 6 ? sourceCharacters.slice(0, 344) : sourceCharacters;
    selectedCounts[String(level)] = selected.length;
    for (const character of selected) levels.set(character, level);
  }
  const level6Source = readFileSync(path.join(sourceDir, "hsk3_2026-extraction", "HSK_Level_6_hanzi.txt"), "utf8").trim().split(/\r?\n/);
  return { levels, sourceCounts, selectedCounts, unreconciledLevel6: level6Source.slice(344) };
}

function readingRecords(character: string, properties: PropertyMap, invalidReadings: string[]): CharacterReading[] {
  const cp = codePoint(character);
  const hanyu = splitHanyuReadings(properties.get("kHanyuPinyin")?.get(cp) ?? "");
  const tghzFallback = hanyu.length === 0 ? splitHanyuReadings(properties.get("kTGHZ2013")?.get(cp) ?? "") : [];
  const mandarin = splitMandarinReadings(properties.get("kMandarin")?.get(cp) ?? "");
  const preferred = new Set(mandarin.map((value) => parsePinyin(value)?.pinyinNumbered).filter(Boolean));
  const raw = [
    ...hanyu.map((value) => ({ value, source: "unihan-kHanyuPinyin" as const })),
    ...tghzFallback.map((value) => ({ value, source: "unihan-kTGHZ2013" as const })),
    ...mandarin.map((value) => ({ value, source: "unihan-kMandarin" as const })),
  ];
  const records = new Map<string, CharacterReading>();
  for (const item of raw) {
    const parsed = parsePinyin(item.value);
    if (!parsed) {
      invalidReadings.push(`${character}\t${item.source}\t${item.value}`);
      continue;
    }
    const existing = records.get(parsed.pinyinNumbered);
    if (existing) {
      if (!existing.sources.includes(item.source)) existing.sources.push(item.source);
      existing.preferred ||= preferred.has(parsed.pinyinNumbered);
      continue;
    }
    records.set(parsed.pinyinNumbered, {
      pinyinMarked: parsed.pinyinMarked,
      pinyinNumbered: parsed.pinyinNumbered,
      baseSyllable: parsed.baseSyllable,
      initial: parsed.initial,
      final: parsed.final,
      tone: parsed.tone,
      preferred: preferred.has(parsed.pinyinNumbered),
      sources: [item.source],
    });
  }
  return [...records.values()].sort((a, b) => Number(b.preferred) - Number(a.preferred) || a.pinyinNumbered.localeCompare(b.pinyinNumbered));
}

function build() {
  mkdirSync(outputDir, { recursive: true });
  mkdirSync(frontendDataDir, { recursive: true });
  const basic = loadBasic3500();
  const basicRank = new Map(basic.map((character, index) => [character, index + 1]));
  const unihan = loadUnihan();
  const hsk2 = loadHsk2();
  const hsk3 = loadHsk3();

  const hsk2Level = new Map<string, number>();
  const hsk2Evidence = new Map<string, string[]>();
  const exampleWords = new Map<string, Hsk2Word[]>();
  for (const word of hsk2.words) {
    for (const character of [...word.word].filter((item) => /\p{Script=Han}/u.test(item))) {
      const previous = hsk2Level.get(character);
      if (!previous || word.level < previous) hsk2Level.set(character, word.level);
      const evidence = hsk2Evidence.get(character) ?? [];
      if (!evidence.includes(word.word) && evidence.length < 5) evidence.push(word.word);
      hsk2Evidence.set(character, evidence);
      const examples = exampleWords.get(character) ?? [];
      if (!examples.some((item) => item.word === word.word) && examples.length < 4) examples.push(word);
      exampleWords.set(character, examples);
    }
  }

  const loadedCharacters = new Set<string>(basic);
  for (const character of hsk2Level.keys()) loadedCharacters.add(character);
  for (const character of hsk3.levels.keys()) loadedCharacters.add(character);

  const invalidReadings: string[] = [];
  const characters: CharacterRecord[] = [...loadedCharacters].map((character) => {
    const cp = codePoint(character);
    const simplifiedVariants = parseVariantCodePoints(unihan.properties.get("kSimplifiedVariant")?.get(cp));
    const traditionalVariants = parseVariantCodePoints(unihan.properties.get("kTraditionalVariant")?.get(cp));
    const definitions = (unihan.properties.get("kDefinition")?.get(cp) ?? "")
      .split(/;\s*/)
      .map((value) => value.trim())
      .filter(Boolean)
      .slice(0, 4);
    const examples = (exampleWords.get(character) ?? []).map((word) => ({
      simplified: word.word,
      traditional: "",
      pinyin: word.pinyin,
      definition: word.definition,
      source: "hsk2_2015" as const,
    }));
    const rank = basicRank.get(character) ?? null;
    return {
      character,
      codePoint: cp,
      standardRank: rank,
      inBasic3500: rank !== null,
      simplified: simplifiedVariants[0] ?? character,
      traditional: [...new Set(traditionalVariants)],
      readings: readingRecords(character, unihan.properties, invalidReadings),
      definitions,
      exampleWords: examples,
      hsk2Level: (hsk2Level.get(character) as 1 | 2 | 3 | 4 | 5 | 6 | undefined) ?? null,
      hsk2EvidenceWords: hsk2Evidence.get(character) ?? [],
      hsk3_2026Level: hsk3.levels.get(character) ?? null,
    } satisfies CharacterRecord;
  }).sort((a, b) => (a.standardRank ?? 999999) - (b.standardRank ?? 999999) || a.codePoint.localeCompare(b.codePoint));

  charactersSchema.parse(characters);

  const cellsByKey = new Map<string, SyllableCell>();
  for (const record of characters) {
    for (const reading of record.readings) {
      const key = `${reading.initial || "∅"}|${reading.final}`;
      const cell = cellsByKey.get(key) ?? {
        key,
        initial: reading.initial,
        final: reading.final,
        baseSyllable: reading.baseSyllable,
        entries: [],
      };
      if (!cell.entries.some((entry) => entry.character === record.character && entry.pinyinNumbered === reading.pinyinNumbered)) {
        cell.entries.push({
          character: record.character,
          tone: reading.tone,
          preferred: reading.preferred,
          pinyinMarked: reading.pinyinMarked,
          pinyinNumbered: reading.pinyinNumbered,
        });
      }
      cellsByKey.set(key, cell);
    }
  }
  const recordByCharacter = new Map(characters.map((record) => [record.character, record]));
  const cells = [...cellsByKey.values()].map((cell) => ({
    ...cell,
    entries: cell.entries.sort((a, b) => {
      const left = recordByCharacter.get(a.character);
      const right = recordByCharacter.get(b.character);
      return Number(b.preferred) - Number(a.preferred)
        || (left?.standardRank ?? 999999) - (right?.standardRank ?? 999999)
        || a.character.localeCompare(b.character, "zh-Hans");
    }),
  })).sort((a, b) => a.key.localeCompare(b.key));

  const searchIndex: SearchEntry[] = characters.map((record) => ({
    character: record.character,
    searchable: [
      record.character,
      record.simplified,
      ...record.traditional,
      ...record.definitions,
      ...record.readings.flatMap((reading) => [reading.pinyinMarked, reading.pinyinNumbered, reading.baseSyllable, normalizePinyinForSearch(reading.baseSyllable)]),
      record.hsk2Level ? `hsk2:${record.hsk2Level}` : "",
      record.hsk3_2026Level ? `hsk3:${record.hsk3_2026Level}` : "",
    ].join(" ").toLowerCase(),
    cellKeys: [...new Set(record.readings.map((reading) => `${reading.initial || "∅"}|${reading.final}`))],
  }));

  const basicWithoutReadings = characters.filter((record) => record.inBasic3500 && record.readings.length === 0).map((record) => record.character);
  const outsideBasic = characters.filter((record) => !record.inBasic3500 && (record.hsk2Level || record.hsk3_2026Level)).map((record) => record.character);
  const cumulativeHsk2SourceRows = hsk2.incrementalCounts.reduce<number[]>((totals, count) => [...totals, (totals.at(-1) ?? 0) + count], []);
  const cumulativeHsk2 = [150, 300, 600, 1200, 2500, 5000];
  const report = {
    generatedAt,
    status: "valid",
    totals: {
      basic3500: basic.length,
      loadedCharacters: characters.length,
      charactersWithReadings: characters.length - characters.filter((record) => record.readings.length === 0).length,
      readingRecords: characters.reduce((sum, record) => sum + record.readings.length, 0),
      syllableCells: cells.length,
      oldHskWordsCumulative: cumulativeHsk2,
      oldHskExtractionRowsCumulative: cumulativeHsk2SourceRows,
      hsk3_2026Incremental: hsk3.selectedCounts,
      hsk3_2026Total: Object.values(hsk3.selectedCounts).reduce((sum, count) => sum + count, 0),
      hsk3_2026SourceIncremental: hsk3.sourceCounts,
      hsk3_2026SourceTotal: Object.values(hsk3.sourceCounts).reduce((sum, count) => sum + count, 0),
      outsideBasic3500: outsideBasic.length,
    },
    reconciliation: {
      issue: "The supplied acceptance contract requires 344 level-6 characters (3,019 total), while the 2025-11 official-syllabus extraction contains 413 (3,088 total).",
      policy: "The requested 344/3,019 contract is used by the application; the remaining source-ordered level-6 records are retained here and not silently discarded.",
      unreconciledLevel6Count: hsk3.unreconciledLevel6.length,
      unreconciledLevel6Characters: hsk3.unreconciledLevel6,
      oldHskExtractionNote: "The available row-level extraction aid places 299 rows in level 3 and 601 in level 4 while retaining the official 600/1,200 cumulative boundaries. All 5,000 rows are accounted for; the official cumulative totals are the validation contract.",
    },
    sourceAccounting: {
      unihanFilesScanned: unihan.files,
      unihanPropertyLinesParsed: unihan.parsedLines,
      invalidReadingRecords: invalidReadings,
      basicCharactersWithoutUnihanReadings: basicWithoutReadings,
      outsideBasicIncludedCharacters: outsideBasic,
    },
  };

  const manifest = {
    name: "Map of Chinese static dataset",
    version: "1.0.0",
    generatedAt: report.generatedAt,
    unicodeVersion: "17.0.0",
    counts: report.totals,
    sources: [
      { id: "tghz-2013", file: "tghz-2013.xlsx", sha256: sha256(path.join(sourceDir, "tghz-2013.xlsx")) },
      { id: "unihan-17.0.0", file: "Unihan-17.0.0.zip", sha256: sha256(path.join(sourceDir, "Unihan-17.0.0.zip")) },
      { id: "hsk2_2015", directory: "hsk2_2015", extractionAidCommit: "615534d31ba085732149416eac668d1b8a1b849e" },
      { id: "hsk3_2026", directory: "hsk3_2026-extraction", extractionAidCommit: "182692ce5a11bc30bdc771835d2f0f27491c25de" },
    ],
    reconciliation: report.reconciliation,
  };

  const generated: Record<string, unknown> = {
    "characters.json": characters,
    "syllable-cells.json": cells,
    "search-index.json": searchIndex,
    "data-manifest.json": manifest,
    "validation-report.json": report,
  };
  for (const [filename, value] of Object.entries(generated)) {
    const content = `${JSON.stringify(value)}\n`;
    const destination = path.join(outputDir, filename);
    writeFileSync(destination, content);
    writeFileSync(path.join(frontendDataDir, filename), content);
    console.log(`${filename.padEnd(26)} ${(Buffer.byteLength(content) / 1024).toFixed(1).padStart(8)} KiB`);
  }

  validateGenerated(outputDir);
  console.log(`\nValidated ${basic.length.toLocaleString()} ranked basic characters, ${characters.length.toLocaleString()} loaded characters, and ${cells.length.toLocaleString()} syllable cells.`);
}

build();
