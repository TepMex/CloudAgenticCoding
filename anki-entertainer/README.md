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

- Ordered OpenAI-compatible LLM providers (BYOK): each has its own base URL, bearer token, and model list
- Providers are tried in order; if one does not respond, the next is used
- Model names (one per line per provider; each new chunk picks a random model from the provider that responds)
- Chunk generation prompt with offline placeholders (`{QUERY}`, `{OPPOSITE}`, `{SIMPL_HISTORY}`, `{MNEMO_EXAMPLES}`, `{SEMANTIC}`, `{PHONETIC}`) and prompt preview
- Target chunk count (liked saved chunks do not reduce how many new chunks are generated)

## Offline Hanzi metadata

Prompt placeholders (except `{QUERY}`) are filled from a prepackaged SQLite database. Ordinary Gradle builds do not download source data.

When the LLM is not configured or unreachable (no chunks generated yet), the main screen falls back to **up to 5 local mnemonic stories or source-backed structure cues** for Han compounds and characters in the vocabulary. Project-authored stories rank ahead of cues derived from the bundled Make Me a Hanzi fields.

```bash
python3 tools/hanzi-data/build.py
```

Details: [docs/HANZI_DATA.md](./docs/HANZI_DATA.md), notices: [THIRD_PARTY_NOTICES.md](./THIRD_PARTY_NOTICES.md).
