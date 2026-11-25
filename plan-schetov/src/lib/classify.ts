import type { Entry } from "./catalog";
import { grows, poleOf, quadrant, type Pole, type QuadrantId } from "./poles";

export type Classified = {
  quadrant: QuadrantId;
  debitPole: Pole;
  creditPole: Pole;
  debitGrows: boolean;
  creditGrows: boolean;
};

export function isDouble(entry: Entry): entry is Entry & { debit: string; credit: string } {
  return Boolean(entry.debit && entry.credit);
}

export function classifyEntry(entry: Entry & { debit: string; credit: string }): Classified {
  const debitPole = poleOf(entry.debit, "debit", entry.credit);
  const creditPole = poleOf(entry.credit, "credit", entry.debit);
  return {
    quadrant: quadrant(debitPole, creditPole),
    debitPole,
    creditPole,
    debitGrows: grows(debitPole, "debit"),
    creditGrows: grows(creditPole, "credit"),
  };
}

export const QUADRANT_ORDER = ["da-ka", "dp-kp", "da-kp", "dp-ka"] as const;

export const QUADRANTS: {
  id: QuadrantId;
  title: string;
  badges: { text: string; pole: Pole }[];
  rule: string;
  example: string;
  wash: "blue" | "purple";
}[] = [
  {
    id: "da-ka",
    title: "Актив + / Актив −",
    badges: [
      { text: "ДА", pole: "A" },
      { text: "КА", pole: "A" },
    ],
    rule: "Увеличиваем актив по дебету, уменьшаем актив по кредиту",
    example: "купили материалы за наличные",
    wash: "blue",
  },
  {
    id: "dp-kp",
    title: "Пассив + / Пассив −",
    badges: [
      { text: "ДП", pole: "P" },
      { text: "КП", pole: "P" },
    ],
    rule: "Уменьшаем пассив по дебету, увеличиваем пассив по кредиту",
    example: "перевели долг из краткосрочного в долгосрочный",
    wash: "purple",
  },
  {
    id: "da-kp",
    title: "Актив + / Пассив +",
    badges: [
      { text: "ДА", pole: "A" },
      { text: "КП", pole: "P" },
    ],
    rule: "Актив растёт по дебету, пассив растёт по кредиту",
    example: "получили товар от поставщика с отсрочкой оплаты",
    wash: "blue",
  },
  {
    id: "dp-ka",
    title: "Актив − / Пассив −",
    badges: [
      { text: "ДП", pole: "P" },
      { text: "КА", pole: "A" },
    ],
    rule: "Пассив уменьшается по дебету, актив уменьшается по кредиту",
    example: "погасили кредит с расчётного счёта",
    wash: "purple",
  },
];
