# Data source ledger

Retrieval date for all local snapshots: **2026-07-15**. Generated fields include a source manifest in `data/generated/data-manifest.json`.

## Source inventory

| ID | Title / publisher | Version / date | Download or upstream location | License / usage notice | Local checksum | Generated fields |
|---|---|---|---|---|---|---|
| `tghz-2013` | 《通用规范汉字表》, Ministry of Education / State Language Commission | 2013 | [Official MOE publication page](https://www.moe.gov.cn/jyb_sjzl/ziliao/A19/201306/t20130601_186002.html); local digital extraction aid at [cdtym/digital-table-of-general-standard-chinese-characters](https://github.com/cdtym/digital-table-of-general-standard-chinese-characters), commit `0558ed6c…` | Public government standard; verify downstream reuse requirements for reproductions. The extraction workbook has its own upstream notices. | `tghz-2013.xlsx`: `9f45bff2376ded8098e1d1e79cff975406619ec66c5e6308b2716222ca6af7e1` | `standardRank`, `inBasic3500`, default scope |
| `unihan-17.0.0` | Unicode Han Database (Unihan), Unicode Consortium | Unicode 17.0.0; files dated 2025-07-24/25 | [Official Unihan.zip](https://www.unicode.org/Public/UNIDATA/Unihan.zip), [UAX #38](https://www.unicode.org/reports/tr38/) | [Unicode Terms of Use](https://www.unicode.org/terms_of_use.html) | `Unihan-17.0.0.zip`: `f7a48b2b545acfaa77b2d607ae28747404ce02baefee16396c5d2d7a8ef34b5e` | readings, preferred flags, variants, definitions, code-point provenance |
| `hsk2_2015` | 汉语水平考试（HSK）词汇表（2015版）, Chinese Testing International | 2015 | Original workbook is not currently exposed at a stable public CTI URL. The checked-in row extraction comes from [plaktos/hsk_csv](https://github.com/plaktos/hsk_csv), commit `615534d3…`; it is treated only as an extraction aid for the official list. | Publisher usage notice applies to the original workbook; extraction repository provides no license file. Do not assume unrestricted relicensing. | Per-file checksums below | `hsk2Level`, `hsk2EvidenceWords`, HSK example words |
| `hsk3_2026` | 《HSK考试大纲》, Center for Language Education and Cooperation / Chinese Testing International | Published 2025-11; implemented 2026-07 | [Official 406-page syllabus PDF](https://hsk.cn-bj.ufileos.com/3.0/%E6%96%B0%E7%89%88HSK%E8%80%83%E8%AF%95%E5%A4%A7%E7%BA%B21219.pdf). Checked-in OCR/extraction aid: [krmanik/HSK-3.0](https://github.com/krmanik/HSK-3.0), commit `182692ce…`. | Official document usage notice applies; extraction repository has a separate license. Lists were checked by count against the source section. | Per-file checksums below | `hsk3_2026Level`, extended scope, validation totals |
| `cc-cedict` | CC-CEDICT | Optional / not bundled | [MDBG CC-CEDICT download page](https://www.mdbg.net/chinese/dictionary?page=cc-cedict) | CC BY-SA 4.0; attribution and share-alike required | Not applicable in this snapshot | Optional definitions, mappings, reading confirmation, examples |

## HSK 2.0 extraction checksums

```text
hsk1.csv  fd65c6b0c221ac2766ad3f42e0f19e1851f75cdc8067c48d266f9a8ea6cab9b3
hsk2.csv  fc788044b8ae709fb33c9b9a0f9516b6bf46d13c126b4d60366ce004a5a6091e
hsk3.csv  ff3e8b74d251abeae2e0ec4c22c2d4640071b2a236924846a161ac9ba255a70a
hsk4.csv  d5a3869d4d2877a7d5e467b9e00926972ffc31fb52767a9df309fce5ff6ee6cd
hsk5.csv  fa9997655735b725002b66a091365a6ce6e1bcad216c658eb7eccfee15866fdf
hsk6.csv  146839cf1649d29e7fa75446f7c2df6aaf4b2b55e38ef4b09cc5dd866d925e5f
```

The files contain 150, 150, 299, 601, 1,300, and 2,500 rows. This preserves all 5,000 source rows but exposes a one-row level 3/4 boundary mismatch versus the official cumulative totals 600/1,200. The mismatch is retained in the validation report.

## hsk3_2026 extraction checksums

```text
Level 1    ced676f85a85d026b45acc7508cc7a717c32a5f1a51d6d45fbf4fcc2a27baa47
Level 2    14890e2a1a08bf4da45bbd9bd49989b280c8e67c7f60aaa44fce77b0bf3cf30c
Level 3    ccb8bf9730d863571339276f58bc13507490c90308042b12de364960a252dcb1
Level 4    a01fa0be84ef7f3eca73eb6748ed4d47ae55169c7b7d0f6a871f3e09819998cc
Level 5    ffe11b27379d57c9f4d1e0382eae01bdcd17194e23e2c4363322109dd15132e7
Level 6    ae118eaa9cda177af90570eee7afd7a18a360d2df31de1d54bea27d0e9c06a60
Level 7–9  74783ee2d65922ed478e08753e5cd1ef20d3d8ccea1189b40ac6ed2243044571
```

Source extraction counts are 246, 125, 284, 441, 431, **413**, and 1,148 (3,088 total). The requested product contract is 246, 125, 284, 441, 431, **344**, and 1,148 (3,019 total). The final 69 source-ordered level-6 records are retained verbatim in `validation-report.json` as an unreconciled set.

## Parsing policy

- Source files are immutable inputs; the pipeline never downloads data.
- All eight Unihan text entries in the archive are scanned. Properties are selected by the second tab-separated field, not by filename.
- No malformed reading is silently skipped: invalid records are listed in the validation report.
- HSK rows excluded from the product count contract remain explicitly accounted for in reconciliation metadata.
