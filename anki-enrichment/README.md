# Anki Enrichment

В проекте два AnkiConnect-инструмента, которые обновляют существующие notes и не
пересоздают cards:

- `ru_translation.py` добавляет русские переводы;
- `same_hanzi_pinyin.py` добавляет `SameHanzi` и `SamePinyinMatrix`.

Оба используют `config.yaml`, требуют запущенный Anki с AnkiConnect и перед
записью требуют подтверждение резервной копии.

## SameHanzi и SamePinyinMatrix

Для карточки `Key=N`:

- `SameHanzi` содержит через запятую слова из предыдущих notes (`Key<N`), у
  которых есть хотя бы один общий иероглиф с текущим `Simplified`;
- `SamePinyinMatrix` содержит готовый HTML карты 3×3 первого слога. Центр —
  текущее слово, окружение — только ранее изученные слова.

Порядок initials/finals соответствует проекту `map-of-chinese`. Поддерживаются
тоны, `ü/u:/v`, zero-initial `y/w`, `j/q/x + ü` и `apical-i`.

### Конфигурация

`anki.query` выбирает notes, в которые будут записаны новые поля. Отдельный
`related.history_query` задаёт полную глобальную историю для расчёта `Key<N`.
По умолчанию это обе китайские колоды, поэтому `Key=1001` видит `Key=1..1000`,
хотя обновляется только колода из `anki.query`.

```json
{
  "anki": {
    "url": "http://127.0.0.1:8765",
    "query": "deck:\"Mandarin HSK 1000-5000\""
  },
  "related": {
    "history_query": "(deck:\"Refold Mandarin 1k Simplified\" OR deck:\"Mandarin HSK 1000-5000\")",
    "key_field": "Key",
    "hanzi_field": "Simplified",
    "pinyin_field": "Pinyin",
    "pinyin_fields": {
      "HSK": "Pinyin.1"
    },
    "same_hanzi_field": "SameHanzi",
    "matrix_field": "SamePinyinMatrix",
    "overwrite_existing": true
  }
}
```

`pinyin_fields` переопределяет имя поля для конкретного Note Type. Это нужно для
текущей коллекции: у `HSK` поле называется `Pinyin.1`, а у Refold — `Pinyin`.

`overwrite_existing=true` полностью пересчитывает оба производных поля при
повторном запуске. При `false` уже заполненные значения сохраняются.

### Безопасный запуск

Аудит по умолчанию показывает выборку, отсутствующие поля и будущие значения,
но ничего не меняет:

```bash
python3 same_hanzi_pinyin.py
python3 same_hanzi_pinyin.py --audit --limit 3
```

Dry run рассчитывает данные для выбранных notes без `modelFieldAdd` и
`updateNoteFields`:

```bash
python3 same_hanzi_pinyin.py --dry-run --limit 10
```

После полной резервной копии коллекции:

```bash
python3 same_hanzi_pinyin.py --enrich --limit 10 --backup-confirmed
```

После проверки первых notes можно обработать всю выбранную колоду:

```bash
python3 same_hanzi_pinyin.py --enrich --backup-confirmed
```

Вместо ручного подтверждения скрипт может сначала экспортировать колоду с
scheduling information:

```bash
python3 same_hanzi_pinyin.py --enrich \
  --backup-deck "Mandarin HSK 1000-5000" \
  --backup-path "/absolute/path/Mandarin-HSK.apkg"
```

Если полей нет, они добавляются в Note Type через `modelFieldAdd`. Затем каждое
значение записывается в тот же note ID через `updateNoteFields`. После обновления
проверяются note ID, card IDs и scheduling. События пишутся в
`logs/same_hanzi_pinyin.jsonl`.

Чтобы показать матрицу, добавьте `{{SamePinyinMatrix}}` в Back Template и CSS из
`assets/same_pinyin_matrix.css` в Styling. `{{SameHanzi}}` можно разместить там же.

### Диагностика

```bash
python3 same_hanzi_pinyin.py --parse "zhǎng"
python3 same_hanzi_pinyin.py --dump-layout
python3 same_hanzi_pinyin.py --audit --debug-key 1001
```

Неразбираемый pinyin не прерывает расчёт всей истории: матрица этой note остаётся
пустой, а предупреждение выводится в stderr.

## Русские переводы

`ru_translation.py` добавляет в существующие notes только нужные значения:

- `MeaningRu`;
- `SentenceMeaningRu`;
- `PartOfSpeechRu`.

Сначала аудит и dry run:

```bash
python3 ru_translation.py --audit --query 'deck:"Mandarin HSK 1000-5000"'
python3 ru_translation.py --dry-run --query 'deck:"Mandarin HSK 1000-5000"' --limit 10
```

После резервной копии:

```bash
python3 ru_translation.py --enrich \
  --query 'deck:"Mandarin HSK 1000-5000"' \
  --limit 10 --backup-confirmed
```

## Проверки

```bash
python3 -m unittest discover -v
```
