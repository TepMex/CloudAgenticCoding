import { describe, expect, it } from "vitest";
import { clusterCentroid, focusPoint, separatedLabels, visibleLabels, wordGlyph, type GlyphLabel } from "./focus";
import type { Cloud, Word } from "./types";

function word(patch: Partial<Word> & Pick<Word, "id" | "cluster">): Word {
  return {
    simplified: String(patch.id),
    traditional: String(patch.id),
    pinyin: "",
    meaning: "",
    meaningRu: "",
    pos: "",
    sources: [],
    hsk: [],
    x2: 0,
    y2: 0,
    x: 0,
    y: 0,
    z: 0,
    neighbors: [],
    ...patch,
  };
}

const cloud: Cloud = {
  model: "google/embeddinggemma-2",
  prompt: "Clustering",
  dim: 768,
  seed: 42,
  k: 2,
  silhouette: {},
  clusters: [
    { id: 0, name: "Еда", color: "#fff", size: 2 },
    { id: 1, name: "Путь", color: "#000", size: 1 },
  ],
  words: [
    word({ id: 0, cluster: 0, x2: 0, y2: 0, x: 0, y: 0, z: 0 }),
    word({ id: 1, cluster: 0, x2: 4, y2: 2, x: 4, y: 2, z: 6 }),
    word({ id: 2, cluster: 1, x2: 9, y2: 9, x: 1, y: 1, z: 1 }),
  ],
};

describe("focus geometry", () => {
  it("uses the plane coordinates in 2d and drops z", () => {
    expect(focusPoint(cloud.words[1]!, "2d")).toEqual([4, 2, 0]);
  });

  it("uses the spatial coordinates in 3d", () => {
    expect(focusPoint(cloud.words[1]!, "3d")).toEqual([4, 2, 6]);
  });

  it("centers a cluster on the mean of its members", () => {
    expect(clusterCentroid(cloud, 0, "2d")).toEqual([2, 1, 0]);
    expect(clusterCentroid(cloud, 1, "3d")).toEqual([1, 1, 1]);
    expect(clusterCentroid(cloud, 9, "2d")).toEqual([0, 0, 0]);
  });

  function glyph(id: number, x: number, y: number, half = 6): GlyphLabel {
    return { id, x, y, halfW: half, halfH: half };
  }

  it("keeps word captions only when their boxes sit apart", () => {
    const apart = [glyph(0, 0, 0), glyph(1, 40, 0)];
    expect(separatedLabels(apart, 4)).toEqual(new Set([0, 1]));

    const crowded = [glyph(0, 0, 0), glyph(1, 10, 0)];
    expect(separatedLabels(crowded, 4)).toEqual(new Set());

    const mixed = [glyph(0, 0, 0), glyph(1, 8, 0), glyph(2, 80, 0)];
    expect(separatedLabels(mixed, 4)).toEqual(new Set([2]));
  });

  it("treats an exact touch as clear and still catches a neighbor across a cell boundary", () => {
    expect(separatedLabels([glyph(0, 0, 0, 10), glyph(1, 24, 0, 10)], 4)).toEqual(new Set([0, 1]));
    expect(separatedLabels([glyph(0, 10, 0, 20), glyph(1, 50, 0, 20)], 4)).toEqual(new Set());
    expect(separatedLabels([glyph(0, -30, -10, 5), glyph(1, 30, -10, 5)], 4)).toEqual(new Set([0, 1]));
  });

  it("drops kept captions when zooming out puts them on unlabeled neighbors", () => {
    const items: GlyphLabel[] = [];
    const keep = new Set<number>();
    for (let index = 0; index < 20; index += 1) {
      items.push(glyph(index, index * 10, 0));
      if (index % 2 === 0) keep.add(index);
    }
    expect(separatedLabels(items, 4, keep)).toEqual(new Set());
  });

  it("keeps a caption that is already showing until the glyphs themselves meet", () => {
    const pair = [glyph(0, 0, 0), glyph(1, 14, 0)];
    expect(separatedLabels(pair, 4)).toEqual(new Set());
    expect(separatedLabels(pair, 4, new Set([0, 1]))).toEqual(new Set([0, 1]));
    expect(separatedLabels(pair, 4, new Set([0]))).toEqual(new Set([0]));
    expect(separatedLabels([glyph(0, 0, 0), glyph(1, 11, 0)], 4, new Set([0, 1]))).toEqual(new Set());
  });

  it("reserves the focused caption and leaves the rest of a clear grid labeled", () => {
    const obstacle = glyph(-1, 0, 0, 20);
    expect(separatedLabels([glyph(0, 0, 0), glyph(1, 80, 0)], 4, undefined, [obstacle])).toEqual(new Set([1]));

    const grid: GlyphLabel[] = [];
    for (let row = 0; row < 6; row += 1) {
      for (let column = 0; column < 6; column += 1) {
        grid.push(glyph(row * 6 + column, column * 20, row * 20, 5));
      }
    }
    expect(separatedLabels(grid, 4).size).toBe(36);
  });

  it("classifies a few thousand captions quickly", () => {
    const crowded = Array.from({ length: 4000 }, (_, id) => glyph(id, (id % 80) * 3, Math.floor(id / 80) * 3, 8));
    const spread = Array.from({ length: 4000 }, (_, id) => glyph(id, (id % 80) * 30, Math.floor(id / 80) * 30, 8));
    const started = performance.now();
    expect(separatedLabels(crowded, 4).size).toBe(0);
    expect(separatedLabels(spread, 4).size).toBe(4000);
    expect(performance.now() - started).toBeLessThan(150);
  });

  it("sizes a caption from the hanzi length", () => {
    expect(wordGlyph(3, "经济", 10, 20)).toMatchObject({ id: 3, x: 10, y: 20, halfW: 14, halfH: 7 });
  });

  it("hides a smaller label that sits on a larger one and keeps a pinned label", () => {
    const labels = [
      { id: 0, x: 0, y: 0, size: 10 },
      { id: 1, x: 10, y: 0, size: 50 },
      { id: 2, x: 80, y: 0, size: 5 },
    ];
    expect(visibleLabels(labels, 40)).toEqual(new Set([1, 2]));
    expect(visibleLabels(labels, 40, 0)).toEqual(new Set([0, 2]));
  });
});
