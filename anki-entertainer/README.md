# Anki Entertainer

Android companion for [AnkiDroid](https://github.com/ankidroid/Anki-Android): open from a card with a custom URI and get short LLM-generated text chunks around the vocabulary under review.

See [SPEC.md](./SPEC.md) for the full specification.

## Deep link

```
ankientapi://x-callback-url?q={VOCAB}
```

Example Anki card field:

```html
<a href="ankientapi://x-callback-url?q={{Front}}">Entertain me</a>
```

## Build

```bash
cd anki-entertainer
./gradlew assembleRelease
```

APK: `app/build/outputs/apk/release/app-release.apk`

## Settings

- OpenAI-compatible API base URL and bearer token (BYOK)
- Model names (one per line; each new chunk uses a random model)
- Chunk generation prompt with `{QUERY}` placeholder
- Target chunk count (liked saved chunks do not reduce how many new chunks are generated)
