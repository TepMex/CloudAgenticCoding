# zametki-kitai — Specification

## Purpose

A private reading page for one person’s travel notes from China in September 2026. The notes come from the HTML export `Заметки_Китай26.zip`. The page is for the author (and anyone they tell the passphrase to). It is a casual gate against someone who opens the link, not a security product.

## Requirements

1. Show every day from the export, in calendar order, with the original paragraphs and photos in their original order.
2. Store note text and photos only as a passphrase-encrypted blob (`public/journal.bin`). The HTML export is not kept in the repository.
3. Derive an AES-256-GCM key from the passphrase with PBKDF2-HMAC-SHA256 and decrypt in the browser. No server, no account.
4. Before a correct passphrase, the page shows a passphrase field and no note text or photos.
5. A wrong passphrase shows an error and does not reveal the journal.
6. After a correct passphrase, the reader can move between days and read the whole trip on one page.
7. Closing the journal drops the decrypted text from the page.

## Interfaces

### Page

- GitHub Pages path: `/<repository>/zametki-kitai/`
- Local dev: `bun dev` in `zametki-kitai/`
- UI language: Russian

### Sealed file `public/journal.bin`

| Offset | Length | Field |
| --- | --- | --- |
| 0 | 4 | ASCII magic `ZN26` |
| 4 | 1 | version `1` |
| 5 | 4 | PBKDF2 iteration count, uint32 big-endian |
| 9 | 16 | salt |
| 25 | 12 | AES-GCM IV |
| 37 | rest | ciphertext, then the 16-byte GCM tag |

Plaintext inside the ciphertext:

| Offset | Length | Field |
| --- | --- | --- |
| 0 | 4 | JSON byte length, uint32 big-endian |
| 4 | N | UTF-8 JSON journal |
| 4+N | rest | JPEG bytes, concatenated |

A photo block in the JSON is `{ "t": "img", "o": <offset into the JPEG section>, "n": <length> }`. A paragraph is `{ "t": "p", "text": "..." }`.

KDF parameters stored with the file: PBKDF2-HMAC-SHA256, 100000 iterations, 32-byte key. The passphrase is not written into the repository or the page.

### Regenerating the blob

```text
NOTES_PASSWORD='…' bun scripts/seal-notes.ts <notes-dir> public/journal.bin
```

`<notes-dir>` is the folder of HTML files from the export. Photos are scaled to at most 1400px wide and stored as JPEG. `ffmpeg` is required for that step only.

## Data model

- **Journal** — title, kicker, date range, ordered days.
- **Day** — ISO date, navigation label, weekday heading, place line taken from the note title, ordered blocks.
- **Block** — paragraph or photo.
- There is no account, database, or editable state. The passphrase lives only in memory while the journal is open.

## UI / UX

1. **Gate.** Title «Путевые заметки», a password field, and «Открыть». Wrong passphrase: «Неверный пароль».
2. **Journal.** Masthead with the trip title and date range, a day index, and a single reading column. Choosing a day scrolls to it. The current day stays marked while scrolling.
3. **Close.** «Закрыть» returns to the gate and discards the decrypted journal.
4. The day index is a sticky column on a wide screen and a horizontal strip on a narrow screen.

## Out of scope

- Protection against someone willing to guess a short passphrase or read the cipher source.
- Editing notes, accounts, sync, comments, or an Android build.
- Publishing the original HTML export or uncompressed photos.

## Acceptance criteria

1. `public/journal.bin` begins with `ZN26` and does not contain the raw HTML export (`<html`, `data:image`, `&nbsp;`).
2. Decrypting the blob yields 25 days (5–29 September 2026) and 19 photos, with paragraphs matching the export.
3. `bun test` in `zametki-kitai` passes, including a cipher round-trip and a wrong-passphrase failure.
4. `bun run build` writes `dist/index.html`.
5. In the browser, a wrong passphrase shows the error and no note text. The author’s passphrase shows the day list, a day’s prose, and a photo. «Закрыть» returns to the gate.
6. The zip archive is not in the repository. The app is listed in `scripts/ci-apps.sh` and the root `README.md`.
