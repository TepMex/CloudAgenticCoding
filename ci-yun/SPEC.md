# 词云 (ci-yun) — SPEC

## Purpose

A browser map of the Mandarin vocabulary in two Anki decks: **Mandarin HSK 1000–5000** and **Refold Mandarin 1k Simplified**. Each sense from the **Simplified** field is embedded with **EmbeddingGemma 2** (`google/embeddinggemma-2`), clustered, and drawn as a cloud the reader can lay on a plane or tumble in space. Cluster names are editorial. Typing a word from the list moves that word to the center of the view.

Audience: a learner browsing these decks by meaning rather than by HSK level or alphabetical order.

## Requirements

1. Take vocabulary from the two `.apkg` decks. The identity of an entry is the **Simplified** field. Keep pinyin, English gloss, Russian gloss, part of speech, deck source, and HSK tag when the note has them.
2. Treat the same simplified string with a different English gloss as two senses (for example 还 hái and 还 huán). Merge a cross-deck pair only when simplified and English gloss match.
3. Embed every sense with EmbeddingGemma 2, text tower only (vision and audio encoders off), **float32**, clustering prompt `task: clustering | query: `, output **768** dimensions, L2-normalized. The embedded string is `{simplified}: {english gloss}` so every row shares one gloss language (Russian glosses are missing on most of the HSK deck).
4. Store the vectors in a SQLite database in the project. The database is an artifact of the build, not something the page recomputes.
5. Project the same vectors to a plane and to a 3D layout (UMAP). Cluster once in embedding space (k-means) and use that assignment in both layouts.
6. Give every cluster a short Russian name that describes the sense group, plus a stable color.
7. Render the cloud in WebGL. A control switches **Плоскость** / **Пространство** without dropping the current word.
8. The reader can type any simplified word from the list (pinyin and gloss match too) and the cloud recenters on that sense. Both senses appear when a string is shared. A string that is not in the list is reported as absent.
9. Clicking a point does the same recentering. Choosing a cluster recenters on its centroid and quiets the other clusters. **Всё облако** returns to the full view.
10. A selected sense shows simplified, pinyin, gloss, part of speech, source, HSK tag, cluster name, and nearest neighbors in the embedding space. Neighbors are clickable.
11. The page is static. No account, no runtime model download, no call to an embedding API from the browser.
12. GitHub Pages builds the folder `ci-yun` like the other web apps. The Python embedding step is not part of CI.
13. Zooming the cloud, on the plane or in space, draws each point's simplified word as a caption once that caption would not cover any other word caption. While the captions would cover one another they stay hidden, including on the wide shot. A caption already on screen remains until its glyph box would meet another point's caption, so zooming back out clears the wide shot. The focused word keeps its large caption instead of a second small one. A cluster name that would cover a word caption stays hidden.

## Interfaces

| Interface | Detail |
| --------- | ------ |
| Pages path | `/ci-yun/` |
| Dev | `bun install` then `bun run dev` inside `ci-yun` |
| Build | `bun run build` → `dist/` |
| Test | `bun run test` |
| Layout file | `src/data/cloud.json` (bundled) |
| Embedding database | `data/embeddings.sqlite` |
| Cluster names | `data/cluster-names.json` |
| Rebuild | `python scripts/build_cloud.py --hsk DECK.apkg --refold DECK.apkg --json src/data/cloud.json --sqlite data/embeddings.sqlite --names data/cluster-names.json` |
| Model | `google/embeddinggemma-2`, prompt name `Clustering`, dim 768, float32 |
| Decks | Anki field **Simplified**. HSK note also uses Pinyin.1, Meaning, MeaningRu, Part of speech, PartOfSpeechRu, tags `HSK1`–`HSK6`. Refold uses Pinyin, Meaning, MeaningRu, Part of speech, PartOfSpeechRu, the same tags. |

SQLite:

- `meta` — model id, prompt, dimension, dtype, seed, k
- `words` — one row per sense, `embedding` is little-endian float32, length 768, L2 norm 1
- `neighbors` — eight nearest other senses by cosine similarity, nearest first

`cloud.json` repeats the metadata needed to draw the page and omits the raw vectors. Word `id` equals the array index and the SQLite primary key.

## Data model

- **Sense** — one simplified headword plus one English gloss. Pinyin, Russian gloss, part of speech, sources (`hsk`, `refold`), and HSK tags hang off the sense.
- **Embedding** — the 768-d unit vector for that sense.
- **Layout** — a 2D point `(x2, y2)` and a 3D point `(x, y, z)`, each cloud scaled to RMS radius 10.
- **Cluster** — an id, a Russian name, a color, and the senses assigned to it.
- **Neighbor** — another sense close in embedding space, used for the lines and the card.

Example sentences, audio, and images from the decks are not stored.

## UI / UX

- Full-viewport WebGL field on a dark ground. Points are colored by cluster. A soft region sits behind each cluster. Names float at centroids and step aside when they would overlap; the active name stays.
- **Плоскость** is an orthographic pan-and-zoom map. **Пространство** is a perspective orbit.
- Search box at the top left. Suggestions open as the reader types. Enter or a click centers that sense and zooms in from the wide shot. The centered hanzi is drawn at the focal point. The card lists the gloss and the neighbors.
- Cluster chips along the bottom, largest first. The active chip dims the other points.
- Hovering a point shows the headword, pinyin, and gloss.
- Zooming in places the simplified word above each point that has room. Captions stay off while they would cover one another, so the wide shot keeps the cluster names. A cluster name yields when a word caption would cover it.
- Copy is Russian. Hanzi use a serif stack that falls back to a CJK system font.

## Out of scope

- Embedding a word that is not in the two decks.
- Editing clusters or names in the browser.
- Audio, images, and example sentences from the notes.
- Rebuilding embeddings on GitHub Actions.
- A simplified/traditional toggle. Traditional is stored and shown on the card when it differs.

## Acceptance criteria

1. `data/embeddings.sqlite` has one row per sense in `src/data/cloud.json`, each embedding 768 float32 values with L2 norm 1, and `meta.model` is `google/embeddinggemma-2`.
2. Senses from both decks are present, including 我 (Refold) and 经济 (HSK). 还 hái and 还 huán are two points.
3. Every sense has a cluster. Every cluster has a non-empty Russian name that is not a placeholder (`Кластер N`).
4. The page draws the cloud in WebGL. **Плоскость** and **Пространство** show different layouts.
5. Entering a listed word centers the view on it and opens its card. Entering a string that is not listed shows that it is absent.
6. Choosing a cluster recenters on that group. **Всё облако** shows the whole cloud again.
7. `bun run test` passes. `bun run build` emits `dist/index.html`.
8. The wide shot draws no word captions. After zooming until neighboring captions would sit apart, those points show their simplified words, and the captions do not cover one another.
