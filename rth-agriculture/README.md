# Сад памяти

Игровой MVP по `GAME_SPEC.md` и `GAME_CONCEPT.md`: значение → самостоятельное написание иероглифа → каждый правильный штрих повреждает сорняк → завершённый знак обновляет FSRS и здоровье поля.

## Запуск

```bash
bun install
bun run dev
```

Production-сборка:

```bash
bun run build
```

## GitHub Pages (this monorepo)

The root workflow [`.github/workflows/deploy.yml`](../.github/workflows/deploy.yml) builds this app and copies `dist/` to `deploy/rth-agriculture/` on the `gh-pages` branch. After a push to `master`, the game is available at:

`https://<user-or-org>.github.io/<repository>/rth-agriculture/`

CI sets `GH_PAGES_PUBLIC_PATH` so Vite emits correct asset URLs under that prefix. For local/`file://` (and the Android wrapper), unset that variable so `base` stays `./`.

Background images are assigned via absolute URLs from `document.baseURI` (`src/assetUrl.ts`) so CSS `url(var(--bg-*))` does not re-resolve `./assets/...` against the hashed stylesheet path (which would 404 as `assets/assets/...` in the Android WebView).

Hanzi stroke JSON is loaded with **XHR** (`src/hanziData.ts`), not `fetch` — Chromium/Android WebView reject Fetch against `file://`, which left quiz/hint dead in the APK.

## Tests

```bash
bun test
bun run test:battle-background
# After `vite build --outDir /tmp/rth-www` + static server on :8765:
bun run test:battle-canvas
# file:// input (Android-shaped):
bun run test:battle-input
```

## Что реализовано

- непрерывная карта из 110 полей по 110 уникальным спискам RSH;
- 2974 иероглифа без книжных мнемонических историй в production-данных;
- локальные stroke-данные для всех 2974 знаков;
- Hanzi Writer в режиме quiz: порядок, направление и положение штриха;
- подсказка только для следующего штриха после ошибок или по запросу;
- урон сорняку за каждый принятый штрих и уничтожение после полного знака;
- FSRS через `ts-fsrs`, оценки `Again`/`Good` независимо от анимации боя;
- здоровье поля по взвешенной сумме штрихов due/new карточек;
- постоянные открытия полей и изменяемое состояние зарастания;
- IndexedDB-сохранение через Dexie с версией save schema;
- мышь, touch и pen Pointer Events через Hanzi Writer;
- адаптивные desktop/mobile интерфейсы.

Сгенерированные проектные фоны находятся в `public/assets/`. Исходные референсы сохранены в `concept-art/`.

