# Polish frequency candidates: SUBTLEX-PL + KWJP

This project builds an auditable list of frequent Polish **lemmas** for a later
Anki-deck pipeline.  It does not translate, detect cognates, generate examples,
or use an LLM.  One command downloads two official frequency lists, verifies
pinned SHA-256 hashes, preserves source-specific data, and produces a union
ranking with 3,000 unique candidates by default.

## Quick start

Python 3.11 or newer is required.  The implementation uses only the standard
library, so no third-party NLP model or package is needed.

```bash
python -m src.build_frequency_list \
  --top 3000 \
  --subtlex-weight 0.65 \
  --kwjp-weight 0.35
```

Force a clean re-download (the downloaded bytes must still match the pinned
hashes):

```bash
python -m src.build_frequency_list --force-download
```

Run the tests:

```bash
python -m unittest discover -v
```

To retain candidates conservatively flagged as proper names in the final top
file, add `--include-proper-names`.  Proper-name candidates are never removed
from `all_candidates.csv`.

## Primary sources

### SUBTLEX-PL

- Official OSF project: <https://osf.io/5a76z/>
- File selected through the OSF API: `subtlex-pl-lemmas-master.csv`
- Actual stable download link returned by OSF: <https://osf.io/download/ab9ym/>
- Article: Mandera, Keuleers, Wodniecka & Brysbaert (2015),
  <https://doi.org/10.3758/s13428-014-0489-4>
- License published with the OSF data: CC BY-NC-SA 4.0

The OSF project also contains surface-form files and raw R objects.  They are
not needed here because the master lemma file already provides author-computed
`lemma + POS` totals.  Downloading or re-tokenizing subtitles would add a much
larger and less reproducible step without improving this stage.

### KWJP

- Frequency-list documentation: <https://kwjp.pl/lists/doc/about/en>
- Official repository: <https://github.com/ipipan/kwjp100-varia/tree/main/freqlists>
- Actual download URL:
  <https://raw.githubusercontent.com/ipipan/kwjp100-varia/main/freqlists/kwjp100-slowa-lemma-all.csv.gz>

The selected `kwjp100-slowa-lemma-all.csv.gz` is the balanced, whole-corpus,
single-word **lemma** list.  No n-gram, `orth`, or genre-only list is used.

SUBTLEX and KWJP represent different registers.  SUBTLEX emphasizes dialogue
from film and television; KWJP is a balanced contemporary corpus that includes
written registers.  Neither is treated as the one correct list: their different
coverage is the reason to combine them.

## Observed schemas (not assumed schemas)

The parsers fail loudly if the schemas change.

SUBTLEX is tab-separated despite its `.csv` extension:

```text
lemma  pos  spelling  freq  cd.count  cd
```

It uses a hierarchical flat representation:

- `spelling == "%"` is an author-computed `lemma + POS` summary;
- `lemma == "@"` and `pos == "@"` is a child word-form row.

Only summary frequencies are aggregated.  Child frequencies are retained only
indirectly in the surface-form diagnostic count and are **never** added to a
repeated lemma total.  The sum of all summary frequencies is 145,982,416; that
observed total is the denominator used to derive lemma IPM.  The chosen lemma
file does not contain an author-published lemma Zipf column, so `subtlex_zipf`
is derived from IPM rather than invented or copied from a surface form.

The KWJP gzip contains a normal comma-separated file, but its first two header
cells are empty:

```text
"", "", freq, ipm, ARF, DP, DP_norm, 1-DP, total_freq
```

The documentation and row contents establish the first two columns as `lemma`
and `POS`; the parser records that mapping in processed output and `stats.json`.
Contrary to the web documentation's display description, this downloadable CSV
does not include an `R` column.  `source_row_rank` preserves its frequency-sorted
row number; after multiple POS rows are aggregated, `kwjp_rank` is recomputed at
orthographic-lemma level.

## Normalization and aggregation

Normalization is deliberately conservative:

- trim, Unicode NFC, and lowercase;
- preserve Polish diacritics and one-letter function words;
- do not stem or re-lemmatize;
- reject empty values, URLs, emails, pure numbers, punctuation-only tokens,
  entities/markup, control characters, and clear mojibake;
- retain plausible alphanumeric lemmas but add `contains_digit`.

Every accepted source row is written to `data/processed/*_lemma_pos.csv`, and
every rejected row and reason to `*_filtered.csv`.  POS-specific rows are first
aggregated inside each source, then merged by normalized orthographic lemma.
The final `pos_primary` and `pos_all` use a small transparent mapping from the
NKJP-style detailed tags to broad classes; raw POS and frequency breakdowns are
also retained.

For multi-POS KWJP lemmas, `freq`, IPM, and ARF are summed.  `1-DP` is a
frequency-weighted mean because the corpus-by-document counts required to
recompute dispersion exactly are not in the list.  For SUBTLEX, `cd` and
`cd.count` use the maximum POS-specific value at lemma level: summing them would
double-count films shared by POS readings.  These dispersion fields are
diagnostics, not ranking inputs.

## Metrics and ranking

- **frequency / F**: source raw occurrence count; never added across corpora.
- **IPM**: items per million, additive across POS within one source.
- **Zipf**: `log10(IPM) + 3` for positive IPM.
- **CD**: SUBTLEX contextual diversity; `cd.count` is films and `cd` is its
  proportional form.
- **ARF**: KWJP average reduced frequency, which discounts clustering.
- **1-DP**: KWJP dispersion transformed so values near one are more even.

The default `combined_score` is the weighted mean of comparable Zipf values:

```text
0.65 × SUBTLEX Zipf + 0.35 × KWJP Zipf
```

Dialogue receives a modestly larger weight because this list targets practical
everyday comprehension.  Both weights are CLI parameters and must be
non-negative and sum to 1.

The union, not an inner join, is ranked.  A missing source is treated as
censored below that source's released frequency range: its value is one Zipf
unit below the smallest positive IPM observed in that source.  This is an
explicit low-frequency floor, not zero, a mean, or a fabricated raw count.
`stats.json` records the actual floors.

`combined_score_alt` is weighted percentile-rank fusion.  A missing source
contributes zero evidence to that alternative.  In this dataset the primary
Zipf score is preferred because it preserves meaningful differences among the
very frequent words; percentile ranks compress most of a 570k-item union near
one and are more useful as a robustness diagnostic.

## Flags and sanity checks

`low_dispersion` is diagnostic only.  It is set when either:

- SUBTLEX frequency is at least 1,000 but `cd < 0.01`; or
- KWJP IPM is at least 1 but `1-DP < 0.25`.

`suspected_proper_name` is set only for noun-primary lemmas where at least 90%
of the source frequency is attached to capitalized originals.  For SUBTLEX this
uses its child spellings (not their frequencies for ranking), because summary
lemmas are usually lowercased; KWJP uses its case-preserving lemma column. These rows
stay in `all_candidates.csv`; the default top file skips them and reports the
count.  This is intentionally a heuristic, because neither selected list has a
reliable named-entity field.

The build aborts if the top list contains duplicate lemmas or loses almost all
of a diagnostic set of common function words.  The diagnostic set is not added
to or otherwise used to alter the ranking.

## Outputs

```text
data/raw/                       downloaded source bytes
data/processed/
  subtlex_lemma_pos.csv         accepted source rows, before POS aggregation
  subtlex_filtered.csv          rejected rows and reasons
  kwjp_lemma_pos.csv
  kwjp_filtered.csv
output/
  top3000.csv                   requested unique candidate list
  all_candidates.csv            complete source union
  top100_debug.csv              first 100 unfiltered combined rows
  stats.json                    schemas, counts, parameters, sanity results
  source_manifest.json          URLs, timestamps, byte sizes, SHA-256 hashes
```

Generated raw, processed, and output files are ignored by Git but remain on
disk.  Empty `.gitkeep` files preserve the reproducible directory layout.

## Reproducibility and limitations

Both current source hashes are pinned in code and emitted in the manifest.  A
silent upstream replacement therefore fails instead of changing the ranking.
To adopt a legitimate new release, inspect its real schema and results first,
then deliberately update the pinned hash.

Corpus lemmatization and tagging are automatic and contain errors.  Lowercasing
can merge a capitalized name with a lowercase common lemma.  The source lists
have different coverage thresholds, registers, tokenization, and tag sets.
ARF/CD aggregation cannot reproduce document-level statistics without raw
texts, so it is kept diagnostic.  The output is a high-quality candidate list,
not a claim that every row is pedagogically suitable; translation, cognate
filtering, false-friend detection, and example generation belong to later
pipeline stages.
