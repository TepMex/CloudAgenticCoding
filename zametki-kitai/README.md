# Путевые заметки

Reading page for the September 2026 China trip. The prose and photos are stored in `public/journal.bin` and decrypted in the browser with a passphrase. The page is a casual gate for people who open the link, not a security product.

```bash
bun install
bun test
bun dev
bun run build
```

GitHub Pages publishes the app at `/zametki-kitai/`.

To seal a fresh HTML export (one `.html` file per day, photos embedded as data URLs):

```bash
NOTES_PASSWORD='…' bun scripts/seal-notes.ts /path/to/notes public/journal.bin
```

That step needs `ffmpeg`. The passphrase is not written into the repository.
