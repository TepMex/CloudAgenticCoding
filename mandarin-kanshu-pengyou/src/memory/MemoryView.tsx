import { useEffect, useState } from "react";
import { db } from "../db/database";
import { memoryToMarkdown, runInitialMemoryExtraction, getMemoryProfile } from "../memory/service";
import { useUiStore } from "../app/ui-store";
import { getChapters } from "../books/repository";
import { createId } from "../shared/id";
import { safeErrorMessage } from "../security/redact";

export function MemoryView() {
  const bookId = useUiStore((s) => s.activeBookId);
  const setView = useUiStore((s) => s.setView);
  const offline = useUiStore((s) => s.offline);
  const [md, setMd] = useState("");
  const [status, setStatus] = useState<string | null>(null);

  useEffect(() => {
    if (!bookId) return;
    void db.bookMemory.get(bookId).then((m) => {
      setMd(m ? memoryToMarkdown(m) : "_No memory yet_");
    });
  }, [bookId]);

  const runInitial = async () => {
    if (!bookId || offline) return;
    const profile = await getMemoryProfile();
    if (!profile) {
      setStatus("Assign a Memory profile in Settings first.");
      return;
    }
    const chapters = await getChapters(bookId);
    const sample = chapters
      .slice(0, 2)
      .map((c) => c.plainText.slice(0, 1200))
      .join("\n\n");
    setStatus("Running lightweight initial analysis…");
    try {
      const controller = new AbortController();
      useUiStore.getState().setActiveRequest({
        id: createId("req"),
        kind: "initial_memory",
        controller,
      });
      const memory = await runInitialMemoryExtraction({
        bookId,
        sampleText: sample,
        location: {
          bookId,
          spineItemId: chapters[0]?.spineItemId ?? "unknown",
          textQuote: sample.slice(0, 40),
          prefix: "",
          suffix: "",
        },
        profile,
        signal: controller.signal,
      });
      setMd(memoryToMarkdown(memory));
      setStatus("Initial analysis saved.");
    } catch (e) {
      setStatus(safeErrorMessage(e));
    } finally {
      useUiStore.getState().setActiveRequest(null);
    }
  };

  return (
    <div className="page memory-page">
      <header className="page-header">
        <div>
          <p className="brand">看书朋友</p>
          <h1>Book memory</h1>
          <p className="lede">Readable debug view — facts only, not companion speculation.</p>
        </div>
        <button type="button" className="ghost" onClick={() => setView("reader")}>
          Back to reader
        </button>
      </header>
      <div className="card-actions">
        <button type="button" disabled={offline || !bookId} onClick={() => void runInitial()}>
          Lightweight initial analysis
        </button>
        <button
          type="button"
          className="ghost"
          disabled={offline || !bookId}
          onClick={() => {
            if (
              window.confirm(
                "Whole-chapter analysis may be slower and more expensive. Continue?",
              )
            ) {
              void runInitial();
            }
          }}
        >
          Whole-chapter analysis (costlier)
        </button>
      </div>
      {status && <p className="status-line">{status}</p>}
      <pre className="memory-md">{md}</pre>
    </div>
  );
}
