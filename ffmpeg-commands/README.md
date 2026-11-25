# Команды FFmpeg

Чат: вы описываете задачу, приложение возвращает ранжированные команды из документации FFmpeg. Ранжирование — косинусное сходство эмбеддингов EmbeddingGemma 2 (текстовая модель 270M) через LiteRT.js (`@litert-lm/core`).

Модель не входит в приложение. Браузер скачивает `embeddinggemma-2-text-270m.litertlm` с Hugging Face. В репозитории лежат только текстовые фрагменты документации и заранее посчитанная матрица `data/embeddings.f32`.

## Запуск

```bash
bun install
bun dev
```

Откройте адрес, который напечатает сервер (порт 4731, если он свободен). Нужен браузер с WebGPU; если GPU-бэкенд не стартует, приложение пробует CPU.

Опубликованная страница: [tepmex.github.io/CloudAgenticCoding/ffmpeg-commands](https://tepmex.github.io/CloudAgenticCoding/ffmpeg-commands/). Сборка для GitHub Pages:

```bash
bun run build
```

`dist/` содержит страницу, корпус, матрицу эмбеддингов и wasm LiteRT. Веса модели туда не попадают: их по-прежнему скачивает браузер. В CI путь задаёт `GH_PAGES_PUBLIC_PATH`.

## Корпус

```bash
bun run corpus          # скачать HTML с ffmpeg.org и собрать data/corpus.json
python3 scripts/embed_corpus.py
```

Перед `embed_corpus.py` положите модель в `data/model/embeddinggemma-2-text-270m.litertlm`. Этот каталог в `.gitignore`.

Спецификация: `SPEC.md`.
