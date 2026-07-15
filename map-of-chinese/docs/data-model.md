# Data model

## Character records

`characters.json` is an ordered array. Basic characters appear first in official `standardRank` order; extended-only characters follow in stable Unicode code-point order.

Each record contains:

- identity: `character`, `codePoint`, `simplified`, `traditional`
- standard scope: `standardRank`, `inBasic3500`
- pronunciation: normalized `readings[]`
- enrichment: `definitions[]`, `exampleWords[]`
- character-level learning metadata: `hsk2Level`, `hsk2EvidenceWords`, `hsk3_2026Level`

HSK values belong to the character record, never to a reading.

## Reading records

Every reading carries display (`pinyinMarked`), search (`pinyinNumbered`), placement (`initial`, `final`), tone, preferred status, and source provenance. Duplicate source readings are merged by normalized numbered pinyin, and their source arrays are combined. When a first-tier character has no `kHanyuPinyin` record, the official Unihan `kTGHZ2013` reading field is used as a documented fallback; `kMandarin` still determines the preferred reading.

## Syllable cells

`syllable-cells.json` pre-groups lightweight entries by `initial|final`. Entries retain tone and preferred-reading state so the browser can filter reading layers without rescanning all character records. Character details are resolved through a prebuilt in-memory character map.

`apical-i` is an internal final key. The interface displays it as `i`. Non-matrix readings use a `special:` final prefix.

## Search index

`search-index.json` stores one pre-normalized searchable string and a list of cell keys per character. It covers hanzi, simplified/traditional forms, marked/numbered/untoned pinyin, normalized ü/v search forms, definitions, and HSK expressions.

## Manifest and report

`data-manifest.json` records source versions, checksums, counts, and reconciliation policy. `validation-report.json` records totals, source accounting, outside-basic characters, invalid source readings, and any unreconciled records.
