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

export type GlyphLabel = {
  id: number;
  x: number;
  y: number;
  halfW: number;
  halfH: number;
};

/** Keep in sync with `.word-label` in styles.css. */
export const WORD_LABEL_FONT = 14;

/** Free pixels required before a word caption appears. */
export const WORD_LABEL_GAP = 6;

export function wordGlyph(id: number, text: string, x: number, y: number): GlyphLabel {
  return {
    id,
    x,
    y,
    halfW: (Math.max(text.length, 1) * WORD_LABEL_FONT) / 2,
    halfH: WORD_LABEL_FONT / 2,
  };
}

export function boxesOverlap(a: GlyphLabel, b: GlyphLabel, gap = 0): boolean {
  return Math.abs(a.x - b.x) < a.halfW + b.halfW + gap && Math.abs(a.y - b.y) < a.halfH + b.halfH + gap;
}

type LabelGrid = {
  cell: number;
  bins: Map<string, GlyphLabel[]>;
};

function cellSize(groups: readonly (readonly GlyphLabel[])[], gap: number): number {
  let halfW = 0;
  let halfH = 0;
  for (const group of groups) {
    for (const item of group) {
      halfW = Math.max(halfW, item.halfW);
      halfH = Math.max(halfH, item.halfH);
    }
  }
  return Math.max(halfW * 2 + gap, halfH * 2 + gap, 1);
}

function buildIndex(items: readonly GlyphLabel[], cell: number): LabelGrid {
  const bins = new Map<string, GlyphLabel[]>();
  for (const item of items) {
    const key = `${Math.floor(item.x / cell)}:${Math.floor(item.y / cell)}`;
    const list = bins.get(key);
    if (list) list.push(item);
    else bins.set(key, [item]);
  }
  return { cell, bins };
}

function hitsIndex(item: GlyphLabel, index: LabelGrid, gap: number, ignoreId?: number): boolean {
  const cx = Math.floor(item.x / index.cell);
  const cy = Math.floor(item.y / index.cell);
  for (let ox = -1; ox <= 1; ox += 1) {
    for (let oy = -1; oy <= 1; oy += 1) {
      const list = index.bins.get(`${cx + ox}:${cy + oy}`);
      if (!list) continue;
      for (const other of list) {
        if (other.id === ignoreId) continue;
        if (boxesOverlap(item, other, gap)) return true;
      }
    }
  }
  return false;
}

function overlapsAny(item: GlyphLabel, others: readonly GlyphLabel[] | undefined, gap: number): boolean {
  if (!others) return false;
  for (const other of others) {
    if (boxesOverlap(item, other, gap)) return true;
  }
  return false;
}

/**
 * Word captions that have room to sit apart from every other point.
 * `gap` is the free space a caption needs before it first appears.
 * Ids in `keep` stay up until their glyph box actually meets another point's caption,
 * including a point that itself has no caption. Zooming back out therefore clears them.
 * `obstacles` (the focused word's large caption) always block.
 */
export function separatedLabels(
  items: readonly GlyphLabel[],
  gap: number,
  keep?: ReadonlySet<number>,
  obstacles?: readonly GlyphLabel[],
): Set<number> {
  const open: GlyphLabel[] = [];
  for (const item of items) {
    if (overlapsAny(item, obstacles, gap)) continue;
    open.push(item);
  }
  if (open.length === 0) return new Set();
  const index = buildIndex(open, cellSize([open], gap));
  const ids = new Set<number>();
  for (const item of open) {
    const limit = keep?.has(item.id) ? 0 : gap;
    if (!hitsIndex(item, index, limit, item.id)) ids.add(item.id);
  }
  return ids;
}

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
