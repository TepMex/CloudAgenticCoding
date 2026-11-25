import { Database } from "bun:sqlite";
import { expect, test } from "bun:test";

const cloud = (await Bun.file(new URL("../src/data/cloud.json", import.meta.url)).json()) as {
  model: string;
  dim: number;
  words: { id: number; simplified: string; cluster: number }[];
};

test("embeddings sqlite matches the cloud", () => {
  const database = new Database(new URL("../data/embeddings.sqlite", import.meta.url).pathname, { readonly: true });
  const meta = new Map(
    (database.query("SELECT key, value FROM meta").all() as { key: string; value: string }[]).map((row) => [
      row.key,
      row.value,
    ]),
  );
  expect(meta.get("model")).toBe("google/embeddinggemma-2");
  expect(meta.get("dim")).toBe(String(cloud.dim));
  expect(meta.get("dtype")).toBe("float32");
  expect(cloud.model).toBe("google/embeddinggemma-2");

  const count = database.query("SELECT COUNT(*) AS n FROM words").get() as { n: number };
  expect(count.n).toBe(cloud.words.length);

  const row = database.query("SELECT id, cluster_id, embedding FROM words WHERE simplified = ? AND meaning_en LIKE ?").get(
    "经济",
    "economy%",
  ) as { id: number; cluster_id: number; embedding: Uint8Array } | null;
  expect(row).not.toBeNull();
  if (!row) return;
  const copy = new Uint8Array(row.embedding);
  const values = new Float32Array(copy.buffer);
  expect(values.length).toBe(768);
  let norm = 0;
  for (const value of values) norm += value * value;
  expect(Math.abs(Math.sqrt(norm) - 1)).toBeLessThan(1e-3);
  expect(cloud.words[row.id]?.simplified).toBe("经济");
  expect(cloud.words[row.id]?.cluster).toBe(row.cluster_id);

  const neighborCount = database.query("SELECT COUNT(*) AS n FROM neighbors WHERE word_id = ?").get(row.id) as { n: number };
  expect(neighborCount.n).toBe(8);
  database.close();
});
