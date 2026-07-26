import { useEffect, useState } from "react";
import { useUiStore } from "../../app/ui-store";
import type { ChapterRecord } from "../../shared/domain";
import { ASSESSMENT_FRIENDLY } from "../../shared/domain";
import { db } from "../../db/database";
import { nearbyParagraphs } from "../../reader/selection/sentences";
import { buildReaderLocation } from "../../reader/locations/location";
import { runAssessment, bumpAttemptAssistance } from "../../assessment/service";
import { simplifyFurther } from "../components/SelectionToolbar";
import { createId } from "../../shared/id";
import { getStructuredFailure, isAbortError } from "../../providers/structured";
import { safeErrorMessage } from "../../security/redact";
import type { AssistanceLevel } from "../../shared/domain";

type Props = {
  bookId: string;
  chapter: ChapterRecord;
};

export function AssistancePanel({ bookId, chapter }: Props) {
  const cards = useUiStore((s) => s.cards);
  const panelOpen = useUiStore((s) => s.panelOpen);
  const setPanelOpen = useUiStore((s) => s.setPanelOpen);
  const offline = useUiStore((s) => s.offline);
  const activeRequest = useUiStore((s) => s.activeRequest);
  const cancelActiveRequest = useUiStore((s) => s.cancelActiveRequest);
  const updateCard = useUiStore((s) => s.updateCard);
  const revealNative = useUiStore((s) => s.revealNativeQuestion);
  const setRevealNative = useUiStore((s) => s.setRevealNativeQuestion);
  const selection = useUiStore((s) => s.selection);

  const [answerDrafts, setAnswerDrafts] = useState<Record<string, string>>({});
  const [questionState, setQuestionState] = useState<
    Record<
      string,
      {
        index: number;
        qZh: string | null;
        qNative: string | null;
        lastLabel?: string;
        keyClue?: string;
        feedback?: string;
        corrected?: string;
        ambiguity?: string | null;
      }
    >
  >({});

  // Intersection-style collapse: when panel scrolls, optional
  useEffect(() => {
    const onResize = () => {
      // mobile vs desktop handled by CSS
    };
    window.addEventListener("resize", onResize);
    return () => window.removeEventListener("resize", onResize);
  }, []);

  if (!panelOpen && !cards.length) return null;

  const submitUnderstand = async (cardId: string) => {
    if (offline || activeRequest) return;
    const draft = answerDrafts[cardId]?.trim();
    if (!draft) return;
    const card = cards.find((c) => c.id === cardId);
    if (!card) return;

    const passage = selection?.expandedText ?? card.body ?? "";
    const settings = await db.settings.get("app");
    const assignments = await db.taskModelAssignments.get("default");
    const profileId = assignments?.assessProfileId;
    if (!profileId) {
      updateCard(cardId, { body: "Configure an Assess profile in Settings." });
      return;
    }
    const profile = await db.providerProfiles.get(profileId);
    if (!profile) return;

    const start = selection?.expandedStart ?? 0;
    const end = selection?.expandedEnd ?? passage.length;
    const nearby = nearbyParagraphs(chapter.plainText, start, end);
    const location = buildReaderLocation({
      bookId,
      spineItemId: chapter.spineItemId,
      chapterText: chapter.plainText,
      start: selection?.manualStart ?? start,
      end: selection?.manualEnd ?? end,
    });

    const q = questionState[cardId];
    const controller = new AbortController();
    useUiStore.getState().setActiveRequest({
      id: cardId,
      kind: "understand",
      controller,
    });
    updateCard(cardId, { loading: true });

    try {
      const result = await runAssessment({
        bookId,
        chapterId: chapter.spineItemId,
        location,
        passage,
        contextBefore: nearby.before,
        contextAfter: nearby.after,
        learnerAnswer: draft,
        learnerLanguage: settings?.learnerLanguage ?? "ru",
        profile,
        assistanceLevel: (card.level ?? 0) as AssistanceLevel,
        attemptId: card.attemptId,
        annotationId: card.annotationId,
        questionIndex: q?.index ?? 0,
        questionInChinese: q?.qZh,
        questionInNativeLanguage: q?.qNative,
        signal: controller.signal,
      });

      if (result.cancelled) {
        updateCard(cardId, {
          loading: false,
          title: "Cancelled",
          body: "Cancelled. Partial output was not saved.",
        });
        return;
      }

      const a = result.assessment;
      setQuestionState((s) => ({
        ...s,
        [cardId]: {
          index: (q?.index ?? 0) + 1,
          qZh: a.nextQuestionInChinese,
          qNative: a.nextQuestionInNativeLanguage,
          lastLabel: ASSESSMENT_FRIENDLY[a.label],
          keyClue: a.keyClueInChinese,
          feedback: a.feedbackInNativeLanguage,
          corrected: a.correctedUnderstandingInNativeLanguage,
          ambiguity: a.ambiguityNote,
        },
      }));
      setAnswerDrafts((d) => ({ ...d, [cardId]: "" }));
      updateCard(cardId, {
        loading: false,
        attemptId: result.attempt.id,
        annotationId: result.attempt.annotationId,
        title: `Understand · ${ASSESSMENT_FRIENDLY[a.label]}`,
        body: passage,
      });
    } catch (e) {
      if (isAbortError(e)) return;
      updateCard(cardId, {
        loading: false,
        kind: "error",
        title: "Assessment failed",
        body: safeErrorMessage(e),
        rawError: getStructuredFailure(e)?.raw,
      });
    } finally {
      useUiStore.getState().setActiveRequest(null);
    }
  };

  return (
    <aside className={`assistance-panel ${panelOpen ? "open" : ""}`} aria-label="Reading assistance">
      <div className="panel-header">
        <h2>Assistance</h2>
        <div className="panel-header-actions">
          {activeRequest && (
            <button type="button" onClick={cancelActiveRequest}>
              Cancel
            </button>
          )}
          <button type="button" className="ghost" onClick={() => setPanelOpen(false)}>
            Close
          </button>
        </div>
      </div>

      <div className="panel-cards">
        {cards
          .filter((c) => !c.collapsed)
          .map((card) => (
            <article key={card.id} className={`reading-card kind-${card.kind}`}>
              <header>
                {card.kind === "companion" ? (
                  <span className="companion-icon" aria-hidden>
                    ◈
                  </span>
                ) : null}
                <h3>{card.title}</h3>
                <button
                  type="button"
                  className="ghost"
                  onClick={() => updateCard(card.id, { collapsed: true })}
                >
                  Collapse
                </button>
              </header>

              {card.loading && <p className="loading-calm">Working…</p>}

              {card.kind === "explain" && card.body && (
                <div className="explain-compare">
                  <section>
                    <h4>Original</h4>
                    <p className="passage-block">{selection?.expandedText ?? "—"}</p>
                  </section>
                  <section>
                    <h4>Level {card.level}</h4>
                    <p className="passage-block simplified">{card.body}</p>
                  </section>
                  <div className="card-actions">
                    {card.level && card.level < 3 && card.explanationId && (
                      <button
                        type="button"
                        disabled={offline || Boolean(activeRequest)}
                        onClick={() =>
                          void (async () => {
                            const assignments = await db.taskModelAssignments.get("default");
                            const profile = assignments?.explainProfileId
                              ? await db.providerProfiles.get(assignments.explainProfileId)
                              : null;
                            if (!profile || !card.explanationId) return;
                            if (card.attemptId) {
                              await bumpAttemptAssistance(
                                card.attemptId,
                                ((card.level ?? 0) + 1) as AssistanceLevel,
                              );
                            }
                            await simplifyFurther({
                              bookId,
                              chapter,
                              cardId: card.id,
                              explanationId: card.explanationId,
                              currentLevel: card.level as 1 | 2 | 3,
                              profile,
                            });
                          })()
                        }
                      >
                        Simplify further
                      </button>
                    )}
                    {card.level === 3 && card.explanationId && (
                      <button
                        type="button"
                        disabled={offline || Boolean(activeRequest)}
                        onClick={() =>
                          void (async () => {
                            const assignments = await db.taskModelAssignments.get("default");
                            const profile = assignments?.explainProfileId
                              ? await db.providerProfiles.get(assignments.explainProfileId)
                              : null;
                            const fallback = assignments?.fallbackProfileId
                              ? await db.providerProfiles.get(assignments.fallbackProfileId)
                              : null;
                            if (!profile || !card.explanationId) return;
                            await simplifyFurther({
                              bookId,
                              chapter,
                              cardId: card.id,
                              explanationId: card.explanationId,
                              currentLevel: 3,
                              profile,
                              fallbackProfile: fallback,
                              fresh: true,
                            });
                          })()
                        }
                      >
                        Try a fresh explanation
                      </button>
                    )}
                  </div>
                </div>
              )}

              {card.kind === "understand" && (
                <div className="understand-flow">
                  <p className="passage-block">{card.body}</p>
                  {questionState[card.id]?.lastLabel && (
                    <div className="assessment-result">
                      <p className="score-label">{questionState[card.id]!.lastLabel}</p>
                      <p>{questionState[card.id]!.feedback}</p>
                      <p className="key-clue">
                        <span>线索</span> {questionState[card.id]!.keyClue}
                      </p>
                      {questionState[card.id]!.ambiguity && (
                        <p className="ambiguity">{questionState[card.id]!.ambiguity}</p>
                      )}
                      <p className="corrected">{questionState[card.id]!.corrected}</p>
                    </div>
                  )}
                  {questionState[card.id]?.qZh && (
                    <div className="followup-q">
                      <p>{questionState[card.id]!.qZh}</p>
                      <button
                        type="button"
                        className="ghost"
                        onClick={() => setRevealNative(!revealNative)}
                      >
                        {revealNative ? "Hide translation" : "Reveal in my language"}
                      </button>
                      {revealNative && questionState[card.id]!.qNative && (
                        <p className="q-native">{questionState[card.id]!.qNative}</p>
                      )}
                    </div>
                  )}
                  {(!questionState[card.id] || questionState[card.id]?.qZh) && (
                    <>
                      <label className="sr-only" htmlFor={`ans-${card.id}`}>
                        Your answer
                      </label>
                      <textarea
                        id={`ans-${card.id}`}
                        rows={4}
                        value={answerDrafts[card.id] ?? ""}
                        onChange={(e) =>
                          setAnswerDrafts((d) => ({ ...d, [card.id]: e.target.value }))
                        }
                        placeholder="Explain the passage in your language…"
                        disabled={card.loading}
                      />
                      <div className="card-actions">
                        <button
                          type="button"
                          disabled={offline || card.loading}
                          onClick={() => void submitUnderstand(card.id)}
                        >
                          Submit
                        </button>
                        <button
                          type="button"
                          className="ghost"
                          onClick={() => updateCard(card.id, { collapsed: true })}
                        >
                          Continue reading
                        </button>
                        {questionState[card.id]?.lastLabel && (
                          <button
                            type="button"
                            className="ghost"
                            disabled={offline}
                            onClick={() =>
                              setQuestionState((s) => ({
                                ...s,
                                [card.id]: {
                                  ...(s[card.id] ?? {
                                    index: 1,
                                    qZh: "再想想：这段话最重要的一点是什么？",
                                    qNative: null,
                                  }),
                                  qZh:
                                    s[card.id]?.qZh ??
                                    "这段话里，还有哪个细节你不太确定？",
                                  index: s[card.id]?.index ?? 1,
                                },
                              }))
                            }
                          >
                            Improve understanding
                          </button>
                        )}
                        <button
                          type="button"
                          className="ghost"
                          disabled={offline}
                          onClick={() =>
                            setQuestionState((s) => ({
                              ...s,
                              [card.id]: {
                                index: s[card.id]?.index ?? 1,
                                qZh: "如果换个角度，作者为什么要这样写？",
                                qNative: "Why might the author write it this way?",
                                lastLabel: s[card.id]?.lastLabel,
                                keyClue: s[card.id]?.keyClue,
                                feedback: s[card.id]?.feedback,
                                corrected: s[card.id]?.corrected,
                                ambiguity: s[card.id]?.ambiguity,
                              },
                            }))
                          }
                        >
                          Challenge me more
                        </button>
                      </div>
                    </>
                  )}
                  {questionState[card.id] && !questionState[card.id]?.qZh && (
                    <button
                      type="button"
                      className="ghost"
                      onClick={() => {
                        // New attempt
                        const newId = createId("card");
                        useUiStore.getState().upsertCard({
                          id: newId,
                          kind: "understand",
                          collapsed: false,
                          passageInView: true,
                          title: "Understand · new attempt",
                          body: card.body,
                          level: card.level ?? 0,
                        });
                      }}
                    >
                      New attempt
                    </button>
                  )}
                </div>
              )}

              {card.kind === "companion" && card.body && (
                <p className="companion-body">{card.body}</p>
              )}

              {card.kind === "error" && (
                <div className="error-card">
                  <p>{card.body}</p>
                  {card.rawError && (
                    <pre className="raw-response">{card.rawError}</pre>
                  )}
                  <div className="card-actions">
                    {card.rawError && (
                      <button
                        type="button"
                        onClick={() => void navigator.clipboard.writeText(card.rawError ?? "")}
                      >
                        Copy raw response
                      </button>
                    )}
                    <button
                      type="button"
                      className="ghost"
                      onClick={() =>
                        useUiStore.setState({
                          cards: useUiStore.getState().cards.filter((c) => c.id !== card.id),
                        })
                      }
                    >
                      Dismiss
                    </button>
                  </div>
                </div>
              )}
            </article>
          ))}
      </div>
    </aside>
  );
}
