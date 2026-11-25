import { describe, expect, it } from "vitest";
import raw from "./cloud.json";
import type { Cloud } from "../lib/types";

const cloud = raw as unknown as Cloud;

describe("cloud layout", () => {
  it("matches the embedding build", () => {
    expect(cloud.model).toBe("google/embeddinggemma-2");
    expect(cloud.dim).toBe(768);
    expect(cloud.words.length).toBeGreaterThan(4900);
    expect(cloud.clusters.length).toBe(cloud.k);
    expect(cloud.clusters.length).toBeGreaterThan(8);

    const names = cloud.clusters.map((cluster) => cluster.name);
    expect(new Set(names).size).toBe(names.length);
    const problems: string[] = [];
    for (const name of names) {
      if (!name.trim() || name.startsWith("Кластер")) problems.push(`name:${name}`);
    }
    const ids = new Set(cloud.clusters.map((cluster) => cluster.id));
    const seen = new Set<string>();
    for (const word of cloud.words) {
      if (word.id < 0 || word.id >= cloud.words.length || cloud.words[word.id]?.simplified !== word.simplified) {
        problems.push(`id:${word.id}`);
      }
      if (!ids.has(word.cluster)) problems.push(`cluster:${word.id}`);
      if (word.neighbors.length !== 8 || word.neighbors.includes(word.id)) problems.push(`neighbors:${word.id}`);
      for (const neighbor of word.neighbors) {
        if (neighbor < 0 || neighbor >= cloud.words.length) problems.push(`neighbor:${word.id}`);
      }
      for (const value of [word.x, word.y, word.z, word.x2, word.y2]) {
        if (!Number.isFinite(value)) problems.push(`coord:${word.id}`);
      }
      seen.add(word.simplified);
    }
    expect(problems).toEqual([]);
    expect(seen.has("经济")).toBe(true);
    expect(seen.has("我")).toBe(true);
    expect(seen.has("苹果")).toBe(true);
    const hai = cloud.words.filter((word) => word.simplified === "还");
    expect(hai.map((word) => word.pinyin).sort()).toEqual(["hái", "huán"].sort());
    expect(cloud.words.some((word) => word.sources.includes("hsk"))).toBe(true);
    expect(cloud.words.some((word) => word.sources.includes("refold"))).toBe(true);
  });
});
