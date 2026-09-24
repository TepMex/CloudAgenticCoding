# Notices

## CC-CEDICT

`app/src/main/assets/lexicon/words.tsv` is derived from [CC-CEDICT](https://www.mdbg.net/chinese/dictionary?page=cc-cedict) (simplified headwords and Hanyu pinyin).

CC-CEDICT is licensed under the [Creative Commons Attribution-ShareAlike 3.0 License](https://creativecommons.org/licenses/by-sa/3.0/).

## Wikidata interwiki titles

Russian glosses that come from Chinese–Russian Wikipedia titles are derived from [open-dict-data/wikidict-ru](https://github.com/open-dict-data/wikidict-ru) and the single-character extract in `hanzi-info`. That data is under [CC0](https://creativecommons.org/publicdomain/zero/1.0/).

A short supplement in `scripts/build-lexicon.py` fills frequent words that have no Wikipedia title.

Regenerate the TSV with `python3 scripts/build-lexicon.py`.
