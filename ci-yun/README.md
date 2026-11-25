# 词云

Mandarin vocabulary from the HSK 1000–5000 and Refold 1k Simplified decks, embedded with EmbeddingGemma 2 and drawn as a cluster cloud on a plane and in space.

```bash
bun install
bun run dev
bun run test
bun run build
```

The vectors already live in `data/embeddings.sqlite`. Rebuilding them needs the original `.apkg` files and a CPU torch install (see `requirements-embed.txt`):

```bash
python scripts/build_cloud.py \
  --hsk "Mandarin HSK 1000-5000.apkg" \
  --refold "Refold Mandarin 1k Simplified.apkg" \
  --json src/data/cloud.json \
  --sqlite data/embeddings.sqlite \
  --names data/cluster-names.json
```

Spec: `SPEC.md`.
