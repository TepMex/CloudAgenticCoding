import { useEffect, useRef, useState, useCallback } from "react";
import { useAppStore } from "../store";
import { openAdapterForBook } from "../../books/import";
import { db, clearTransientChapterCache } from "../../db/database";
import type { EpubRendererAdapter, RenderedChapter } from "../../reader/epub-adapter";
import type { ChapterRecord, ReaderLocation } from "../../shared/domain";
import { expandSelection, gatherContext } from "../../reader/selection/expand";
import { runExplain, freshExplain } from "../../explain/service";
import { runAssess, finishAttempt } from "../../assessment/service";
import { getBookMemory, memoryToCompactString, runMemoryPatch, shouldUpdateMemoryImmediately, summarizeChapter } from "../../memory/service";
import { runCompanion } from "../../companion/service";
import { SelectionToolbar } from "./SelectionToolbar";
import { AssistancePanel } from "./AssistancePanel";
import type { ActiveCard } from "./cardTypes";

export function ReaderView({ bookId }: { bookId: string }) {
  const { settings, setSettings, online } = useAppStore();
  const settingsRef = useRef(settings);
  settingsRef.current = settings;
  const [adapter, setAdapter] = useState<EpubRendererAdapter | null>(null);
  const [chapters, setChapters] = useState<ChapterRecord[]>([]);
  const [currentChapter, setCurrentChapter] = useState<ChapterRecord | null>(null);
  const [rendered, setRendered] = useState<RenderedChapter | null>(null);
  const [paragraphs, setParagraphs] = useState<string[]>([]);
  const [cards, setCards] = useState<ActiveCard[]>([]);
  const [toolbar, setToolbar] = useState<{ x: number; y: number; manualText: string; range: Range } | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const hostRef = useRef<HTMLDivElement>(null);
  const chapterRef = useRef<ChapterRecord | null>(null);
  chapterRef.current = currentChapter;

  useEffect(() => { document.documentElement.classList.toggle("dark", settings.theme === "dark"); }, [settings.theme]);

  useEffect(() => {
    const iframe = rendered?.iframe;
    if (iframe?.contentDocument) {
      const doc = iframe.contentDocument;
      doc.documentElement.style.setProperty("--reader-font-size", settings.fontSize + "px");
      doc.documentElement.style.setProperty("--reader-line-height", String(settings.lineHeight));
      doc.documentElement.style.setProperty("--reader-content-width", settings.contentWidth + "px");
      doc.documentElement.style.setProperty("--reader-fg", settings.theme === "dark" ? "#f0e9d8" : "#2c2418");
    }
  }, [settings, rendered]);

  const onLeaveChapter = useCallback(async (oldChapter: ChapterRecord) => {
    const spineId = oldChapter.id.split(":")[1];
    await clearTransientChapterCache(bookId, spineId);
    await summarizeChapter(bookId, spineId, oldChapter.label);
  }, [bookId]);

  const renderChapterInner = useCallback(async (ad: EpubRendererAdapter, chapter: ChapterRecord) => {
    const prev = chapterRef.current;
    if (prev && prev.id !== chapter.id) await onLeaveChapter(prev);
    setLoading(true);
    try {
      const spineId = chapter.id.split(":")[1];
      const r = await ad.renderChapter(spineId, hostRef.current!);
      setRendered(r);
      setParagraphs(r.paragraphs);
      setCurrentChapter(chapter);
      setToolbar(null);
      const adj = ad.adjacentChapters(spineId);
      if (adj.next) ad.chapterText(adj.next.id).catch(() => {});
      if (adj.prev) ad.chapterText(adj.prev.id).catch(() => {});
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
    } finally {
      setLoading(false);
    }
  }, [onLeaveChapter]);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      setLoading(true);
      try {
        const { adapter, chapters } = await openAdapterForBook(bookId);
        if (cancelled) return;
        setAdapter(adapter);
        setChapters(chapters);
        const pos = await db.readingPositions.get(bookId);
        const startChapter = chapters.find((c) => c.id.split(":")[1] === pos?.spineItemId) || chapters[0];
        if (startChapter) await renderChapterInner(adapter, startChapter);
      } catch (e) {
        setError(e instanceof Error ? e.message : String(e));
      } finally {
        if (!cancelled) setLoading(false);
      }
    })();
    return () => { cancelled = true; };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [bookId]);

  useEffect(() => {
    const iframe = rendered?.iframe;
    const doc = iframe?.contentDocument;
    if (!doc) return;
    const onSelect = () => {
      const sel = doc.getSelection();
      if (!sel || sel.isCollapsed || sel.rangeCount === 0) { clearHighlights(doc); setToolbar(null); return; }
      const range = sel.getRangeAt(0);
      const text = range.toString();
      if (!text.trim()) return;
      const rect = range.getBoundingClientRect();
      const hostRect = hostRef.current?.getBoundingClientRect();
      if (!hostRect) return;
      // Render visual highlights: faint for expanded passage, strong for manual selection.
      clearHighlights(doc);
      const expanded = buildPassage(text);
      if (expanded) {
        const norm = (s: string) => s.replace(/\s+/g, "");
        const fullNorm = norm(rendered?.text ?? "");
        const manualNorm = norm(text);
        const startOff = fullNorm.indexOf(manualNorm);
        // Best-effort: only highlight the manual range (precise), since mapping
        // expanded paragraph offsets back to DOM nodes is fragile.
        void startOff;
        surroundRange(doc, range, "mkp-highlight-strong");
      }
      setToolbar({ x: rect.left - hostRect.left + rect.width / 2, y: rect.top - hostRect.top - 12, manualText: text, range });
    };
    doc.addEventListener("selectionchange", onSelect);
    return () => doc.removeEventListener("selectionchange", onSelect);
  }, [rendered]);

  useEffect(() => {
    const pane = hostRef.current?.parentElement;
    if (!pane) return;
    let t: number | undefined;
    const onScroll = () => {
      window.clearTimeout(t);
      t = window.setTimeout(async () => {
        const ch = chapterRef.current;
        if (!ch || !rendered) return;
        await db.readingPositions.put({
          bookId, spineItemId: ch.id.split(":")[1], epubCfi: undefined,
          textQuote: rendered.text.slice(0, 200), prefix: "", suffix: "",
          approximateProgress: ch.index / Math.max(1, chapters.length), updatedAt: Date.now(),
        });
      }, 400);
    };
    pane.addEventListener("scroll", onScroll);
    return () => pane.removeEventListener("scroll", onScroll);
  }, [rendered, bookId, chapters.length]);

  function buildPassage(manualText: string) {
    if (!rendered) return null;
    const norm = (s: string) => s.replace(/\s+/g, "");
    const manualNorm = norm(manualText);
    const fullNorm = norm(rendered.text);
    const start = fullNorm.indexOf(manualNorm);
    if (start < 0) return null;
    let startPara = 0, startOff = 0, endPara = 0, endOff = 0;
    let found = 0;
    const normParas = paragraphs.map(norm);
    for (let i = 0; i < normParas.length; i++) {
      const pLen = normParas[i].length;
      if (start >= found && start < found + pLen) { startPara = i; startOff = start - found; }
      const end = start + manualNorm.length;
      if (end > found && end <= found + pLen) { endPara = i; endOff = end - found; break; }
      found += pLen;
    }
    return expandSelection(paragraphs, startPara, startOff, endPara, endOff, manualText);
  }

  function makeLocation(manualText: string): ReaderLocation | null {
    if (!currentChapter) return null;
    const spineId = currentChapter.id.split(":")[1];
    const idx = currentChapter.index;
    const text = rendered?.text || "";
    const norm = (s: string) => s.replace(/\s+/g, "");
    const manualNorm = norm(manualText);
    const offset = norm(text).indexOf(manualNorm);
    return {
      bookId, spineItemId: spineId,
      epubCfi: "epubcfi(/" + idx + "/x(text)" + (offset >= 0 ? offset : 0) + ")",
      textQuote: manualText.trim(),
      prefix: offset > 0 ? norm(text).slice(Math.max(0, offset - 60), offset) : "",
      suffix: offset >= 0 ? norm(text).slice(offset + manualNorm.length, offset + manualNorm.length + 60) : "",
      approximateProgress: idx / Math.max(1, chapters.length),
    };
  }

  async function handleExplain() {
    if (!toolbar || !currentChapter) return;
    const manualText = toolbar.manualText;
    const expanded = buildPassage(manualText);
    if (!expanded) return;
    const loc = makeLocation(manualText)!;
    const memory = await getBookMemory(bookId);
    const memStr = memoryToCompactString(memory);
    const ctx = gatherContext(paragraphs, 0, 0, 2, 2);
    const contextStr = [...ctx.before, ...ctx.after].join("\n\n");
    setToolbar(null);
    const tempId = crypto.randomUUID();
    setCards((c) => [...c, { kind: "explain", annotationId: tempId, location: loc, passage: expanded.passage, manualSelection: manualText, level: 0, sourceText: expanded.passage, text: "", loading: true, error: null, collapsed: false }]);
    try {
      if (!online) throw new Error("Offline — LLM actions are disabled.");
      const s = settingsRef.current;
      const { explanation } = await runExplain({ bookId, location: loc, passage: expanded.passage, manualSelection: manualText, context: contextStr, memory: memStr, hskLevel: s.hskLevel, learnerLanguage: s.learnerLanguage, currentLevel: 0 });
      setCards((c) => c.map((card) => card.kind === "explain" && card.annotationId === tempId ? { ...card, annotationId: explanation.annotationId, level: explanation.level, text: explanation.text, sourceText: explanation.sourceText, loading: false } : card));
      const decision = shouldUpdateMemoryImmediately(expanded.passage, memory);
      if (decision.immediate) await runMemoryPatch(bookId, expanded.passage, loc, currentChapter.id.split(":")[1]).catch(() => {});
    } catch (e) {
      setCards((c) => c.map((card) => card.kind === "explain" && card.annotationId === tempId ? { ...card, loading: false, error: e instanceof Error ? e.message : String(e) } : card));
    }
  }

  async function handleSimplifyFurther(card: Extract<ActiveCard, { kind: "explain" }>) {
    if (card.level >= 3 || !online) return;
    const memory = await getBookMemory(bookId);
    setCards((c) => c.map((x) => x.kind === "explain" && x.annotationId === card.annotationId ? { ...x, loading: true, error: null } : x));
    try {
      const s = settingsRef.current;
      const { explanation } = await runExplain({ bookId, location: card.location, passage: card.passage, manualSelection: card.manualSelection, context: "", memory: memoryToCompactString(memory), hskLevel: s.hskLevel, learnerLanguage: s.learnerLanguage, currentLevel: card.level as 0 | 1 | 2 | 3, parentExplanationId: card.annotationId, sourceText: card.text });
      setCards((c) => c.map((x) => x.kind === "explain" && x.annotationId === card.annotationId ? { ...x, level: explanation.level, text: explanation.text, sourceText: explanation.sourceText, loading: false } : x));
    } catch (e) {
      setCards((c) => c.map((x) => x.kind === "explain" && x.annotationId === card.annotationId ? { ...x, loading: false, error: String(e) } : x));
    }
  }

  async function handleFreshExplain(card: Extract<ActiveCard, { kind: "explain" }>) {
    if (!online) return;
    setCards((c) => c.map((x) => x.kind === "explain" && x.annotationId === card.annotationId ? { ...x, loading: true, error: null, level: 0 } : x));
    try {
      const { explanation } = await freshExplain(bookId, card.annotationId);
      setCards((c) => c.map((x) => x.kind === "explain" && x.annotationId === card.annotationId ? { ...x, level: explanation.level, text: explanation.text, sourceText: explanation.sourceText, loading: false } : x));
    } catch (e) {
      setCards((c) => c.map((x) => x.kind === "explain" && x.annotationId === card.annotationId ? { ...x, loading: false, error: String(e) } : x));
    }
  }

  async function handleUnderstand() {
    if (!toolbar || !currentChapter) return;
    const manualText = toolbar.manualText;
    const expanded = buildPassage(manualText);
    if (!expanded) return;
    const loc = makeLocation(manualText)!;
    setToolbar(null);
    const tempId = crypto.randomUUID();
    setCards((c) => [...c, { kind: "understand", annotationId: tempId, location: loc, passage: expanded.passage, manualSelection: manualText, followUpCount: 0, lastAssessment: null, loading: false, error: null, collapsed: false }]);
  }

  async function submitAnswer(card: Extract<ActiveCard, { kind: "understand" }>, answer: string) {
    if (!answer.trim() || !online) return;
    setCards((c) => c.map((x) => x.kind === "understand" && x.annotationId === card.annotationId ? { ...x, loading: true, error: null } : x));
    try {
      const memory = await getBookMemory(bookId);
      const ctx = gatherContext(paragraphs, 0, 0, 2, 2);
      const contextStr = [...ctx.before, ...ctx.after].join("\n\n");
      const previousQuestions = card.followUpCount > 0 ? [{ q: card.lastAssessment?.nextQuestionInChinese || "", a: "" }] : [];
      const s = settingsRef.current;
      // Assistance level = highest simplification level viewed for this passage
      // before the final answer (spec §10). 0 means unassisted.
      const maxExplainLevel = cards
        .filter((x) => x.kind === "explain" && x.location.textQuote === card.location.textQuote && !x.loading && x.text)
        .reduce((m, x) => Math.max(m, x.kind === "explain" ? x.level : 0), 0);
      const assistanceLevel = Math.min(3, maxExplainLevel) as 0 | 1 | 2 | 3;
      const result = await runAssess({ bookId, annotationId: card.annotationId, passage: card.passage, context: contextStr, memory: memoryToCompactString(memory), answer, learnerLanguage: s.learnerLanguage, previousQuestions, followUpCount: card.followUpCount, assistanceLevel, unassisted: card.followUpCount === 0 && assistanceLevel === 0 });
      setCards((c) => c.map((x) => x.kind === "understand" && x.annotationId === card.annotationId ? { ...x, annotationId: result.attempt.annotationId, attemptId: result.attempt.id, followUpCount: card.followUpCount + 1, lastAssessment: result.assessment, loading: false } : x));
    } catch (e) {
      setCards((c) => c.map((x) => x.kind === "understand" && x.annotationId === card.annotationId ? { ...x, loading: false, error: String(e) } : x));
    }
  }

  async function handleCompanion() {
    if (!toolbar || !online) return;
    const ctx = toolbar.manualText;
    const memory = await getBookMemory(bookId);
    setCards((c) => [...c, { kind: "companion", text: "", loading: true, collapsed: false }]);
    try {
      const r = await runCompanion(bookId, ctx, memoryToCompactString(memory));
      setCards((c) => c.map((x) => x.kind === "companion" && x.loading ? { ...x, text: r.text, loading: false } : x));
    } catch (e) {
      setCards((c) => c.map((x) => x.kind === "companion" && x.loading ? { ...x, loading: false, text: "Error: " + String(e) } : x));
    }
  }

  function goChapter(delta: number) {
    if (!currentChapter) return;
    const idx = currentChapter.index + delta;
    const next = chapters[idx];
    if (next && adapter) renderChapterInner(adapter, next);
  }

  function clearHighlights(doc: Document) {
    doc.querySelectorAll("span.mkp-highlight-strong, span.mkp-highlight-passage").forEach((el) => {
      const parent = el.parentNode;
      if (!parent) return;
      while (el.firstChild) parent.insertBefore(el.firstChild, el);
      parent.removeChild(el);
      parent.normalize();
    });
  }

  function surroundRange(doc: Document, range: Range, cls: string) {
    try {
      const span = doc.createElement("span");
      span.className = cls;
      range.surroundContents(span);
    } catch {
      // surroundContents fails across node boundaries; ignore gracefully.
    }
  }

  if (loading && !rendered) return <div className="content"><p>Loading book…</p></div>;
  if (error) return <div className="content"><div className="error">{error}</div></div>;

  return (
    <div className="reader-layout" style={{ ["--reader-content-width" as string]: settings.contentWidth + "px" } as React.CSSProperties}>
      <div className="reader-pane">
        <div className="row" style={{ padding: "10px 16px", borderBottom: "1px solid var(--border)", gap: 8 }}>
          <button onClick={() => goChapter(-1)} disabled={!currentChapter || currentChapter.index === 0}>← Prev</button>
          <strong style={{ flex: 1, textAlign: "center" }}>{currentChapter?.label}</strong>
          <button onClick={() => goChapter(1)} disabled={!currentChapter || currentChapter.index >= chapters.length - 1}>Next →</button>
        </div>
        <div className="reader-host" ref={hostRef} style={{ maxWidth: settings.contentWidth, margin: "0 auto" }} />
        {toolbar && (
          <SelectionToolbar x={toolbar.x} y={toolbar.y} online={online} onExplain={handleExplain} onUnderstand={handleUnderstand} onCompanion={handleCompanion} onCopy={() => { navigator.clipboard?.writeText(toolbar.manualText); setToolbar(null); }} />
        )}
        <div className="row" style={{ position: "fixed", bottom: 12, left: "50%", transform: "translateX(-50%)", background: "var(--card)", border: "1px solid var(--border)", borderRadius: 999, padding: "4px 10px", gap: 8, zIndex: 40 }}>
          <button onClick={() => setSettings({ fontSize: Math.max(14, settings.fontSize - 1) })} aria-label="Smaller font">A−</button>
          <button onClick={() => setSettings({ fontSize: Math.min(28, settings.fontSize + 1) })} aria-label="Larger font">A+</button>
          <button onClick={() => setSettings({ theme: settings.theme === "dark" ? "light" : "dark" })} aria-label="Toggle theme">{settings.theme === "dark" ? "☀" : "☾"}</button>
          <button onClick={() => setSettings({ lineHeight: settings.lineHeight < 2 ? 2.2 : 1.6 })} aria-label="Line height">≡</button>
        </div>
      </div>
      <AssistancePanel
        cards={cards}
        online={online}
        onSimplifyFurther={handleSimplifyFurther}
        onFreshExplain={handleFreshExplain}
        onSubmitAnswer={submitAnswer}
        onContinueReading={async (card) => {
          if (card.kind === "understand" && card.attemptId) await finishAttempt(card.attemptId);
          setCards((c) => c.map((x) => (x.kind === "understand" && x.annotationId === (card.kind === "companion" ? "" : card.annotationId)) ? { ...x, collapsed: true } : x));
        }}
        onClose={(id) => setCards((c) => c.filter((x) => !(x.kind !== "companion" && x.annotationId === id)))}
        onCloseCompanion={() => setCards((c) => c.filter((x) => x.kind !== "companion"))}
        onExpand={(id) => setCards((c) => c.map((x) => (x.kind === "explain" || x.kind === "understand") && x.annotationId === id ? { ...x, collapsed: false } : x))}
      />
    </div>
  );
}