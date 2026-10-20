const MONTHS = ["янв", "фев", "мар", "апр", "май", "июн", "июл", "авг", "сен", "окт", "ноя", "дек"];

export type HeatCell = { date: string; level: 0 | 1 | 2 };

function toISO(date: Date): string {
  const month = String(date.getMonth() + 1).padStart(2, "0");
  const day = String(date.getDate()).padStart(2, "0");
  return `${date.getFullYear()}-${month}-${day}`;
}

function startOfWeek(date: Date): Date {
  const copy = new Date(date.getFullYear(), date.getMonth(), date.getDate());
  const day = copy.getDay();
  const delta = day === 0 ? 6 : day - 1;
  copy.setDate(copy.getDate() - delta);
  return copy;
}

function endOfWeek(date: Date): Date {
  const start = startOfWeek(date);
  start.setDate(start.getDate() + 6);
  return start;
}

/** Monday-based weeks from August through December of `year`. */
export function buildHeatmap(
  activity: Record<string, 1 | 2>,
  year = 2026,
): { cells: HeatCell[]; months: string[] } {
  const start = startOfWeek(new Date(year, 7, 1));
  const end = endOfWeek(new Date(year, 11, 31));
  const cells: HeatCell[] = [];
  for (let cursor = new Date(start); cursor <= end; cursor.setDate(cursor.getDate() + 1)) {
    const iso = toISO(cursor);
    const inWindow = cursor.getFullYear() === year && cursor.getMonth() >= 7 && cursor.getMonth() <= 11;
    cells.push({ date: iso, level: inWindow ? (activity[iso] ?? 0) : 0 });
  }
  const months: string[] = [];
  for (let index = 0; index < cells.length; index += 7) {
    const column = cells.slice(index, index + 7);
    const firstOfMonth = column.find(cell => cell.date.endsWith("-01") && cell.date.startsWith(String(year)));
    if (!firstOfMonth) {
      months.push("");
      continue;
    }
    const monthIndex = Number(firstOfMonth.date.slice(5, 7)) - 1;
    months.push(MONTHS[monthIndex] ?? "");
  }
  return { cells, months };
}
