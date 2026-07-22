# Third-party notices — anki-entertainer Hanzi metadata

This file documents third-party datasets bundled in the Hanzi metadata
SQLite database shipped with the anki-entertainer APK.

## Unicode Unihan Database

- Lock key: `unihan`
- Version / revision: `16.0.0`
- URL: https://www.unicode.org/Public/16.0.0/ucd/Unihan.zip
- License: Unicode License v3
- License URL: https://www.unicode.org/license.txt
- SHA-256: `b8f000df69de7828d21326a2ffea462b04bc7560022989f7cc704f10521ef3e0`
- Used for: OPPOSITE, SIMPL_HISTORY variant pairs

## OpenCC STCharacters

- Lock key: `opencc_st`
- Version / revision: `ver.1.1.9`
- URL: https://raw.githubusercontent.com/BYVoid/OpenCC/ver.1.1.9/data/dictionary/STCharacters.txt
- License: Apache-2.0
- License URL: https://github.com/BYVoid/OpenCC/blob/ver.1.1.9/LICENSE
- SHA-256: `ed1d268e0ad028511dcf5b0089faed0a980ad332449ec11d481ceefde6879f41`
- Used for: OPPOSITE supplemental mappings

## OpenCC TSCharacters

- Lock key: `opencc_ts`
- Version / revision: `ver.1.1.9`
- URL: https://raw.githubusercontent.com/BYVoid/OpenCC/ver.1.1.9/data/dictionary/TSCharacters.txt
- License: Apache-2.0
- License URL: https://github.com/BYVoid/OpenCC/blob/ver.1.1.9/LICENSE
- SHA-256: `6b5a0a799bea2bb22c001f635eaa3fc2904310f0c08addbff275477a80ecf09a`
- Used for: OPPOSITE supplemental mappings

## Make Me a Hanzi dictionary.txt

- Lock key: `makemeahanzi_dictionary`
- Version / revision: `bddc96d41bef78427ed0e034e9f7e31d71fd1b92`
- URL: https://raw.githubusercontent.com/skishore/makemeahanzi/bddc96d41bef78427ed0e034e9f7e31d71fd1b92/dictionary.txt
- License: LGPL-3.0-or-later
- License URL: https://github.com/skishore/makemeahanzi/blob/bddc96d41bef78427ed0e034e9f7e31d71fd1b92/COPYING
- SHA-256: `744bb05d5b0742e9ee35c37791f94d56a173349b3367569e7ca11e510364d203`
- Used for: SEMANTIC, PHONETIC, SIMPL_HISTORY IDS decomposition

## anki-entertainer seed mnemonics

- Lock key: `project_seed_mnemonics`
- Version / revision: `1.0.0`
- Path: `hanzi_data/seed/mnemonics.json`
- License: CC0-1.0
- Used for: MNEMO_EXAMPLES
- Note: Small project-authored seed set (single Han and compound keys). Not a large community mnemonic corpus.

## anki-entertainer curated simplifications

- Lock key: `project_curated_simplifications`
- Version / revision: `1.0.0`
- Path: `hanzi_data/seed/curated_simplifications.json`
- License: CC0-1.0
- Used for: SIMPL_HISTORY curated explanations

## Make Me a Hanzi LGPL notice

`dictionary.txt` from Make Me a Hanzi is licensed under LGPL-3.0-or-later.
A copy of the upstream COPYING notice is retained under
`tools/hanzi-data/cache/mmah-COPYING` when sources are downloaded.
Modifications consist of importing selected fields into a normalized SQLite schema.

## Unicode notice

Unicode Data Files include Unihan.zip fields used for simplified/traditional variants.
Copyright © Unicode, Inc. See https://www.unicode.org/license.txt

