import { appUrl } from "./app-url.ts";
import { EMBEDDING_CONTRACT } from "./contract.ts";
import { loadEmbedder, type Embedder } from "./engine.ts";
import { readVectorCache, writeVectorCache } from "./idb.ts";
import { cosineSimilarity, rankChunks } from "./rank.ts";
import type { Chunk, CorpusFile, EmbeddingMeta, SearchResult } from "./types.ts";

export type Status =
  | { phase: "corpus" }
  | { phase: "download"; loaded: number; total: number }
  | { phase: "engine" }
  | { phase: "check" }
  | { phase: "index"; done: number; total: number }
  | { phase: "ready"; count: number; backend: string; shipped: boolean; partial: boolean }
  | { phase: "error"; message: string };

type Listener = (status: Status) => void;

const listeners = new Set<Listener>();
let status: Status = { phase: "corpus" };
let ready: Ready | null = null;
let boot: Promise<void> | null = null;

type Ready = {
  corpus: CorpusFile;
  vectors: Float32Array[];
  embedder: Embedder;
  partial: boolean;
};

function emit(next: Status) {
  status = next;
  for (const listener of listeners) listener(status);
}

export function subscribeStatus(listener: Listener): () => void {
  listeners.add(listener);
  listener(status);
  return () => listeners.delete(listener);
}

export function startSearch(): Promise<void> {
  boot ??= run().catch(error => {
    boot = null;
    const message = error instanceof Error ? error.message : "Не удалось подготовить поиск";
    emit({ phase: "error", message });
    throw error;
  });
  return boot;
}

export async function searchCommands(query: string, k = 6): Promise<SearchResult> {
  await startSearch();
  if (!ready) throw new Error(status.phase === "error" ? status.message : "Поиск ещё готовится");
  const text = query.trim();
  if (!text) throw new Error("Пустой запрос");
  const vector = await ready.embedder.embed(`${ready.corpus.model.queryPrefix}${text}`);
  const hits = rankChunks(vector, ready.corpus.chunks, ready.vectors, k);
  return { stage: "embedding", hits, partial: ready.partial };
}

async function run() {
  emit({ phase: "corpus" });
  const corpus = (await fetch(appUrl("/corpus.json")).then(response => {
    if (!response.ok) throw new Error("Нет корпуса документации");
    return response.json();
  })) as CorpusFile;
  if (corpus.model.url !== EMBEDDING_CONTRACT.modelUrl) throw new Error("Корпус собран под другую модель");

  const shipped = await loadShippedVectors(corpus);
  emit({ phase: "download", loaded: 0, total: EMBEDDING_CONTRACT.approximateBytes });
  const embedder = await loadEmbedder((loaded, total) => emit({ phase: "download", loaded, total }));

  if (shipped && (await matchesRuntime(embedder, corpus, shipped))) {
    ready = { corpus, vectors: shipped, embedder, partial: false };
    emit({
      phase: "ready",
      count: corpus.chunks.length,
      backend: embedder.backend,
      shipped: true,
      partial: false,
    });
    return;
  }

  let release!: () => void;
  let fail!: (error: unknown) => void;
  const searchable = new Promise<void>((resolve, reject) => {
    release = resolve;
    fail = reject;
  });
  void indexLocally(embedder, corpus, release).catch(error => {
    if (ready) {
      emit({ phase: "error", message: error instanceof Error ? error.message : "Индексация прервалась" });
      return;
    }
    fail(error);
  });
  await searchable;
}

async function loadShippedVectors(corpus: CorpusFile): Promise<Float32Array[] | null> {
  const metaResponse = await fetch(appUrl("/embeddings.json"));
  if (!metaResponse.ok) return null;
  const meta = (await metaResponse.json()) as EmbeddingMeta;
  if (meta.corpusId !== corpus.id || meta.dim !== corpus.model.outputSize || meta.count !== corpus.chunks.length) {
    return null;
  }
  const binary = await fetch(appUrl("/embeddings.f32"));
  if (!binary.ok) return null;
  const buffer = await binary.arrayBuffer();
  if (buffer.byteLength !== meta.count * meta.dim * 4) return null;
  const flat = new Float32Array(buffer);
  const vectors: Float32Array[] = [];
  for (let index = 0; index < meta.count; index++) {
    vectors.push(flat.subarray(index * meta.dim, (index + 1) * meta.dim));
  }
  return vectors;
}

async function matchesRuntime(embedder: Embedder, corpus: CorpusFile, vectors: Float32Array[]): Promise<boolean> {
  emit({ phase: "check" });
  const samples = [0, Math.floor(corpus.chunks.length / 2)].filter(index => corpus.chunks[index] && vectors[index]);
  for (const index of samples) {
    const chunk = corpus.chunks[index];
    const expected = vectors[index];
    if (!chunk || !expected) return false;
    const got = await embedder.embed(`${corpus.model.documentPrefix}${chunk.embedText.trim()}`);
    const similarity = cosineSimilarity(got, expected);
    console.info("embedding self-check", chunk.id, similarity.toFixed(4));
    if (similarity < 0.9) return false;
  }
  return samples.length > 0;
}

async function indexLocally(embedder: Embedder, corpus: CorpusFile, onSearchable: () => void): Promise<void> {
  const dim = corpus.model.outputSize;
  const cached = await readVectorCache(corpus.id, dim);
  const vectors = Array.from({ length: corpus.chunks.length }, () => new Float32Array(dim));
  const missing: number[] = [];
  for (const [index, chunk] of corpus.chunks.entries()) {
    const stored = cached.get(chunk.id);
    if (stored && stored.length === dim) vectors[index] = stored;
    else missing.push(index);
  }
  missing.sort((a, b) => corpus.chunks[a]!.priority - corpus.chunks[b]!.priority);

  ready = { corpus, vectors, embedder, partial: missing.length > 0 };
  let done = corpus.chunks.length - missing.length;
  const priorityLeft = new Set(missing.filter(index => corpus.chunks[index]?.priority === 0));
  let unlocked = priorityLeft.size === 0;
  if (unlocked) {
    emitReady(embedder, corpus.chunks.length, missing.length > 0);
    onSearchable();
  }

  const pending: { id: string; vector: Float32Array }[] = [];
  for (const index of missing) {
    const chunk = corpus.chunks[index];
    if (!chunk) continue;
    const vector = await embedder.embed(`${corpus.model.documentPrefix}${chunk.embedText.trim()}`);
    vectors[index] = vector;
    pending.push({ id: chunk.id, vector });
    done += 1;
    if (pending.length >= 12) await writeVectorCache(corpus.id, dim, pending.splice(0));
    emit({ phase: "index", done, total: corpus.chunks.length });
    if (chunk.priority === 0) priorityLeft.delete(index);
    if (!unlocked && priorityLeft.size === 0) {
      unlocked = true;
      ready.partial = done < corpus.chunks.length;
      emitReady(embedder, corpus.chunks.length, ready.partial);
      onSearchable();
    }
  }
  if (pending.length) await writeVectorCache(corpus.id, dim, pending);
  ready.partial = false;
  emitReady(embedder, corpus.chunks.length, false);
}

function emitReady(embedder: Embedder, count: number, partial: boolean) {
  emit({ phase: "ready", count, backend: embedder.backend, shipped: false, partial });
}
