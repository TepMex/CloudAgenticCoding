/** Spawn interval and drift scale by RTH list index only (equal within a list). */

export const SPAWN_BASE_MS = 2600;
export const SPAWN_MIN_MS = 720;

export function spawnIntervalForList(listIndex: number): number {
  return Math.max(
    SPAWN_MIN_MS,
    Math.floor(SPAWN_BASE_MS * Math.pow(0.92, listIndex)),
  );
}

export function pixelsPerTickForList(listIndex: number): number {
  return 2.6 + listIndex * 0.4;
}

/** Whether every unique entry in the current list has been cleared. */
export function isListComplete(
  listSize: number,
  clearedHanzi: ReadonlySet<string>,
): boolean {
  return listSize > 0 && clearedHanzi.size >= listSize;
}
