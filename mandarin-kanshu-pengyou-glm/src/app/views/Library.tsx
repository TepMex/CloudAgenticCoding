import { useState, useEffect } from "react";
import { listBooks, importEpub } from "../../books/import";
import { deleteBook } from "../../db/database";
import type { LocalBook } from "../../shared/domain";

export function Library({ onOpen }: { onOpen: (id: string) => void }) {
  const [books, setBooks] = useState<LocalBook[]>([]);
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState<string | null>(null);

  useEffect(() => { listBooks().then(setBooks); }, []);

  async function onFiles(files: FileList | null) {
    if (!files || files.length === 0) return;
    setBusy(true); setErr(null);
    try {
      for (const f of Array.from(files)) {
        if (!/\.epub$/i.test(f.name)) continue;
        await importEpub(f);
      }
      setBooks(await listBooks());
    } catch (e) {
      setErr(e instanceof Error ? e.message : String(e));
    } finally { setBusy(false); }
  }

  async function onDelete(id: string) {
    if (!confirm("Delete this book and all related local data?")) return;
    await deleteBook(id);
    setBooks(await listBooks());
  }

  return (
    <div className="content">
      <h1>Library</h1>
      <p className="muted">Local-first. Your books stay on this device. Select a DRM-free EPUB to import.</p>
      <div className="row" style={{ margin: "16px 0" }}>
        <label className="primary" style={{ display: "inline-block", cursor: "pointer", background: "var(--moss)", color: "#fff", borderRadius: 6 }}>
          <span style={{ padding: "7px 12px", display: "inline-block" }}>+ Import EPUB</span>
          <input type="file" accept=".epub,application/epub+zip" multiple style={{ display: "none" }} onChange={(e) => onFiles(e.target.files)} />
        </label>
        {busy && <span className="loading" aria-label="Loading" />}
      </div>
      {err && <div className="error">{err}</div>}
      {books.length === 0 ? (
        <p className="muted">No books yet.</p>
      ) : (
        <div className="book-list">
          {books.map((b) => (
            <div key={b.id} className="card book-card" onClick={() => onOpen(b.id)}>
              {b.coverDataUrl ? <img src={b.coverDataUrl} alt="" /> : <div style={{ height: 160, background: "var(--paper-2)", borderRadius: 6, display: "flex", alignItems: "center", justifyContent: "center", fontSize: 36 }}>📖</div>}
              <div className="title">{b.title}</div>
              <div className="meta">{b.author || "—"}</div>
              <div className="row" style={{ marginTop: 4 }}>
                <button onClick={(e) => { e.stopPropagation(); onOpen(b.id); }}>Open</button>
                <button className="danger" onClick={(e) => { e.stopPropagation(); onDelete(b.id); }}>Delete</button>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
