import type { Cloud, Mode, Word } from "./types";

export function focusPoint(word: Word, mode: Mode): [number, number, number] {
  return mode === "2d" ? [word.x2, word.y2, 0] : [word.x, word.y, word.z];
}

export function clusterCentroid(cloud: Cloud, clusterId: number, mode: Mode): [number, number, number] {
  let x = 0;
  let y = 0;
  let z = 0;
  let count = 0;
  for (const word of cloud.words) {
    if (word.cluster !== clusterId) continue;
    const point = focusPoint(word, mode);
    x += point[0];
    y += point[1];
    z += point[2];
    count += 1;
  }
  if (count === 0) return [0, 0, 0];
  return [x / count, y / count, z / count];
}

export type LabelBox = {
  id: number;
  x: number;
  y: number;
  size: number;
};

/** Keep larger labels. A pinned id is drawn even when it crowds a neighbor. */
export function visibleLabels(items: readonly LabelBox[], minDistance: number, pin?: number): Set<number> {
  const ordered = [...items].sort((left, right) => {
    if (left.id === pin) return -1;
    if (right.id === pin) return 1;
    return right.size - left.size;
  });
  const kept: LabelBox[] = [];
  for (const item of ordered) {
    const clear = kept.every((other) => {
      const dx = other.x - item.x;
      const dy = other.y - item.y;
      return Math.hypot(dx, dy) >= minDistance;
    });
    if (clear || item.id === pin) kept.push(item);
  }
  return new Set(kept.map((item) => item.id));
}
