import { useEffect, useMemo, useRef, useState } from "react";
import { db } from "../db/database";
import type { BookRecord, ChapterRecord, ProviderProfile } from "../shared/domain";
import { getBook, getChapters, touchBookOpened } from "../books/repository";
import { createIframeRenderer, type RendererHandle } from "../reader/epub-adapter/parse";
import { buildExpandedSelection } from "../reader/selection/sentences";
import { recoverRangeFromLocation } from "../reader/locations/location";
import { useUiStore } from "../app/ui-store";
import { leaveChapterCleanup, getMemoryProfile } from "../memory/service";
import { SelectionToolbar } from "../ui/components/SelectionToolbar";
import { AssistancePanel } from "../ui/panels/AssistancePanel";
import { createId } from "../shared/id";

export function ReaderView() {
  const bookId = useUiStore((s) => s.activeBookId);
  const spineItemId = useUiStore((s) => s.activeSpineItemId);
  const setSpine = useUiStore((s) => s.setSpine);
  const setView = useUiStore((s) => s.setView);
  const appearance = useUiStore((s) => s.appearance);
  const setSelection = useUiStore((s) => s.setSelection);
  const setToolbar = useUiStore((s) => s.setToolbar);
  const upsertCard = useUiStore((s) => s.upsertCard);
  const updateCard = useUiStore((s) => s.updateCard);
  const offline = useUiStore((s) => s.offline);
  const cards = useUiStore((s) => s.cards);

  const [book, setBook] = useState<BookRecord | null>(null);
  const [chapters, setChapters] = useState<ChapterRecord[]>([]);
  const [settings, setSettings] = useState({
    fontSizePx: 20,
    lineHeight: 1.75,
    contentWidthCh: 42,
  });
  const hostRef = useRef<HTMLDivElement>(null);
  const rendererRef = useRef<RendererHandle | null>(null);
  const prefetchRef = useRef<Map<string, ChapterRecord>>(new Map());
  const scrollSaveTimer = useRef<number | null>(null);

  const chapter = useMemo(
    () => chapters.find((c) => c.spineItemId === spineItemId) ?? null,
    [chapters, spineItemId],
  );
  const chapterIndex = chapters.findIndex((c) => c.spineItemId === spineItemId);

  useEffect(() => {
    if (!bookId) return;
    void (async () => {
      const b = await getBook(bookId);
      const ch = await getChapters(bookId);
      const s = await db.settings.get("app");
      if (s) {
        setSettings({
          fontSizePx: s.fontSizePx,
          lineHeight: s.lineHeight,
          contentWidthCh: s.contentWidthCh,
        });
      }
      setBook(b ?? null);
      setChapters(ch);
      await touchBookOpened(bookId);
    })();
  }, [bookId]);

  // Prefetch adjacent chapters (not into DOM)
  useEffect(() => {
    if (chapterIndex < 0) return;
    for (const offset of [-1, 1]) {
      const c = chapters[chapterIndex + offset];
      if (c) prefetchRef.current.set(c.spineItemId, c);
    }
  }, [chapterIndex, chapters]);

  useEffect(() => {
    if (!hostRef.current || !chapter) return;
    if (!rendererRef.current) {
      rendererRef.current = createIframeRenderer(hostRef.current);
    }
    rendererRef.current.setChapterHtml(chapter.html, {
      ...settings,
      appearance,
    });

    // Restore scroll
    void (async () => {
      if (!bookId) return;
      const pos = await db.readingPositions.get(bookId);
      const doc = rendererRef.current?.getDocument();
      const win = rendererRef.current?.iframe.contentWindow;
      if (pos && pos.spineItemId === chapter.spineItemId && win && doc) {
        requestAnimationFrame(() => {
          const max = doc.documentElement.scrollHeight - win.innerHeight;
          win.scrollTo(0, Math.max(0, max * pos.scrollRatio));
        });
      }
    })();

    // Annotations markers
    void (async () => {
      if (!bookId) return;
      const anns = await db.annotations.where("bookId").equals(bookId).toArray();
      const doc = rendererRef.current?.getDocument();
      if (!doc?.body) return;
      for (const ann of anns.filter((a) => a.location.spineItemId === chapter.spineItemId)) {
        const recovered = recoverRangeFromLocation(chapter.plainText, ann.location);
        if (recovered.method === "failed") continue;
        // Soft visual: we mark via data attributes on text walk — simplified: margin list in panel
        void recovered;
      }
    })();

    const doc = rendererRef.current.getDocument();
    const win = rendererRef.current.iframe.contentWindow;
    if (!doc || !win) return;

    const onScroll = () => {
      if (!bookId) return;
      if (scrollSaveTimer.current) window.clearTimeout(scrollSaveTimer.current);
      scrollSaveTimer.current = window.setTimeout(() => {
        const max = doc.documentElement.scrollHeight - win.innerHeight;
        const ratio = max > 0 ? win.scrollY / max : 0;
        void db.readingPositions.put({
          bookId,
          spineItemId: chapter.spineItemId,
          scrollRatio: ratio,
          updatedAt: Date.now(),
        });

        // Collapse cards whose passage is out of view (heuristic via quote presence)
        for (const card of useUiStore.getState().cards) {
          // Keep simple: if user scrolled significantly, mark collapsed optional
          updateCard(card.id, {
            passageInView: true,
          });
        }
      }, 200);
    };

    const onMouseUp = () => {
      const sel = doc.getSelection();
      if (!sel || sel.isCollapsed || !sel.rangeCount) {
        setToolbar(false);
        return;
      }
      const selected = sel.toString();
      if (!selected.trim()) {
        setToolbar(false);
        return;
      }

      // Map selection to plainText offsets via quote search
      const quote = selected;
      const start = chapter.plainText.indexOf(quote.replace(/\s+/g, ""));
      // Fallback: loose match
      let manualStart = chapter.plainText.indexOf(quote);
      let manualEnd = manualStart + quote.length;
      if (manualStart < 0) {
        const compact = quote.replace(/\s+/g, "");
        const compactChapter = chapter.plainText.replace(/\s+/g, "");
        const ci = compactChapter.indexOf(compact);
        if (ci >= 0) {
          // approximate back to original — use first occurrence of first 8 chars
          const tip = quote.trim().slice(0, 8);
          manualStart = chapter.plainText.indexOf(tip);
          manualEnd = manualStart >= 0 ? manualStart + tip.length : 0;
          if (manualStart < 0) {
            manualStart = 0;
            manualEnd = Math.min(quote.length, chapter.plainText.length);
          } else {
            manualEnd = manualStart + quote.length;
          }
        } else {
          manualStart = Math.max(0, start);
          manualEnd = manualStart + quote.length;
        }
      }

      const expanded = buildExpandedSelection(chapter.plainText, manualStart, manualEnd);
      setSelection(expanded, chapter.plainText);

      const range = sel.getRangeAt(0);
      const rect = range.getBoundingClientRect();
      const hostRect = hostRef.current!.getBoundingClientRect();
      setToolbar(true, hostRect.left + rect.left + rect.width / 2, hostRect.top + rect.top - 8);

      // Apply dual highlight in iframe
      try {
        doc.querySelectorAll(".mkp-hl-manual, .mkp-hl-expanded").forEach((n) => {
          const parent = n.parentNode;
          if (!parent) return;
          while (n.firstChild) parent.insertBefore(n.firstChild, n);
          parent.removeChild(n);
        });
        // Highlight manual selection strongly
        const mark = doc.createElement("mark");
        mark.className = "mkp-hl-manual";
        range.surroundContents(mark);
      } catch {
        // surroundContents can fail on partial nodes — leave native selection
      }
    };

    win.addEventListener("scroll", onScroll, { passive: true });
    doc.addEventListener("mouseup", onMouseUp);
    doc.addEventListener("touchend", onMouseUp);

    return () => {
      win.removeEventListener("scroll", onScroll);
      doc.removeEventListener("mouseup", onMouseUp);
      doc.removeEventListener("touchend", onMouseUp);
    };
  }, [chapter, settings, appearance, bookId, setSelection, setToolbar, updateCard]);

  useEffect(() => {
    return () => {
      rendererRef.current?.destroy();
      rendererRef.current = null;
    };
  }, []);

  const goChapter = async (nextIndex: number) => {
    if (!bookId || !chapter) return;
    const next = chapters[nextIndex];
    if (!next) return;
    const profile = await getMemoryProfile();
    await leaveChapterCleanup({
      bookId,
      leavingChapterId: chapter.spineItemId,
      chapterTitle: chapter.title,
      profile,
    });
    setSpine(next.spineItemId);
    await db.readingPositions.put({
      bookId,
      spineItemId: next.spineItemId,
      scrollRatio: 0,
      updatedAt: Date.now(),
    });
  };

  if (!bookId || !book || !chapter) {
    return (
      <div className="page">
        <p>No book open.</p>
        <button type="button" onClick={() => setView("library")}>
          Back to library
        </button>
      </div>
    );
  }

  return (
    <div className={`reader-layout appearance-${appearance}`}>
      <header className="reader-topbar">
        <button type="button" className="ghost" onClick={() => setView("library")}>
          Library
        </button>
        <div className="reader-titleblock">
          <p className="brand-inline">看书朋友</p>
          <h1>{book.title}</h1>
          <p className="chapter-label">{chapter.title}</p>
        </div>
        <div className="reader-actions">
          {offline && <span className="offline-pill">Offline</span>}
          <button type="button" className="ghost" onClick={() => setView("memory")}>
            Memory
          </button>
          <button type="button" className="ghost" onClick={() => setView("settings")}>
            Settings
          </button>
        </div>
      </header>

      <div className="reader-body">
        <div className="reader-main">
          <div className="chapter-nav">
            <button
              type="button"
              disabled={chapterIndex <= 0}
              onClick={() => void goChapter(chapterIndex - 1)}
            >
              Previous
            </button>
            <span>
              {chapterIndex + 1} / {chapters.length}
            </span>
            <button
              type="button"
              disabled={chapterIndex >= chapters.length - 1}
              onClick={() => void goChapter(chapterIndex + 1)}
            >
              Next
            </button>
          </div>
          <div ref={hostRef} className="reader-host" />
          <SelectionToolbar
            bookId={bookId}
            chapter={chapter}
            onNeedProfile={async (task) => {
              const assignments = await db.taskModelAssignments.get("default");
              const id =
                task === "explain"
                  ? assignments?.explainProfileId
                  : task === "assess"
                    ? assignments?.assessProfileId
                    : assignments?.explainProfileId;
              if (!id) return null;
              return (await db.providerProfiles.get(id)) as ProviderProfile | null;
            }}
          />
        </div>
        <AssistancePanel bookId={bookId} chapter={chapter} />
      </div>

      {/* collapsed markers */}
      <div className="collapsed-markers" aria-label="Collapsed assistance">
        {cards
          .filter((c) => c.collapsed)
          .map((c) => (
            <button
              key={c.id}
              type="button"
              className="marker-chip"
              onClick={() =>
                upsertCard({ ...c, collapsed: false, id: c.id || createId("card") })
              }
            >
              {c.title}
            </button>
          ))}
      </div>
    </div>
  );
}
