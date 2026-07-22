# Offline Hanzi metadata dataset

This document describes the bundled Hanzi metadata database used by prompt placeholders in **anki-entertainer**.

## Placeholders and sources

| Placeholder | Source-backed fields | Notes |
|-------------|---------------------|-------|
| `{QUERY}` | (none) | Exact deep-link vocabulary; no database lookup |
| `{OPPOSITE}` | Unihan `kSimplifiedVariant` / `kTraditionalVariant`; OpenCC ST/TS character dicts as supplemental | Character-level only; one-to-many kept visible |
| `{SEMANTIC}` | Make Me a Hanzi `etymology.type=pictophonetic` + `semantic` | Omitted unless type is pictophonetic **and** semantic is non-null |
| `{PHONETIC}` | Make Me a Hanzi `etymology.type=pictophonetic` + `phonetic` | Same filtering as `{SEMANTIC}` |
| `{MNEMO_EXAMPLES}` | Project seed mnemonics (`CC0-1.0`) | Small seed only — **not** a large community corpus; keys may be single Han or compounds |
| `{SIMPL_HISTORY}` | Unihan/OpenCC variant pairs + MMAH IDS; optional curated seed rows | Distinguishes **curated** vs **derived** structural comparison |

## Source-backed vs derived

**Source-backed**

- Variant pairs from Unihan (primary) and OpenCC (supplemental).
- Pictophonetic semantic/phonetic components from Make Me a Hanzi.
- Curated simplification explanations in `tools/hanzi-data/hanzi_data/seed/curated_simplifications.json`.
- Seed mnemonic stories and their attribution/license fields.

**Derived by this project**

- `{SIMPL_HISTORY}` classifications and prose produced by comparing IDS/decomposition trees when no curated row exists.
- These are **structural comparisons**, not proven historical etymology.
- Derived rows store `evidenceType=derived` and a confidence score in the database.

## Precedence

1. **Variants:** Unihan edges win for a source character + direction. OpenCC may add additional targets (marking the mapping ambiguous) when Unihan already has a mapping, or supply a mapping when Unihan has none.
2. **Simplification:** A curated row for `inputCharacter` replaces any derived explanation.
3. **Mnemonics:** Multiple providers can be merged; ranking uses normalized score, then source priority, then stable source/id tie-breakers.

## Known ambiguities

- Characters such as `发` map to multiple traditional forms (`發` / `髮`). Placeholders show all targets and mark them context-dependent rather than picking one without phrase context.
- Unihan and OpenCC sometimes disagree; supplemental OpenCC targets are retained and marked ambiguous.
- Some MMAH IDS strings are incomplete (`？`); structural comparison then classifies as `UNKNOWN`.

## Mnemonic ranking

1. Prefer an explicit source-provided score/rank when present (`raw_score` / `normalized_score`).
2. Otherwise use a documented normalized score (seed files set both when available).
3. Tie-break: higher `source_priority`, then `source`, then `source_record_id`.
4. Keep at most five stories per mnemonic key (single Han character **or** contiguous compound word) after whitespace normalization, near-duplicate removal, and a 500 code-point story cap.
5. Attribution and license remain in the database even when omitted from prompt text.

**Compound words:** seed rows may use a multi-character Han key (e.g. `休息`). Prompt `{MNEMO_EXAMPLES}` and offline fallback look up contiguous Han runs in the query first, then each unique character.

**Coverage limitation:** only a small project-authored CC0 seed set is bundled. Do not treat rankings as community popularity.

## Rebuild the database

Ordinary Android/Gradle builds **do not** download Hanzi sources.

```bash
# From anki-entertainer/
python3 tools/hanzi-data/build.py
```

Offline (cached pinned downloads must already match `sources.lock.json`):

```bash
python3 tools/hanzi-data/build.py --offline
```

Pipeline steps: read lock file → download/verify SHA-256 → parse → merge → IDS structural diff → mnemonic rank → write SQLite → copy to `app/src/main/assets/databases/hanzi_metadata.db` → write `tools/hanzi-data/out/build-report.json` and `THIRD_PARTY_NOTICES.md`.

After Room schema changes, run `./gradlew :app:kspDebugKotlin` so `app/schemas/.../1.json` exists, then rebuild so `room_master_table.identity_hash` matches.

## Database and APK size

See `tools/hanzi-data/out/build-report.json` for the current generated database byte size (on the order of ~7 MB uncompressed). APK packaging compresses assets; measure with `./gradlew assembleDebug` and inspect `app/build/outputs/apk/debug/`.

## Adding another dataset

1. Add a provider under `tools/hanzi-data/hanzi_data/` (or a new `JsonMnemonicProvider` seed file).
2. Pin URL/path, revision, SHA-256, and license in `tools/hanzi-data/sources.lock.json`.
3. Confirm redistribution inside an APK is allowed; update `THIRD_PARTY_NOTICES.md` / rebuild notices.
4. Wire the provider into `build.py` merge/rank order.
5. Rebuild the database and re-run Android tests.

## Runtime behavior

- Lookups are offline via a prepackaged Room/SQLite database.
- Prompt expansion resolves only placeholders present in the template.
- If the database is missing/corrupt, `{QUERY}` still works; metadata placeholders expand to empty strings with a warning.
