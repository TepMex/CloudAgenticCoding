import { useEffect, useState } from "react";
import { deleteBookAndData, importEpubFile, listBooks } from "../books/repository";
import type { BookRecord } from "../shared/domain";
import { db } from "../db/database";
import { useUiStore } from "../app/ui-store";
import { createId, now } from "../shared/id";

export function LibraryView() {
  const [books, setBooks] = useState<BookRecord[]>([]);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const openBook = useUiStore((s) => s.openBook);
  const setView = useUiStore((s) => s.setView);

  const refresh = async () => setBooks(await listBooks());

  useEffect(() => {
    void refresh();
  }, []);

  const onImport = async (file: File | null) => {
    if (!file) return;
    setBusy(true);
    setError(null);
    try {
      const book = await importEpubFile(file, file.name);
      await refresh();
      const first = book.spineItemIds[0];
      if (first) openBook(book.id, first);
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
    } finally {
      setBusy(false);
    }
  };

  const onOpen = async (book: BookRecord) => {
    const pos = await db.readingPositions.get(book.id);
    const spine = pos?.spineItemId ?? book.spineItemIds[0];
    if (!spine) return;
    await db.readingSessions.put({
      id: createId("sess"),
      bookId: book.id,
      startedAt: now(),
      endedAt: null,
    });
    openBook(book.id, spine);
  };

  const onDelete = async (book: BookRecord) => {
    const ok = window.confirm(
      `Delete “${book.title}” and all related local data (annotations, assessments, memory)?`,
    );
    if (!ok) return;
    await deleteBookAndData(book.id);
    await refresh();
  };

  return (
    <div className="page library-page">
      <header className="page-header">
        <div>
          <p className="brand">看书朋友</p>
          <h1>Library</h1>
          <p className="lede">Local EPUB companions for steady Chinese reading.</p>
        </div>
        <nav className="header-nav">
          <button type="button" className="ghost" onClick={() => setView("stats")}>
            Stats
          </button>
          <button type="button" className="ghost" onClick={() => setView("settings")}>
            Settings
          </button>
        </nav>
      </header>

      <label className="import-card">
        <span>{busy ? "Importing…" : "Import DRM-free EPUB"}</span>
        <input
          type="file"
          accept=".epub,application/epub+zip"
          disabled={busy}
          onChange={(e) => void onImport(e.target.files?.[0] ?? null)}
        />
      </label>

      {error && <p className="error-text" role="alert">{error}</p>}

      <ul className="book-list">
        {books.map((b) => (
          <li key={b.id}>
            <button type="button" className="book-row" onClick={() => void onOpen(b)}>
              <span className="book-title">{b.title}</span>
              <span className="book-meta">{b.author}</span>
            </button>
            <button
              type="button"
              className="ghost danger"
              aria-label={`Delete ${b.title}`}
              onClick={() => void onDelete(b)}
            >
              Delete
            </button>
          </li>
        ))}
        {!books.length && (
          <li className="empty-hint">No books yet. Import a DRM-free EPUB to begin.</li>
        )}
      </ul>
    </div>
  );
}
