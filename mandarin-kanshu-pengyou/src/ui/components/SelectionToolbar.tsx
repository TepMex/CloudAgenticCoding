import { useUiStore } from "../../app/ui-store";
import type { ChapterRecord, ProviderProfile } from "../../shared/domain";
import { nearbyParagraphs } from "../../reader/selection/sentences";
import { buildReaderLocation } from "../../reader/locations/location";
import { runExplain, nextSimplificationLevel } from "../../explain/service";
import { createId } from "../../shared/id";
import { db } from "../../db/database";
import { getStructuredFailure, isAbortError } from "../../providers/structured";
import { safeErrorMessage } from "../../security/redact";
import { runCompanionReaction } from "../../companion/service";

type Props = {
  bookId: string;
  chapter: ChapterRecord;
  onNeedProfile: (task: "explain" | "assess" | "companion") => Promise<ProviderProfile | null>;
};

export function SelectionToolbar({ bookId, chapter, onNeedProfile }: Props) {
  const visible = useUiStore((s) => s.toolbarVisible);
  const x = useUiStore((s) => s.toolbarX);
  const y = useUiStore((s) => s.toolbarY);
  const selection = useUiStore((s) => s.selection);
  const offline = useUiStore((s) => s.offline);
  const showLong = useUiStore((s) => s.showLongPassageWarning);
  const setToolbar = useUiStore((s) => s.setToolbar);
  const upsertCard = useUiStore((s) => s.upsertCard);
  const setActiveRequest = useUiStore((s) => s.setActiveRequest);
  const activeRequest = useUiStore((s) => s.activeRequest);
  const setPanelOpen = useUiStore((s) => s.setPanelOpen);

  if (!visible || !selection) return null;

  const startExplain = async (opts?: { force?: boolean; level?: 1 | 2 | 3 }) => {
    if (offline) return;
    if (activeRequest) return;
    if (selection.exceedsSoftLimit && !opts?.force && !window.confirm(
      `This passage has about ${selection.sentenceCount} sentences (soft limit ~5). Continue anyway?`,
    )) {
      return;
    }

    const profile = await onNeedProfile("explain");
    if (!profile) {
      upsertCard({
        id: createId("card"),
        kind: "error",
        collapsed: false,
        passageInView: true,
        title: "Provider missing",
        body: "Configure an Explain profile in Settings.",
      });
      return;
    }

    const settings = await db.settings.get("app");
    const nearby = nearbyParagraphs(
      chapter.plainText,
      selection.expandedStart,
      selection.expandedEnd,
    );
    const location = buildReaderLocation({
      bookId,
      spineItemId: chapter.spineItemId,
      chapterText: chapter.plainText,
      start: selection.manualStart,
      end: selection.manualEnd,
      approximateProgress: selection.expandedStart / Math.max(1, chapter.plainText.length),
    });

    const cardId = createId("card");
    const controller = new AbortController();
    setActiveRequest({ id: cardId, kind: "explain", controller });
    setPanelOpen(true);
    setToolbar(false);
    upsertCard({
      id: cardId,
      kind: "explain",
      collapsed: false,
      passageInView: true,
      title: "Explain",
      loading: true,
      level: 0,
    });

    try {
      const level = opts?.level ?? 1;
      const result = await runExplain({
        bookId,
        chapterId: chapter.spineItemId,
        location,
        originalPassage: selection.expandedText,
        manualSelection: selection.manualText,
        contextBefore: nearby.before,
        contextAfter: nearby.after,
        level,
        profile,
        hskLevel: settings?.hskLevel ?? 4,
        signal: controller.signal,
      });
      if (result.cancelled) {
        useUiStore.getState().updateCard(cardId, {
          loading: false,
          title: "Cancelled",
          body: "Request cancelled. Nothing was saved.",
        });
        return;
      }
      useUiStore.getState().updateCard(cardId, {
        loading: false,
        annotationId: result.annotationId,
        explanationId: result.explanation.id,
        level,
        title: `Explanation · Level ${level}`,
        body: result.simplifiedChinese,
      });

      // Optional companion
      if (settings?.companionEnabled) {
        const companionProfile = (await onNeedProfile("companion")) ?? profile;
        void runCompanionReaction({
          bookId,
          chapterId: chapter.spineItemId,
          location,
          passage: selection.expandedText,
          profile: companionProfile,
        }).then((r) => {
          if (r.cancelled || !r.text) return;
          upsertCard({
            id: createId("card"),
            kind: "companion",
            collapsed: false,
            passageInView: true,
            title: "Companion",
            body: r.text,
            annotationId: r.annotationId,
          });
        });
      }
    } catch (e) {
      if (isAbortError(e)) return;
      const failure = getStructuredFailure(e);
      useUiStore.getState().updateCard(cardId, {
        loading: false,
        kind: "error",
        title: "Could not parse response",
        body: safeErrorMessage(e),
        rawError: failure?.raw,
      });
    } finally {
      setActiveRequest(null);
    }
  };

  const startUnderstand = () => {
    if (offline) return;
    if (!selection) return;
    const cardId = createId("card");
    setPanelOpen(true);
    setToolbar(false);
    upsertCard({
      id: cardId,
      kind: "understand",
      collapsed: false,
      passageInView: true,
      title: "Understand",
      body: selection.expandedText,
      level: 0,
    });
  };

  return (
    <div
      className="selection-toolbar"
      style={{ left: x, top: y }}
      role="toolbar"
      aria-label="Passage actions"
    >
      <button type="button" disabled={offline || Boolean(activeRequest)} onClick={() => void startExplain()}>
        Explain
      </button>
      <button type="button" disabled={offline || Boolean(activeRequest)} onClick={startUnderstand}>
        Understand
      </button>
      <button
        type="button"
        onClick={() => {
          void navigator.clipboard.writeText(selection.manualText);
        }}
      >
        Copy
      </button>
      {showLong && (
        <span className="soft-warn" title="Long passage">
          Long · {selection.sentenceCount} sentences
        </span>
      )}
      {offline && <span className="soft-warn">LLM offline</span>}
    </div>
  );
}

export async function simplifyFurther(opts: {
  bookId: string;
  chapter: ChapterRecord;
  cardId: string;
  explanationId: string;
  currentLevel: 1 | 2 | 3;
  profile: ProviderProfile;
  fallbackProfile?: ProviderProfile | null;
  fresh?: boolean;
}): Promise<void> {
  const next = opts.fresh ? 1 : nextSimplificationLevel(opts.currentLevel);
  if (!next && !opts.fresh) return;

  const level = (opts.fresh ? 1 : next) as 1 | 2 | 3;
  const exp = await db.explanations.get(opts.explanationId);
  if (!exp) return;
  if (!opts.fresh && opts.currentLevel >= 3) return;

  const profile =
    opts.fresh && opts.fallbackProfile ? opts.fallbackProfile : opts.profile;

  const settings = await db.settings.get("app");
  const nearby = nearbyParagraphs(
    opts.chapter.plainText,
    0,
    Math.min(exp.originalPassage.length, opts.chapter.plainText.length),
  );
  // Prefer locating original passage
  const idx = opts.chapter.plainText.indexOf(exp.originalPassage.slice(0, 40));
  const start = Math.max(0, idx);
  const location = buildReaderLocation({
    bookId: opts.bookId,
    spineItemId: opts.chapter.spineItemId,
    chapterText: opts.chapter.plainText,
    start,
    end: start + exp.manualSelection.length,
  });

  const source =
    opts.fresh
      ? undefined
      : exp.levels.find((l) => l.level === opts.currentLevel)?.text;

  const controller = new AbortController();
  useUiStore.getState().setActiveRequest({
    id: opts.cardId,
    kind: "explain",
    controller,
  });
  useUiStore.getState().updateCard(opts.cardId, { loading: true });

  try {
    const result = await runExplain({
      bookId: opts.bookId,
      chapterId: opts.chapter.spineItemId,
      location,
      originalPassage: exp.originalPassage,
      manualSelection: exp.manualSelection,
      contextBefore: nearby.before,
      contextAfter: nearby.after,
      level,
      sourceTextForIteration: source,
      annotationId: exp.annotationId,
      explanationId: exp.id,
      profile,
      hskLevel: settings?.hskLevel ?? 4,
      signal: controller.signal,
      usingFallback: Boolean(opts.fresh && opts.fallbackProfile),
    });
    if (result.cancelled) {
      useUiStore.getState().updateCard(opts.cardId, {
        loading: false,
        body: "Cancelled. Nothing new was saved.",
      });
      return;
    }
    useUiStore.getState().updateCard(opts.cardId, {
      loading: false,
      level,
      title: `Explanation · Level ${level}`,
      body: result.simplifiedChinese,
      explanationId: result.explanation.id,
    });
  } catch (e) {
    useUiStore.getState().updateCard(opts.cardId, {
      loading: false,
      kind: "error",
      title: "Explain failed",
      body: safeErrorMessage(e),
      rawError: getStructuredFailure(e)?.raw,
    });
  } finally {
    useUiStore.getState().setActiveRequest(null);
  }
}
