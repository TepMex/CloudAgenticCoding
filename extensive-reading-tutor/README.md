# Читаю · extensive-reading-tutor

Local-first tutor for extensive reading in Chinese. The interface follows a phone layout: library, book progress, a four-phase lesson (text, questions, your answers, review), dictionary cards, notes, learning memory, profile, and settings.

There is no backend. Books, notes, and the API key stay in `localStorage`. Lesson generation, grading, and word lookup call an **OpenAI-compatible** Chat Completions endpoint directly from the browser.

## Requirements

- [Bun](https://bun.sh) 1.1+

## Commands

```bash
bun install
bun dev
bun test
bun run check
bun run build
```

## First run

The sample shelf (天官赐福, 三体, 围城, 活着) works offline, including a full lesson on the first book.

To use a model:

1. Profile → Изменить.
2. Set the base URL (`https://api.openai.com`, `https://host/v1`, or a full `…/chat/completions` URL), the API key, and the model id (`gpt-4o` or whatever the provider calls it).
3. Проверить соединение.
4. Add a Chinese passage with «+». The app asks the model for a lesson. If the call fails, a local question template still opens.

Requests are `POST {base}/v1/chat/completions` with `Authorization: Bearer …`. A trailing `/v1` is not doubled. The provider must allow browser CORS. The key is stored only in this browser.

## Docs

`SPEC.md` — product spec.

## Production build

```bash
bun run build
```

Output is `dist/`. GitHub Pages sets `GH_PAGES_PUBLIC_PATH` (for example `/CloudAgenticCoding/extensive-reading-tutor/`).
