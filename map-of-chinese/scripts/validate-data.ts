#!/usr/bin/env bun
import { readFileSync } from "node:fs";
import path from "node:path";
import { charactersSchema, type CharacterRecord } from "../src/data/schema";

function invariant(condition: unknown, message: string): asserts condition {
  if (!condition) throw new Error(`Data validation failed: ${message}`);
}

export function validateGenerated(directory = path.resolve(import.meta.dir, "..", "data", "generated")) {
  const characters = charactersSchema.parse(JSON.parse(readFileSync(path.join(directory, "characters.json"), "utf8"))) as CharacterRecord[];
  const report = JSON.parse(readFileSync(path.join(directory, "validation-report.json"), "utf8")) as {
    totals: {
      oldHskWordsCumulative: number[];
      hsk3_2026Incremental: Record<string, number>;
      hsk3_2026Total: number;
      hsk3_2026SourceIncremental: Record<string, number>;
    };
    reconciliation: { unreconciledLevel6Count: number };
  };

  const basic = characters.filter((record) => record.inBasic3500);
  invariant(basic.length === 3500, `expected exactly 3,500 basic characters, received ${basic.length}`);
  invariant(new Set(basic.map((record) => record.character)).size === 3500, "basic character list contains duplicates");
  const ranks = basic.map((record) => record.standardRank).sort((a, b) => (a ?? 0) - (b ?? 0));
  invariant(ranks.every((rank, index) => rank === index + 1), "standardRank must cover 1 through 3500 with no gaps");

  for (const record of characters) {
    invariant([...record.character].length === 1 && record.character.codePointAt(0) !== undefined, `invalid Unicode character ${record.character}`);
    const readingKeys = record.readings.map((reading) => reading.pinyinNumbered);
    invariant(new Set(readingKeys).size === readingKeys.length, `duplicate normalized reading for ${record.character}`);
    for (const reading of record.readings) {
      invariant(Boolean(reading.final) && reading.tone >= 1 && reading.tone <= 5, `incomplete reading for ${record.character}`);
    }
    if (record.hsk2Level !== null) invariant(record.hsk2EvidenceWords.length > 0, `old-HSK character ${record.character} lacks evidence`);
  }

  invariant(JSON.stringify(report.totals.oldHskWordsCumulative) === JSON.stringify([150, 300, 600, 1200, 2500, 5000]), "old-HSK cumulative word totals do not match 2015 workbook totals");
  const expectedHsk3: Record<string, number> = { "1": 246, "2": 125, "3": 284, "4": 441, "5": 431, "6": 344, "7-9": 1148 };
  invariant(Object.entries(expectedHsk3).every(([level, count]) => report.totals.hsk3_2026Incremental[level] === count), "hsk3_2026 incremental counts do not match the requested contract");
  invariant(report.totals.hsk3_2026Total === 3019, "hsk3_2026 total must equal 3,019");
  invariant((report.totals.hsk3_2026SourceIncremental["6"] ?? 0) === 413, "official HSK source extraction level-6 count changed; reconciliation requires review");
  invariant((report.totals.hsk3_2026Incremental["6"] ?? 0) + report.reconciliation.unreconciledLevel6Count === report.totals.hsk3_2026SourceIncremental["6"], "HSK level-6 source records are not fully accounted for");
  console.log("Data validation passed.");
}

if (import.meta.main) validateGenerated();
