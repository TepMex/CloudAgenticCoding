# Russian gloss data

- **`wiki_zh_ru_single_char.json`** — Single-character Chinese keys to Russian Wikipedia article titles, merged from [open-dict-data/wikidict-ru](https://github.com/open-dict-data/wikidict-ru) (`zh-ru_wiki.txt`) and [open-dict-data/wikidict-zh](https://github.com/open-dict-data/wikidict-zh) (`ru-zh_wiki.txt`). Regenerate with `bun run extract:wiki-ru`.

- **`gloss_en_to_ru.json`** — Maps each distinct CC-CEDICT English gloss string to Russian text (machine translation via [MyMemory](https://mymemory.translated.net/doc/usagelimits.php)). Extend or refresh with `bun run fill:ru-gloss` (subject to API limits; safe to re-run — it resumes from existing keys).

The build script merges these into `meaning_ru` on each row (English-gloss translation first, then Wikipedia title when still empty).
