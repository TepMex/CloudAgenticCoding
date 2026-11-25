import { byCode, syntheticCode, type Account, type Kind } from "./catalog";

export type Pole = "A" | "P";
export type Side = "debit" | "credit";

type RuleName = "A" | "P" | "split" | "dev16" | "bare75" | "bare76";

/**
 * Учебное правило, в какую сторону шпаргалки ставить счёт.
 * Актив и пассив берутся из справочника. Активно-пассивный счёт
 * раскрывается по субсчёту и обычному сальдо, а не «как получится».
 */
const RULES: Record<string, RuleName> = {
  "16": "dev16",
  "40": "split",
  "60": "P",
  "60.01": "P",
  "60.02": "A",
  "62": "A",
  "62.01": "A",
  "62.02": "P",
  "68": "P",
  "68.90": "A",
  "69": "P",
  "70": "P",
  "71": "A",
  "73": "A",
  "73.01": "A",
  "73.02": "A",
  "73.03": "P",
  "75": "bare75",
  "75.01": "A",
  "75.02": "P",
  "76": "bare76",
  "76.АВ": "A",
  "76.ВА": "P",
  "79": "split",
  "84": "P",
  "90": "split",
  "90.01": "P",
  "90.02": "A",
  "90.03": "A",
  "90.07": "A",
  "90.08": "A",
  "90.09": "P",
  "91": "split",
  "91.01": "P",
  "91.02": "A",
  "91.09": "P",
  "99": "P",
};

const MATRIX_NOTE: Record<string, string> = {
  "16": "Дебет — перерасход, он ведёт себя как актив. Кредит в паре со счётом 15 — экономия, кредитовое отклонение. Кредит при списании на затраты — уменьшение уже накопленного перерасхода.",
  "40": "Дебет собирает фактическую себестоимость, как актив. Кредит показывает выпуск по плановой себестоимости, как пассив. В конце месяца сальдо нет.",
  "60": "Обычный долг перед поставщиком — пассив. Субсчёт 60.02, выданный аванс, — актив: мы уже заплатили и ждём поставку.",
  "62": "Долг покупателя — актив. Запись без субсчёта в справочнике читается как этот долг. Полученный аванс был бы пассивом; в тексте такой проводки это оговорено.",
  "68": "Налог к уплате — пассив. Субсчёт 68.90, единый налоговый платёж, — актив: деньги уже ушли в бюджет и ещё не зачтены в конкретный налог.",
  "69": "Взносы к уплате — пассив: кредит их начисляет, дебет перечисляет.",
  "70": "Обычное сальдо кредитовое, мы должны зарплату, поэтому в матрице счёт стоит как пассив. Переплата работнику возможна, но в этих проводках её нет.",
  "71": "Подотчёт — актив. Дебет: сотруднику выдали деньги. Кредит: он отчитался или вернул остаток.",
  "73": "Заём работнику и недостача — актив, человек должен организации. Субсчёт 73.03, компенсация работнику за его имущество, — пассив.",
  "75": "75.01, вклад к получению, — актив. 75.02, дивиденды к выплате, — пассив. Без субсчёта возврат части капитала — пассив, покрытие убытка участниками — актив.",
  "76": "Претензия и право на субсидию — актив. 76.АВ, НДС с аванса покупателя, тоже дебетовый. 76.ВА, НДС с нашего аванса поставщику, — кредитовый. Кредит счёта 76 в паре с затратами — долг перед контрагентом.",
  "79": "Дебет — филиал или головная организация должны нам. Кредит — мы должны им. Одна хозяйственная передача у отправителя и у получателя попадает в разные клетки.",
  "84": "Прибыль — пассив, это капитал. Убыток, дивиденды и изъятие денег предпринимателем проходят по дебету: капитал уменьшается.",
  "90": "Выручка 90.01 — пассив. Себестоимость, НДС и расходы 90.02, 90.03, 90.07, 90.08 — актив. Свёртка 90.09 несёт результат, как пассив.",
  "91": "Прочие доходы 91.01 — пассив, прочие расходы 91.02 — актив. Сальдо 91.09 закрывается на прибыль, как пассив.",
  "99": "Прибыль копится по кредиту. Убыток и налог на прибыль проходят по дебету и уменьшают результат.",
};

export function matrixNote(account: Account): string {
  if (account.offBalance) {
    return "Забалансовая запись односторонняя: корреспондирующего счёта нет, поэтому четыре клетки двойной записи здесь не используются. Дебет — принять на контроль, кредит — снять.";
  }
  const note = MATRIX_NOTE[account.code];
  if (note) return note;
  if (account.kind === "active") {
    return "Активный счёт растёт по дебету и уменьшается по кредиту. В какую из четырёх клеток встанет проводка, решает второй счёт.";
  }
  if (account.kind === "passive") {
    return "Пассивный счёт растёт по кредиту и уменьшается по дебету. В какую из четырёх клеток встанет проводка, решает второй счёт.";
  }
  return "Для матрицы у этого счёта нет отдельной оговорки: смотрите знак у каждой проводки.";
}

function chain(token: string): string[] {
  const parts = token.split(".");
  const keys: string[] = [];
  for (let length = parts.length; length >= 1; length -= 1) {
    keys.push(parts.slice(0, length).join("."));
  }
  const code = syntheticCode(token);
  if (!keys.includes(code)) keys.push(code);
  return keys;
}

function kindOf(code: string): Kind {
  const account = byCode.get(code);
  if (!account) throw new Error(`unknown account ${code}`);
  return account.kind;
}

function pole16(side: Side, other: string): Pole {
  if (side === "debit") return "A";
  if (syntheticCode(other) === "15") return "P";
  return "A";
}

function pole75(side: Side, other: string): Pole {
  if (syntheticCode(other) === "80") return "P";
  if (side === "debit") return "A";
  if (["50", "51", "52", "55"].includes(syntheticCode(other))) return "A";
  return "P";
}

function pole76(side: Side, other: string): Pole {
  if (side === "debit") return "A";
  if (["50", "51", "52", "55", "57"].includes(syntheticCode(other))) return "A";
  return "P";
}

export function poleOf(token: string, side: Side, other: string): Pole {
  for (const key of chain(token)) {
    const rule = RULES[key];
    if (!rule) continue;
    if (rule === "A" || rule === "P") return rule;
    if (rule === "split") return side === "debit" ? "A" : "P";
    if (rule === "dev16") return pole16(side, other);
    if (rule === "bare75") return pole75(side, other);
    if (rule === "bare76") return pole76(side, other);
  }
  const kind = kindOf(syntheticCode(token));
  if (kind === "active") return "A";
  if (kind === "passive") return "P";
  throw new Error(`no pole for ${token}`);
}

export function grows(pole: Pole, side: Side): boolean {
  return (pole === "A" && side === "debit") || (pole === "P" && side === "credit");
}

export type QuadrantId = "da-ka" | "dp-kp" | "da-kp" | "dp-ka";

export function quadrant(debit: Pole, credit: Pole): QuadrantId {
  if (debit === "A" && credit === "A") return "da-ka";
  if (debit === "P" && credit === "P") return "dp-kp";
  if (debit === "A" && credit === "P") return "da-kp";
  return "dp-ka";
}
