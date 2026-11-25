/**
 * EmbeddingGemma 2 text model used for this corpus.
 * The file is not shipped with the app. The browser downloads `modelUrl`.
 * Document vectors in `data/embeddings.f32` were produced with the same file,
 * prefixes, output size, and L2 normalization.
 */
export const EMBEDDING_CONTRACT = {
  modelRepo: "litert-community/embeddinggemma-2-text-270m-litert-lm",
  modelFile: "embeddinggemma-2-text-270m.litertlm",
  modelUrl:
    "https://huggingface.co/litert-community/embeddinggemma-2-text-270m-litert-lm/resolve/main/embeddinggemma-2-text-270m.litertlm",
  documentPrefix: "task: search result | text: ",
  queryPrefix: "task: search query | text: ",
  outputSize: 256,
  maxInputLength: 256,
  approximateBytes: 165_000_000,
} as const;

export const DOC_PAGES = [
  { page: "ffmpeg", url: "https://ffmpeg.org/ffmpeg.html" },
  { page: "ffprobe", url: "https://ffmpeg.org/ffprobe.html" },
  { page: "ffplay", url: "https://ffmpeg.org/ffplay.html" },
  { page: "filters", url: "https://ffmpeg.org/ffmpeg-filters.html" },
  { page: "codecs", url: "https://ffmpeg.org/ffmpeg-codecs.html" },
  { page: "formats", url: "https://ffmpeg.org/ffmpeg-formats.html" },
  { page: "protocols", url: "https://ffmpeg.org/ffmpeg-protocols.html" },
  { page: "bsf", url: "https://ffmpeg.org/ffmpeg-bitstream-filters.html" },
  { page: "devices", url: "https://ffmpeg.org/ffmpeg-devices.html" },
  { page: "faq", url: "https://ffmpeg.org/faq.html" },
] as const;

export type DocPageId = (typeof DOC_PAGES)[number]["page"];
