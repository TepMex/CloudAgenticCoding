import { describe, expect, it } from "vitest";
import { clusterCentroid, focusPoint, visibleLabels } from "./focus";
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
