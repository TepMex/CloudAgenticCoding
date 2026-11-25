# FFmpeg commands — Specification

## Purpose

A browser chat that turns a plain-language media task into a ranked list of FFmpeg commands. The ranking is retrieval over the official FFmpeg manual: each documentation fragment was embedded with EmbeddingGemma 2, and the browser embeds only the user's sentence with the same model.

The app is for someone who knows what they want to do with a file and wants the matching `ffmpeg` / `ffprobe` / `ffplay` invocation, with a link back to the manual section.

## Requirements

1. Build a searchable corpus from the nightly command-line documentation linked from `https://ffmpeg.org/documentation.html`: `ffmpeg`, `ffprobe`, `ffplay`, filters, codecs, formats, protocols, bitstream filters, devices, and the FAQ.
2. Each fragment stores a title, a short summary, an optional command, the source URL with anchor, and the text that was embedded. Fragments are components (filter, codec, muxer, protocol, …), CLI options, or concrete examples. A short list of task cards (common jobs such as cutting, compressing, extracting audio) is added on top, each linked to the manual section it comes from. Their wording is original; the commands are the usual form of those sections.
3. Document embeddings use EmbeddingGemma 2 Text 270M (`embeddinggemma-2-text-270m.litertlm`), the asymmetric prefix `task: search result | text: `, L2 normalization, and Matryoshka output size 256. Query embeddings use `task: search query | text: ` and the same size.
4. The model file is not part of the application. The browser downloads it from the public Hugging Face URL and may keep it in the Cache API. Shipped data is the text corpus and the precomputed float32 matrix only.
5. Before trusting the shipped matrix, the browser embeds two fragments and checks cosine similarity against the stored rows. If the check fails, it embeds the corpus locally, caches vectors in IndexedDB, and still answers.
6. The chat accepts a task in any language EmbeddingGemma 2 covers, including Russian. The reply is up to 6 commands, highest cosine similarity first, with duplicate command lines removed.
7. Each result shows rank, score, the command or option signature, whether the command is a documented example or a template derived from the component name, a short summary, a copy button, and a link to the manual.
8. The retrieval result is a list of hits (`stage: "embedding"`). A later reranker or LLM can consume that list. This version does not call either.

## Interfaces

- Web UI at `/` (Bun + React). Dev server: `bun dev` in `ffmpeg-commands/`.
- Static build: `bun run build` writes `dist/` (page, corpus, embedding matrix, LiteRT wasm). GitHub Pages publishes that directory at `/CloudAgenticCoding/ffmpeg-commands/`. The client resolves those files from the page URL, so `/` and the Pages path share one client. `GH_PAGES_PUBLIC_PATH` prefixes the bundled script and stylesheet. The model weights are not in `dist/`.
- `GET /corpus.json` — fragments and the embedding contract (model URL, prefixes, output size).
- `GET /embeddings.json` — matrix metadata (`corpusId`, `dim`, `count`).
- `GET /embeddings.f32` — little-endian float32, one 256-d row per corpus chunk, L2-normalized.
- `GET /vendor/litertlm/*` — LiteRT-LM wasm runtime from `@litert-lm/core` (LiteRT.js stack). The model itself is not served here.
- Model download, from the browser only: `https://huggingface.co/litert-community/embeddinggemma-2-text-270m-litert-lm/resolve/main/embeddinggemma-2-text-270m.litertlm`
- Rebuild text: `bun run corpus` (optional `--refresh`). Rebuild vectors, with the model file present at `data/model/embeddinggemma-2-text-270m.litertlm`: `python3 scripts/embed_corpus.py`.

## Data model

- `Chunk`: `id`, `kind` (`component` | `option` | `example`), `priority`, `title`, `summary`, `command`, `signature`, `synthesized`, `url`, `page`, `family`, `embedText`.
- `CorpusFile.id` is a short sha256 of chunk ids and embed texts. The matrix is valid only when `embeddings.json.corpusId` matches.
- No accounts, no server-side session. Chat history stays in the page. The model blob stays in the browser Cache API. A locally rebuilt index stays in IndexedDB `ffmpeg-commands`.

## UI / UX

- One chat column. The user describes a task. The assistant side is the ranked command list, not a generated explanation.
- Empty state explains that the model is downloaded by the browser and offers example tasks.
- The header shows download or index progress and, when ready, the fragment count and CPU/GPU backend.
- Enter sends. Shift+Enter inserts a newline. Copy writes the command line.
- The interface language is Russian. Documentation excerpts stay in the manual's language.

## Out of scope

- Running FFmpeg, uploading media, or checking that a command succeeds.
- A cross-encoder reranker or an LLM answer. The hit list is the extension point.
- The full `ffmpeg-all` manual, the C API (Doxygen), ffmpeg-utils, scaler, and resampler pages.
- Shipping, proxying, or bundling the `.litertlm` weights.
- Accounts, history sync, and hosting the model.

## Acceptance criteria

1. `bun test` passes, including the fixture parser (volume example, synthesized `scale` template, `-ss` option) and cosine ranking with duplicate commands removed.
2. `data/corpus.json` contains fragments from each source page, with unique ids, commands that start with `ffmpeg`, `ffprobe`, or `ffplay` when `command` is set, and embed texts that include the task-oriented hints for common jobs (compress, cut, volume, scale, subtitles, concat).
3. `data/embeddings.f32` byte length equals `chunk count × 256 × 4`, and `embeddings.json.corpusId` equals the corpus id.
4. In the browser, the first visit downloads EmbeddingGemma 2 from Hugging Face (progress in the header). After the runtime check, a task such as «сделать тише» returns a ranked list whose top commands mention volume or an equivalent documented audio adjustment, and the manual link opens the matching section.
5. The network panel shows the model request going to Hugging Face, not to the app origin. Reloading uses the browser cache for the weights and does not re-embed the corpus when the shipped matrix matches.
6. `bun run build` produces `dist/index.html`, `corpus.json`, `embeddings.json`, `embeddings.f32`, and `vendor/litertlm/`. With `GH_PAGES_PUBLIC_PATH` set, the built HTML references the script under that path.
