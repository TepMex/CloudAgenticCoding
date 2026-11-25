import raw from "../data/accounts.json";

export type Kind = "active" | "passive" | "active-passive";

export type Entry = {
  id: string;
  storno: boolean;
  debit: string | null;
  credit: string | null;
  text: string;
  tags: string[];
};

export type SourceLink = {
  title: string;
  url: string;
};

export type Account = {
  code: string;
  name: string;
  kind: Kind;
  offBalance: boolean;
  meaning: string;
  debitMeaning: string;
  creditMeaning: string;
  notes: string;
  sources: SourceLink[];
  entries: Entry[];
};

export type Catalog = {
  basis: string;
  balanceCount: number;
  offBalanceCount: number;
  entryCount: number;
  accounts: Account[];
};

export const catalog = raw as Catalog;

export const byCode = new Map(catalog.accounts.map((account) => [account.code, account]));

export function syntheticCode(token: string): string {
  const match = token.match(/^(\d{2,3})/);
  if (!match) throw new Error(`bad account token: ${token}`);
  return match[1];
}

export function matrixCell(code: string): { row: number; col: number } | null {
  if (!/^\d{2}$/.test(code)) return null;
  const value = Number(code);
  return { row: Math.floor(value / 10), col: value % 10 };
}

export function cellCode(row: number, col: number): string {
  return `${row}${col}`;
}

const SUB_GLOSS: Record<string, string> = {
  "01.Выбытие": "выбытие основных средств",
  "03.Выбытие": "выбытие доходных вложений",
  "41.01": "товары на складе",
  "44.01": "издержки обращения",
  "55.01": "аккредитив",
  "58.02": "долговые бумаги",
  "58.03": "выданный заём",
  "60.01": "долг поставщику",
  "60.02": "аванс поставщику",
  "62.01": "долг покупателя",
  "68.01": "НДФЛ",
  "68.02": "НДС",
  "68.04": "налог на прибыль",
  "68.04.1": "налог на прибыль",
  "68.12": "налог при УСН",
  "68.34": "налог с дивидендов",
  "68.90": "единый налоговый платёж",
  "69.01": "социальное страхование",
  "69.02.7": "пенсионные взносы",
  "69.03.1": "медицинские взносы",
  "69.06.3": "взносы ИП",
  "69.06.5": "взносы ИП",
  "69.06.6": "взносы ИП",
  "69.09": "страховые взносы",
  "69.11": "взносы на травматизм",
  "73.01": "заём работнику",
  "73.02": "долг работника по недостаче",
  "73.03": "компенсация работнику",
  "75.01": "вклад учредителя",
  "75.02": "дивиденды к выплате",
  "76.АВ": "НДС с аванса покупателя",
  "76.ВА": "НДС с выданного аванса",
  "84.01": "накопленная прибыль",
  "84.02": "непокрытый убыток",
  "90.01": "выручка",
  "90.01.1": "выручка",
  "90.02": "себестоимость продаж",
  "90.02.1": "себестоимость продаж",
  "90.03": "НДС с продаж",
  "90.07": "коммерческие расходы",
  "90.08": "управленческие расходы",
  "90.09": "результат продаж",
  "91.01": "прочие доходы",
  "91.02": "прочие расходы",
  "91.09": "сальдо прочих доходов и расходов",
  "99.01": "прибыль или убыток",
  "99.01.1": "прибыль или убыток",
};

const NAME_SHORT: Record<string, string> = {
  "02": "амортизация",
  "03": "доходные вложения",
  "08": "вложения во внеоборотные активы",
  "14": "резерв под обесценение запасов",
  "15": "заготовление",
  "16": "отклонение в стоимости",
  "19": "НДС по покупкам",
  "20": "основное производство",
  "25": "общепроизводственные расходы",
  "26": "общехозяйственные расходы",
  "41": "товары",
  "42": "торговая наценка",
  "43": "готовая продукция",
  "44": "расходы на продажу",
  "51": "расчётный счёт",
  "52": "валютный счёт",
  "60": "поставщики",
  "62": "покупатели",
  "66": "краткосрочный кредит",
  "67": "долгосрочный кредит",
  "68": "налоги",
  "69": "страховые взносы",
  "70": "зарплата",
  "71": "подотчёт",
  "73": "расчёты с персоналом",
  "75": "учредители",
  "76": "прочие расчёты",
  "79": "внутрихозяйственные расчёты",
  "83": "добавочный капитал",
  "84": "нераспределённая прибыль",
  "90": "продажи",
  "91": "прочие доходы и расходы",
  "94": "недостачи",
  "96": "резерв предстоящих расходов",
  "98": "доходы будущих периодов",
  "99": "прибыль и убыток",
};

function shortName(name: string): string {
  return name.replace(/\s*\([^)]*\)/g, "").replace(/\s+/g, " ").trim();
}

export function gloss(token: string): string {
  const known = SUB_GLOSS[token];
  if (known) return known;
  const code = syntheticCode(token);
  const named = NAME_SHORT[code];
  const account = byCode.get(code);
  const base = named ?? (account ? shortName(account.name).toLocaleLowerCase("ru") : token);
  return token === code ? base : `${base} (${token})`;
}

export type Section = {
  id: string;
  title: string;
  short: string;
};

export function sectionFor(code: string): Section {
  if (code.length === 3) {
    return { id: "off", title: "Забалансовые счета", short: "За балансом" };
  }
  const n = Number(code);
  if (n <= 9) return { id: "1", title: "Раздел I. Внеоборотные активы", short: "Внеоборотные" };
  if (n <= 19) return { id: "2", title: "Раздел II. Производственные запасы", short: "Запасы" };
  if (n <= 29) return { id: "3", title: "Раздел III. Затраты на производство", short: "Производство" };
  if (n <= 46) return { id: "4", title: "Раздел IV. Готовая продукция и товары", short: "Товары" };
  if (n <= 59) return { id: "5", title: "Раздел V. Денежные средства", short: "Деньги" };
  if (n <= 79) return { id: "6", title: "Раздел VI. Расчёты", short: "Расчёты" };
  if (n <= 89) return { id: "7", title: "Раздел VII. Капитал", short: "Капитал" };
  return { id: "8", title: "Раздел VIII. Финансовые результаты", short: "Результат" };
}

export const MATRIX_ROWS: { row: number; title: string; hint?: string }[] = [
  { row: 0, title: "Внеоборотные" },
  { row: 1, title: "Запасы" },
  { row: 2, title: "Производство" },
  { row: 3, title: "Свободно" },
  { row: 4, title: "Товары" },
  { row: 5, title: "Деньги" },
  { row: 6, title: "Расчёты", hint: "60–69" },
  { row: 7, title: "Расчёты", hint: "70–79" },
  { row: 8, title: "Капитал" },
  { row: 9, title: "Результат" },
];

export function accountMatchesQuery(account: Account, query: string): boolean {
  const needle = query.trim().toLocaleLowerCase("ru");
  if (!needle) return true;
  if (/^\d+$/.test(needle)) return account.code.startsWith(needle);
  return account.code.startsWith(needle) || account.name.toLocaleLowerCase("ru").includes(needle);
}

export function kindLabel(account: Account): string {
  if (account.offBalance) return "Забалансовый";
  if (account.kind === "active") return "Активный";
  if (account.kind === "passive") return "Пассивный";
  return "Активно-пассивный";
}

export function kindLetter(account: Account): string {
  if (account.offBalance) return "З";
  if (account.kind === "active") return "А";
  if (account.kind === "passive") return "П";
  return "АП";
}

export function neighbors(code: string): { prev: Account | null; next: Account | null } {
  const index = catalog.accounts.findIndex((account) => account.code === code);
  return {
    prev: index > 0 ? catalog.accounts[index - 1] : null,
    next: index >= 0 && index < catalog.accounts.length - 1 ? catalog.accounts[index + 1] : null,
  };
}

export function entriesWord(count: number): string {
  const mod10 = count % 10;
  const mod100 = count % 100;
  if (mod10 === 1 && mod100 !== 11) return `${count} проводка`;
  if (mod10 >= 2 && mod10 <= 4 && (mod100 < 12 || mod100 > 14)) return `${count} проводки`;
  return `${count} проводок`;
}
