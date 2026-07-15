import type { Tone } from "../data/schema";

const markedVowels: Record<string, [string, Tone]> = {
  ā: ["a", 1], á: ["a", 2], ǎ: ["a", 3], à: ["a", 4],
  ē: ["e", 1], é: ["e", 2], ě: ["e", 3], è: ["e", 4],
  ī: ["i", 1], í: ["i", 2], ǐ: ["i", 3], ì: ["i", 4],
  ō: ["o", 1], ó: ["o", 2], ǒ: ["o", 3], ò: ["o", 4],
  ū: ["u", 1], ú: ["u", 2], ǔ: ["u", 3], ù: ["u", 4],
  ǖ: ["ü", 1], ǘ: ["ü", 2], ǚ: ["ü", 3], ǜ: ["ü", 4],
  ń: ["n", 2], ň: ["n", 3], ǹ: ["n", 4], ḿ: ["m", 2],
};

const toneMarks: Record<string, string[]> = {
  a: ["a", "ā", "á", "ǎ", "à"],
  e: ["e", "ē", "é", "ě", "è"],
  i: ["i", "ī", "í", "ǐ", "ì"],
  o: ["o", "ō", "ó", "ǒ", "ò"],
  u: ["u", "ū", "ú", "ǔ", "ù"],
  ü: ["ü", "ǖ", "ǘ", "ǚ", "ǜ"],
};

export const INITIALS = ["", "b", "p", "m", "f", "d", "t", "n", "l", "g", "k", "h", "j", "q", "x", "zh", "ch", "sh", "r", "z", "c", "s"] as const;

export const FINAL_GROUPS = [
  { label: "a family", finals: ["a", "ai", "an", "ang", "ao"] },
  { label: "e / o family", finals: ["o", "e", "ei", "en", "eng", "er", "ou", "ong"] },
  { label: "i family", finals: ["i", "ia", "ie", "iao", "iu", "ian", "in", "iang", "ing", "iong"] },
  { label: "u family", finals: ["u", "ua", "uo", "uai", "ui", "uan", "un", "uang", "ueng"] },
  { label: "ü family", finals: ["ü", "üe", "üan", "ün"] },
] as const;

const orthographicY: Record<string, string> = {
  yi: "i", ya: "ia", ye: "ie", yao: "iao", you: "iu", yan: "ian", yin: "in",
  yang: "iang", ying: "ing", yong: "iong", yu: "ü", yue: "üe", yuan: "üan", yun: "ün",
};
const orthographicW: Record<string, string> = {
  wu: "u", wa: "ua", wo: "uo", wai: "uai", wei: "ui", wan: "uan", wen: "un", wang: "uang", weng: "ueng",
};
const validFinals: Set<string> = new Set(FINAL_GROUPS.flatMap((group) => [...group.finals]));

export interface ParsedPinyin {
  pinyinMarked: string;
  pinyinNumbered: string;
  baseSyllable: string;
  initial: string;
  final: string;
  tone: Tone;
  special: boolean;
}

function stripTone(raw: string): { syllable: string; tone: Tone } {
  let input = raw.normalize("NFC").trim().toLowerCase().replaceAll("u:", "ü").replaceAll("v", "ü");
  input = input.replace(/[·'’\s]/g, "");
  const numbered = input.match(/([1-5])$/);
  let tone: Tone = numbered ? Number(numbered[1]) as Tone : 5;
  if (numbered) input = input.slice(0, -1);

  let plain = "";
  for (const char of input) {
    const marked = markedVowels[char];
    if (marked) {
      plain += marked[0];
      tone = marked[1];
    } else {
      plain += char;
    }
  }
  return { syllable: plain.normalize("NFC"), tone };
}

function toneIndex(syllable: string): number {
  const a = syllable.indexOf("a");
  if (a >= 0) return a;
  const e = syllable.indexOf("e");
  if (e >= 0) return e;
  const ou = syllable.indexOf("ou");
  if (ou >= 0) return ou;
  for (let index = syllable.length - 1; index >= 0; index -= 1) {
    if ("aeiouü".includes(syllable[index] ?? "")) return index;
  }
  return -1;
}

export function numberedToMarked(numbered: string): string {
  const { syllable, tone } = stripTone(numbered);
  if (tone === 5) return syllable;
  const index = toneIndex(syllable);
  if (index < 0) return syllable;
  const vowel = syllable[index] ?? "";
  const marked = toneMarks[vowel]?.[tone] ?? vowel;
  return `${syllable.slice(0, index)}${marked}${syllable.slice(index + 1)}`;
}

export function parsePinyin(raw: string): ParsedPinyin | null {
  const { syllable: sourceSyllable, tone } = stripTone(raw);
  if (!sourceSyllable || !/^[a-zü]+$/u.test(sourceSyllable)) return null;

  let initial = "";
  let final = sourceSyllable;
  if (orthographicY[sourceSyllable]) {
    final = orthographicY[sourceSyllable];
  } else if (orthographicW[sourceSyllable]) {
    final = orthographicW[sourceSyllable];
  } else {
    for (const candidate of ["zh", "ch", "sh", "b", "p", "m", "f", "d", "t", "n", "l", "g", "k", "h", "j", "q", "x", "r", "z", "c", "s"]) {
      if (sourceSyllable.startsWith(candidate)) {
        initial = candidate;
        final = sourceSyllable.slice(candidate.length);
        break;
      }
    }
    if (["j", "q", "x"].includes(initial) && final.startsWith("u")) final = `ü${final.slice(1)}`;
  }

  if (["z", "c", "s", "zh", "ch", "sh", "r"].includes(initial) && final === "i") final = "apical-i";

  let special = false;
  if (!validFinals.has(final) && final !== "apical-i") {
    special = true;
    initial = "";
    final = `special:${sourceSyllable}`;
  }

  return {
    pinyinMarked: numberedToMarked(`${sourceSyllable}${tone}`),
    pinyinNumbered: `${sourceSyllable}${tone}`,
    baseSyllable: sourceSyllable,
    initial,
    final,
    tone,
    special,
  };
}

export function normalizePinyinForSearch(raw: string): string {
  return stripTone(raw).syllable.replaceAll("ü", "v");
}

export function displayFinal(final: string): string {
  if (final === "apical-i") return "i";
  if (final.startsWith("special:")) return final.slice(8);
  return final;
}
