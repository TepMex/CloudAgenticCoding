import { useEffect, useState } from "react";
import { getBookMemory, memoryToDebugMarkdown } from "../../memory/service";
import type { BookMemory } from "../../shared/domain";

export function MemoryView({ bookId, onBack }: { bookId: string; onBack: () => void }) {
  const [memory, setMemory] = useState<BookMemory | null>(null);
  useEffect(() => { getBookMemory(bookId).then(setMemory); }, [bookId]);
  if (!memory) return <div className="content"><p>Loading…</p></div>;
  return (
    <div className="content">
      <div className="row" style={{ justifyContent: "space-between" }}>
        <h1>Book memory (debug)</h1>
        <button onClick={onBack}>← Back to reading</button>
      </div>
      <p className="muted">Structured canonical memory. Facts are distinguished from guesses. Read-only — generated from your reading interactions.</p>
      <pre style={{ whiteSpace: "pre-wrap", background: "var(--card)", padding: 16, borderRadius: 8, border: "1px solid var(--border)", fontFamily: "ui-monospace, monospace", fontSize: 13 }}>{memoryToDebugMarkdown(memory)}</pre>
    </div>
  );
}
