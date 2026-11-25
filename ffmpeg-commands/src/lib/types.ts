import type { DocPageId } from "./contract.ts";

export type ChunkKind = "component" | "option" | "example";

export type Chunk = {
  id: string;
  kind: ChunkKind;
  /** 0 is indexed first when the browser has to embed locally. */
  priority: 0 | 1;
  title: string;
  summary: string;
  /** Full ffmpeg/ffprobe/ffplay invocation, or null when only a flag is known. */
  command: string | null;
  /** CLI signature such as `-ss position (input/output)`. */
  signature: string | null;
  /** True when the command was filled from the documented component name. */
  synthesized: boolean;
  url: string;
  page: DocPageId | string;
  family: string;
  embedText: string;
};

export type CorpusFile = {
  id: string;
  builtAt: string;
  source: "https://ffmpeg.org/documentation.html";
  model: {
    repo: string;
    file: string;
    url: string;
    documentPrefix: string;
    queryPrefix: string;
    outputSize: number;
    maxInputLength: number;
  };
  chunks: Chunk[];
};

export type EmbeddingMeta = {
  corpusId: string;
  file: "embeddings.f32";
  dim: number;
  count: number;
  normalized: true;
  modelUrl: string;
  documentPrefix: string;
  outputSize: number;
};

/** A ranked documentation hit. A future reranker or LLM consumes this list. */
export type SearchHit = {
  id: string;
  title: string;
  summary: string;
  command: string | null;
  signature: string | null;
  synthesized: boolean;
  url: string;
  page: string;
  family: string;
  kind: ChunkKind;
  score: number;
};

export type SearchResult = {
  stage: "embedding";
  hits: SearchHit[];
  /** True while a local re-index of the long tail is still running. */
  partial: boolean;
};
