import { expect, test } from "bun:test";
import { spawnSync } from "node:child_process";
import { accountMatchesQuery, byCode, catalog, cellCode, entriesWord, matrixCell, syntheticCode, type Entry } from "./catalog";
import { classifyEntry, isDouble, QUADRANT_ORDER } from "./classify";
import { poleOf, quadrant } from "./poles";

test("json matches the reference html", () => {
  const result = spawnSync("python3", ["scripts/extract.py", "--check"], {
    cwd: new URL("../..", import.meta.url).pathname,
  });
  expect(result.status).toBe(0);
});

test("catalog is the commercial chart: 62 balance accounts and 11 off-balance", () => {
  expect(catalog.accounts).toHaveLength(73);
  expect(catalog.balanceCount).toBe(62);
  expect(catalog.offBalanceCount).toBe(11);
  expect(catalog.accounts.filter((account) => account.offBalance)).toHaveLength(11);
  expect(new Set(catalog.accounts.map((account) => account.code)).size).toBe(73);
});

test("balance accounts fill a 10×10 matrix and leave the unused numbers empty", () => {
  let filled = 0;
  for (let row = 0; row < 10; row += 1) {
    for (let col = 0; col < 10; col += 1) {
      const code = cellCode(row, col);
      const account = byCode.get(code);
      if (!account) continue;
      filled += 1;
      expect(account.offBalance).toBe(false);
      expect(matrixCell(code)).toEqual({ row, col });
    }
  }
  expect(filled).toBe(62);
  expect(byCode.has("06")).toBe(false);
  expect(byCode.has("30")).toBe(false);
  expect(matrixCell("01")).toEqual({ row: 0, col: 1 });
  expect(matrixCell("51")).toEqual({ row: 5, col: 1 });
  expect(matrixCell("001")).toBeNull();
});

test("the four picture patterns", () => {
  expect(quadrant("A", "A")).toBe("da-ka");
  expect(quadrant("P", "P")).toBe("dp-kp");
  expect(quadrant("A", "P")).toBe("da-kp");
  expect(quadrant("P", "A")).toBe("dp-ka");
  expect(QUADRANT_ORDER).toEqual(["da-ka", "dp-kp", "da-kp", "dp-ka"]);

  expect(quadrant(poleOf("10", "debit", "50"), poleOf("50", "credit", "10"))).toBe("da-ka");
  expect(quadrant(poleOf("66", "debit", "67"), poleOf("67", "credit", "66"))).toBe("dp-kp");
  expect(quadrant(poleOf("41", "debit", "60"), poleOf("60", "credit", "41"))).toBe("da-kp");
  expect(quadrant(poleOf("66", "debit", "51"), poleOf("51", "credit", "66"))).toBe("dp-ka");
});

test("real postings land in the same cells as the picture", () => {
  const goods = byCode.get("41")?.entries.find((entry) => entry.debit === "41" && entry.credit === "60");
  const loan = byCode.get("66")?.entries.find((entry) => entry.debit === "66" && entry.credit === "51");
  const offset = byCode.get("60")?.entries.find((entry) => entry.debit === "60.01" && entry.credit === "60.02");
  const materials = byCode.get("10")?.entries.find((entry) => entry.debit === "10" && entry.credit === "60");
  expect(goods && isDouble(goods) && classifyEntry(goods).quadrant).toBe("da-kp");
  expect(loan && isDouble(loan) && classifyEntry(loan).quadrant).toBe("dp-ka");
  expect(offset && isDouble(offset) && classifyEntry(offset).quadrant).toBe("dp-ka");
  expect(materials && isDouble(materials) && classifyEntry(materials).quadrant).toBe("da-kp");
});

test("every posting is either one-sided off-balance or one of the four cells", () => {
  for (const account of catalog.accounts) {
    for (const entry of account.entries) {
      if (!isDouble(entry)) {
        expect(account.offBalance).toBe(true);
        expect(Boolean(entry.debit) !== Boolean(entry.credit)).toBe(true);
        continue;
      }
      const placed = classifyEntry(entry);
      expect(QUADRANT_ORDER).toContain(placed.quadrant);
      expect(syntheticCode(entry.debit)).not.toBe("");
      if (account.kind === "active" && !account.offBalance) {
        expect(placed.quadrant).not.toBe("dp-kp");
      }
      if (account.kind === "passive") {
        expect(placed.quadrant).not.toBe("da-ka");
      }
    }
  }
});

test("storno stays in the cell of the same pair and is marked as a negative amount", () => {
  const storno = catalog.accounts
    .flatMap((account) => account.entries)
    .filter((entry): entry is Entry & { debit: string; credit: string } => entry.storno && isDouble(entry));
  expect(storno.length).toBeGreaterThan(0);
  for (const entry of storno) {
    expect(classifyEntry(entry).quadrant).toBeTruthy();
  }
});

test("source links stay on https", () => {
  for (const account of catalog.accounts) {
    for (const source of account.sources) {
      expect(source.url.startsWith("https://")).toBe(true);
    }
  }
});

test("search matches the account name, not a word hidden in the explanation", () => {
  expect(accountMatchesQuery(byCode.get("50")!, "касса")).toBe(true);
  expect(accountMatchesQuery(byCode.get("57")!, "касса")).toBe(false);
  expect(accountMatchesQuery(byCode.get("10")!, "материал")).toBe(true);
  expect(accountMatchesQuery(byCode.get("60")!, "поставщик")).toBe(true);
});

test("russian entry count", () => {
  expect(entriesWord(1)).toBe("1 проводка");
  expect(entriesWord(2)).toBe("2 проводки");
  expect(entriesWord(5)).toBe("5 проводок");
  expect(entriesWord(11)).toBe("11 проводок");
  expect(entriesWord(21)).toBe("21 проводка");
});
