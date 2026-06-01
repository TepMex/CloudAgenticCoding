# Sense of Text

Sandbox for highlighting the most important words in a text. Compare local WASM embeddings (leave-one-out cosine distance) or an OpenAI-compatible LLM (JSON importance scores).

## Setup

```bash
bun install
bun dev
```

Open the URL printed in the terminal (default Bun dev server).

## Usage

1. Open **Settings** and configure:
   - **Base URL** and **API token** for LLM models (stored in `localStorage` only on your machine).
   - **Embedding models**: Hugging Face IDs for browser WASM (e.g. `Xenova/all-MiniLM-L6-v2`). First run downloads model weights.
   - **LLM models**: names your API accepts (e.g. `gpt-4o-mini`).
2. Paste text (max **80** tokens).
3. Choose a model and click **Analyze**.

Embedding analysis runs in a Web Worker; LLM requests go through a local `/api/chat` proxy to avoid CORS.

## Scripts

- `bun dev` — development with HMR
- `bun start` — production server
- `bun run build` — static build to `dist`
