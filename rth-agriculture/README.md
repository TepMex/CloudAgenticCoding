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

