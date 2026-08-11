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

The Android APK build sets `VITE_REMOTE_ASSET_BASE` to the GitHub raw `public/` tree so map/battle PNGs are fetched after launch instead of being packed into the APK (GitHub rejects blobs over 100 MB). The browser/Pages build leaves that unset and serves the same PNGs from `public/` as usual.

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

- один масштабируемый мир из 15 садовых культур и 220 игровых участков;
- каждый из 110 уникальных списков RSH разделён на две последовательные половины без перестановки кадров;
- логическая сетка 15×15; первые пять участков занимают по две ячейки и дают более заметное раннее восстановление;
- управление RTS-картой: pan мышью/touch, cursor-centered wheel zoom, pinch zoom, возврат из боя к той же камере;
- 2974 иероглифа без книжных мнемонических историй в production-данных;
- локальные stroke-данные для всех 2974 знаков;
- Hanzi Writer в режиме quiz: порядок, направление и положение штриха;
- подсказка только для следующего штриха после ошибок или по запросу;
- урон сорняку за каждый принятый штрих и уничтожение после полного знака;
- FSRS через `ts-fsrs`, оценки `Again`/`Good` независимо от анимации боя;
- здоровье участка по взвешенной сумме штрихов due/new карточек и blending полной clean/negative карты;
- постоянные открытия соседних участков и изменяемое состояние зарастания;
- IndexedDB-сохранение через Dexie с без потерь миграцией v1 полей в две v2-половины;
- отдельная статистика: плотная стена всех иероглифов и цветовая проекция SRS-стадий;
- мышь, touch и pen Pointer Events через Hanzi Writer;
- адаптивные desktop/mobile интерфейсы.

Сгенерированные проектные фоны находятся в `public/assets/`. Исходные референсы сохранены в `concept-art/`.
