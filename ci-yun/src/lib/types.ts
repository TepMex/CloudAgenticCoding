export type Mode = "2d" | "3d";

export type Cluster = {
  id: number;
  name: string;
  color: string;
  size: number;
};

export type Word = {
  id: number;
  simplified: string;
  traditional: string;
  pinyin: string;
  meaning: string;
  meaningRu: string;
  pos: string;
  sources: string[];
  hsk: string[];
  cluster: number;
  x2: number;
  y2: number;
  x: number;
  y: number;
  z: number;
  neighbors: number[];
};

export type Cloud = {
  model: string;
  prompt: string;
  dim: number;
  seed: number;
  k: number;
  silhouette: Record<string, number>;
  clusters: Cluster[];
  words: Word[];
};

export const SOURCE_LABEL: Record<string, string> = {
  hsk: "HSK 1000–5000",
  refold: "Refold 1k",
};
