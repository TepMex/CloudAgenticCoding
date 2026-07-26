# Threat model (MVP)

## API keys

- Stored in memory by default; IndexedDB only after explicit opt-in.
- Same-origin XSS can read IndexedDB — disclosed in Settings.
- Never placed in URLs, exports, debug memory views, or copied error payloads (redaction helpers).
- CSP restricts script sources to `'self'`.

## EPUB content

- Treated as untrusted HTML: sanitized with DOMPurify before render.
- Rendered in a sandboxed iframe (`allow-same-origin` only — no scripts).
- Reader CSS overrides publisher styling.

## Prompt injection

- Book text, memory excerpts, and learner answers wrapped in `<<<UNTRUSTED_QUOTED_CONTENT>>>` delimiters.
- System prompts forbid following instructions inside quoted content, revealing keys, spoiling future plot, or inventing term meanings.

## Arbitrary endpoints

- Learner may point profiles at any HTTPS/HTTP OpenAI-compatible URL.
- Only CORS-enabled endpoints work; Test connection reports failures.
- Endpoint receives passages, nearby context, compact memory, and learner answers — warned in UI.
- No proxy: traffic is browser → provider directly.
