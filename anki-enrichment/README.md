# Anki Enrichment

Инструмент добавляет в **существующие** Anki notes три поля и заполняет только
недостающие значения через OpenAI-compatible Chat Completions API:

- `MeaningRu` — перевод значения в контексте карточки;
- `SentenceMeaningRu` — перевод примера;
- `PartOfSpeechRu` — часть речи по-русски.

Notes и cards не пересоздаются. Запись выполняется только через AnkiConnect
`modelFieldAdd` и `updateNoteFields`; после каждого обновления проверяются note ID,
card IDs и scheduling (`type`, `queue`, `due`, `interval`, ease factor, `reps`,
`lapses`, `left`).

## Подготовка

1. Установите и запустите Anki с дополнением AnkiConnect (порт по умолчанию `8765`).
2. Проверьте mapping и query в `config.yaml`. По умолчанию они уже настроены по
   аудиту текущей коллекции: `Mandarin HSK 1000-5000`, `Simplified`, `Meaning`,
   `SentenceMeaning`, `Part of speech`. Файл использует
   JSON-compatible YAML, поэтому внешние Python-зависимости не нужны.
3. Скопируйте `.env.example` в `.env` и задайте ключ:

   ```dotenv
   OPENAI_API_KEY=...
   OPENAI_BASE_URL=https://api.openai.com/v1
   OPENAI_MODEL=gpt-4.1-mini
   ```

`OPENAI_BASE_URL` может указывать на любой совместимый endpoint; клиент добавляет
`/chat/completions`. Секрет не попадает в кеш или логи. Для локального endpoint
без авторизации задайте `api_key_env` пустой строкой. Если провайдер не принимает
OpenAI `response_format`, установите `json_response_format: false`; строгая
проверка JSON-ответа всё равно останется включённой.

## Безопасный порядок запуска

Сначала исследуйте коллекцию. Команда выводит колоды, все Note Types в выборке,
поля, templates, несколько реальных notes и предлагаемый mapping:

```bash
python3 main.py --audit --query 'deck:"Mandarin HSK 1000-5000"'
```

Затем сделайте dry run. Он не добавляет поля, не вызывает LLM и ничего не пишет:

```bash
python3 main.py --dry-run --query 'deck:"Mandarin HSK 1000-5000"' --limit 10
```

Перед первой записью экспортируйте **полную коллекцию** из Anki с scheduling
information. Первый запуск ограничьте несколькими notes:

```bash
python3 main.py --enrich --query 'deck:"Mandarin HSK 1000-5000"' --limit 10 --backup-confirmed
```

Вместо ручного подтверждения можно попросить AnkiConnect экспортировать отдельную
колоду (путь относится к компьютеру, где запущен Anki):

```bash
python3 main.py --enrich --limit 10 \
  --backup-deck 'Mandarin HSK 1000-5000' --backup-path '/absolute/path/HSK.apkg'
```

После проверки полей и scheduling команда для всей колоды:

```bash
python3 main.py --enrich --query 'deck:"Mandarin HSK 1000-5000"' --backup-confirmed
```

## Повторные запуски

По умолчанию `overwrite_existing` равен `false`: заполненные, в том числе вручную
исправленные, поля не меняются. Ответы LLM кешируются по endpoint, модели, prompt,
версии и исходным данным. Повышение `enrichment_version` создаёт новую генерацию
кеша, но само по себе не перезаписывает пользовательские данные. Для осознанной
перегенерации задайте `overwrite_existing: true`, предварительно сделав backup и
ограничив выборку.

Каждая note обрабатывается независимо; ошибки пишутся в `logs/enrichment.jsonl`,
после чего работа продолжается. Недоступность AnkiConnect проверяется до начала
обработки. Кеш пишется атомарно в `cache/`.

## Проверки

```bash
python3 -m unittest discover -v
```
