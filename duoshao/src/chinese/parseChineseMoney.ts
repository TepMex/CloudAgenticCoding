import { toChineseNumber } from "./chineseNumber";
import { normalizeTranscript } from "./normalizeTranscript";

export interface ParsedAnswer {
  amount: number;
  raw: string;
  normalized: string;
}

let numeralLookup: ReadonlyMap<string, number> | undefined;

function getNumeralLookup(): ReadonlyMap<string, number> {
  if (numeralLookup) return numeralLookup;
  const values = new Map<string, number>();
  for (let amount = 1; amount <= 9999; amount += 1) values.set(toChineseNumber(amount), amount);
  numeralLookup = values;
  return values;
}

export function parseChineseMoney(raw: string): ParsedAnswer | null {
  const normalized = normalizeTranscript(raw);
  if (!normalized) return null;

  const match = normalized.match(/^(.+?)(?:块钱|元|块)?$/u);
  if (!match) return null;
  const body = match[1];

  if (/^[1-9]\d{0,3}$/u.test(body)) {
    const amount = Number(body);
    return { amount, raw, normalized };
  }

  if (!/^[零一二两三四五六七八九十百千]+$/u.test(body)) return null;
  const canonicalBody = body.replace(/两(?=[百千])/gu, "二");
  const amount = getNumeralLookup().get(canonicalBody);
  return amount ? { amount, raw, normalized } : null;
}
