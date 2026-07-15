# Map of Chinese

Map of Chinese is a static React application that places Mandarin characters on an initial × final matrix. Tones are switchable reading layers, polyphonic characters can occupy multiple cells, and old/new HSK membership can be inspected without assigning levels to individual pronunciations.

The default scope is the officially ranked first tier of 3,500 characters from the 2013 **通用规范汉字表**. Extended scopes retain HSK characters outside that set.

## Interface preview

> **Desktop screenshot placeholder** — full matrix with sticky initial/final headers and persistent details drawer.
>
> **Mobile screenshot placeholder** — touch-scrollable matrix with filter deck and bottom-sheet details.

The implemented interface is usable immediately with `bun run dev`; these placeholders are intended to be replaced by deployment screenshots.

## Quick start

Requires Bun 1.3 or newer.

```bash
bun install
bun run data:build
bun run dev
```

Verification:

```bash
bun run data:validate
bun run test
bun run build
```

No network access is needed at runtime or during `data:build`; source snapshots are stored under `data/sources/`.

## Data build

`scripts/build-data.ts` performs a deterministic local build:

1. Reads rows 1–3,500 from the `字表8105` worksheet and preserves their official ordering.
2. Opens every text file in the Unicode 17.0 Unihan archive and indexes records by property name, never by assumed filename.
3. Combines and normalizes `kHanyuPinyin` and `kMandarin` readings and reads variant/definition properties.
4. Splits the 5,000 old-HSK vocabulary rows into Han characters, assigns the earliest level, and stores evidence words.
5. Loads incremental `hsk3_2026` recognition-character lists with a single `"7-9"` advanced value.
6. Builds character, syllable-cell, and search indexes; validates them with Zod and explicit invariants. Generated timestamps are fixed to the snapshot retrieval date unless `SOURCE_DATE_EPOCH` is supplied.
7. Writes the same deterministic JSON payloads to `data/generated/` and `src/data/`.

Generated payloads:

- `characters.json`
- `syllable-cells.json`
- `search-index.json`
- `data-manifest.json`
- `validation-report.json`

The build prints each JSON size and exits non-zero when an invariant fails.

## Pinyin normalization

The pure TypeScript parser accepts marked and numbered tones, NFC-normalizes input, treats unmarked syllables as neutral tone 5, and canonicalizes `u:`, `v`, and `ü`. It matches `zh/ch/sh` before single-letter initials, rewrites orthographic `y/w` forms to their underlying finals, maps `ju/qu/xu` to the ü family, and uses `apical-i` internally for `zi/ci/si/zhi/chi/shi/ri`.

Rare source readings that do not fit the teaching matrix are retained in a visible **Special syllabic readings** group. See [docs/pinyin-normalization.md](docs/pinyin-normalization.md).

## HSK interpretation

- **Old HSK 2.0:** levels are derived from the earliest vocabulary-list occurrence of each character. Evidence words are retained. This is not an official character-level classification.
- **New HSK 3.0 (2026):** direct recognition-character assignments from the November 2025 syllabus; levels are incremental and 7–9 remains a single combined band.

The supplied acceptance contract requires 344 level-6 / 3,019 total new-HSK characters, while the currently available November 2025 source extraction contains 413 / 3,088. The application follows the requested count contract, and the remaining 69 source-ordered records are listed—not silently dropped—in `validation-report.json`. See **Known limitations** below.

## Directory structure

```text
map-of-chinese/
├── data/
│   ├── generated/                 # validated static outputs
│   └── sources/                   # immutable local source snapshots
│       ├── hsk2_2015/
│       └── hsk3_2026-extraction/
├── docs/
│   ├── data-model.md
│   └── pinyin-normalization.md
├── scripts/
│   ├── build-data.ts
│   └── validate-data.ts
├── src/
│   ├── data/                      # generated files bundled by Vite
│   ├── lib/pinyin.ts
│   ├── App.tsx
│   ├── main.tsx
│   └── styles.css
├── NOTICE.md
├── index.html
├── package.json
└── vite.config.ts
```

## Source and licensing summary

- 通用规范汉字表: Ministry of Education / State Language Commission, 2013; official public standard, with a reviewed digital workbook used as the local extraction aid.
- Unicode Unihan 17.0: © Unicode, Inc.; used under the [Unicode Terms of Use](https://www.unicode.org/terms_of_use.html).
- HSK source documents: Chinese Testing International / Center for Language Education and Cooperation; source notices and extraction commits are recorded in `data/sources/README.md`.
- CC-CEDICT: optional enrichment path, licensed CC BY-SA 4.0. It is not bundled in this snapshot; attribution is retained in `NOTICE.md` for builds that enable it.

See [data/sources/README.md](data/sources/README.md) and [NOTICE.md](NOTICE.md) for full provenance and checksums.

## Known limitations

1. The official HSK 2026 source extraction has 69 more level-6 recognition characters than the requested acceptance count. They remain in the validation report pending a product/source decision.
2. The accessible old-HSK row extraction has a 299/601 level-3/4 row boundary while the official cumulative totals are 600/1,200. All 5,000 rows are accounted for and the official cumulative totals are validated.
3. CC-CEDICT enrichment is optional and not bundled, so definitions come from Unihan and example words come from old-HSK evidence in this snapshot.
4. Unihan dictionary readings are intentionally broad; uncommon, literary, and dialectal readings may be visible.
5. The matrix is not a phonological claim that every geometrically possible cell is valid. Impossible combinations are retained as muted educational negative space.

## Build

```bash
bun run data:build
bun run build
```

Static output is in `dist/` — serve with any static host.

For the same asset paths as **GitHub Pages** on a **project site** (repo `my-repo` → `/my-repo/map-of-chinese/`):

```bash
GH_PAGES_PUBLIC_PATH=/my-repo/map-of-chinese/ bun run build
```

## GitHub Pages (this monorepo)

The root workflow [`.github/workflows/deploy.yml`](../.github/workflows/deploy.yml) runs `data:build`, builds this app, and copies `dist/` to `deploy/map-of-chinese/` on the `gh-pages` branch. After a push to `master`, the app is available at:

`https://<user-or-org>.github.io/<repository>/map-of-chinese/`

CI sets `GH_PAGES_PUBLIC_PATH` so Vite emits correct asset URLs under that prefix. No server rewrite or runtime API is required.
