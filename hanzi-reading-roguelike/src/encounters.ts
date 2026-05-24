const STORAGE_KEY = "hanzi-reading-encounters";

export type EncounterMap = Record<string, number>;

export function loadEncounters(): EncounterMap {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (!raw) return {};
    const parsed = JSON.parse(raw) as unknown;
    if (typeof parsed !== "object" || parsed === null) return {};
    return parsed as EncounterMap;
  } catch {
    return {};
  }
}

export function saveEncounters(map: EncounterMap): void {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(map));
  } catch {
    /* ignore quota / private mode */
  }
}

/** How many times this hanzi has been spawned across sessions (used for hints). */
export function getEncounterCount(map: EncounterMap, hanzi: string): number {
  return map[hanzi] ?? 0;
}

/** Increment after spawning an enemy with this hanzi. */
export function bumpEncounter(map: EncounterMap, hanzi: string): EncounterMap {
  const next = { ...map, [hanzi]: (map[hanzi] ?? 0) + 1 };
  saveEncounters(next);
  return next;
}
