# Команды FFmpeg

Чат: вы описываете задачу, приложение возвращает ранжированные команды из документации FFmpeg. Ранжирование — косинусное сходство эмбеддингов EmbeddingGemma 2 (текстовая модель 270M) через LiteRT.js (`@litert-lm/core`).

Модель не входит в приложение. Браузер скачивает `embeddinggemma-2-text-270m.litertlm` с Hugging Face. В репозитории лежат только текстовые фрагменты документации и заранее посчитанная матрица `data/embeddings.f32`.

## Запуск

```bash
bun install
bun dev
```

Откройте адрес, который напечатает сервер (порт 4731, если он свободен). Нужен браузер с WebGPU; если GPU-бэкенд не стартует, приложение пробует CPU.

## Корпус

```bash
bun run corpus          # скачать HTML с ffmpeg.org и собрать data/corpus.json
python3 scripts/embed_corpus.py
```

Перед `embed_corpus.py` положите модель в `data/model/embeddinggemma-2-text-270m.litertlm`. Этот каталог в `.gitignore`.

Спецификация: `SPEC.md`.
