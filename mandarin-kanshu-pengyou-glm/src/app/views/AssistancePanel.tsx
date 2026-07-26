import { useState } from "react";
import type { ActiveCard } from "./cardTypes";
import { SCORE_LABELS } from "../../shared/domain";

export function AssistancePanel({ cards, online, onSimplifyFurther, onFreshExplain, onSubmitAnswer, onContinueReading, onClose, onCloseCompanion, onExpand }: {
  cards: ActiveCard[];
  online: boolean;
  onSimplifyFurther: (card: Extract<ActiveCard, { kind: "explain" }>) => void;
  onFreshExplain: (card: Extract<ActiveCard, { kind: "explain" }>) => void;
  onSubmitAnswer: (card: Extract<ActiveCard, { kind: "understand" }>, answer: string) => void;
  onContinueReading: (card: ActiveCard) => void;
  onClose: (id: string) => void;
  onCloseCompanion: () => void;
  onExpand: (id: string) => void;
}) {
  return (
    <aside className="panel" aria-label="Reading assistance">
      <h3 style={{ marginTop: 0 }}>Reading assistance</h3>
      {cards.length === 0 && <p className="muted">Select a passage in the book to explain or assess your understanding.</p>}
      {cards.map((card, i) => (
        <CardView
          key={i}
          card={card}
          online={online}
          onSimplifyFurther={() => card.kind === "explain" && onSimplifyFurther(card)}
          onFreshExplain={() => card.kind === "explain" && onFreshExplain(card)}
          onSubmitAnswer={(a) => card.kind === "understand" && onSubmitAnswer(card, a)}
          onContinueReading={() => onContinueReading(card)}
          onClose={() => { if (card.kind === "companion") onCloseCompanion(); else onClose(card.annotationId); }}
          onExpand={() => card.kind !== "companion" && onExpand(card.annotationId)}
        />
      ))}
    </aside>
  );
}

function CardView({ card, online, onSimplifyFurther, onFreshExplain, onSubmitAnswer, onContinueReading, onClose, onExpand }: {
  card: ActiveCard; online: boolean;
  onSimplifyFurther: () => void; onFreshExplain: () => void;
  onSubmitAnswer: (a: string) => void; onContinueReading: () => void;
  onClose: () => void; onExpand: () => void;
}) {
  const [answer, setAnswer] = useState("");
  if (card.collapsed) {
    return (
      <div className="card" style={{ cursor: "pointer" }} onClick={onExpand}>
        <span className="marker marker-collapsed" /> <span className="muted">{card.kind} — tap to expand</span>
      </div>
    );
  }
  if (card.kind === "explain") {
    return (
      <div className="card assistance-card">
        <div className="row" style={{ justifyContent: "space-between" }}>
          <h3>Explain · Level {card.level || "—"}</h3>
          <button onClick={onClose} aria-label="Close">✕</button>
        </div>
        <div style={{ margin: "6px 0", fontSize: 13, color: "var(--ink-soft)", borderLeft: "3px solid var(--amber)", padding: "4px 8px", background: "var(--amber-soft)" }}>
          <strong>Passage:</strong> {card.passage.slice(0, 160)}{card.passage.length > 160 ? "…" : ""}
        </div>
        {card.loading && <p><span className="loading" /> Explaining…</p>}
        {card.error && <div className="error">{card.error}</div>}
        {!card.loading && !card.error && card.text && (
          <>
            <div style={{ whiteSpace: "pre-wrap", padding: "8px 0" }}>{card.text}</div>
            <div className="row">
              {card.level < 3 && <button onClick={onSimplifyFurther} disabled={!online}>Simplify further →</button>}
              {card.level >= 3 && <button onClick={onFreshExplain} disabled={!online}>Try a fresh explanation</button>}
            </div>
          </>
        )}
      </div>
    );
  }
  if (card.kind === "understand") {
    const a = card.lastAssessment;
    return (
      <div className="card assistance-card">
        <div className="row" style={{ justifyContent: "space-between" }}>
          <h3>Understand</h3>
          <button onClick={onClose} aria-label="Close">✕</button>
        </div>
        <div style={{ fontSize: 13, color: "var(--ink-soft)", borderLeft: "3px solid var(--moss)", padding: "4px 8px", background: "var(--paper-2)", margin: "6px 0" }}>
          {card.passage.slice(0, 140)}…
        </div>
        {a && (
          <div style={{ margin: "8px 0" }}>
            <span className={"score-pill score-" + a.score}>Score: {SCORE_LABELS[a.score]}</span>
            <p style={{ marginTop: 8 }}>{a.feedbackInNativeLanguage}</p>
            {a.correctedUnderstandingInNativeLanguage && <p className="muted"><em>Corrected:</em> {a.correctedUnderstandingInNativeLanguage}</p>}
            {a.keyClueInChinese && <p className="muted"><em>Key clue:</em> {a.keyClueInChinese}</p>}
            {a.ambiguityNote && <p className="muted"><em>Ambiguity:</em> {a.ambiguityNote}</p>}
          </div>
        )}
        {a?.shouldContinueQuestioning && card.followUpCount < 3 && (
          <div className="card" style={{ background: "var(--paper-2)", margin: "8px 0" }}>
            <strong>Next question:</strong>
            <p>{a.nextQuestionInChinese}</p>
            {a.nextQuestionInNativeLanguage && <p className="muted">{a.nextQuestionInNativeLanguage}</p>}
          </div>
        )}
        {card.loading ? <p><span className="loading" /> Assessing…</p> : (
          <>
            <textarea value={answer} onChange={(e) => setAnswer(e.target.value)} placeholder="Write your understanding in your native language…" />
            <div className="row" style={{ marginTop: 8 }}>
              <button className="primary" onClick={() => { onSubmitAnswer(answer); setAnswer(""); }} disabled={!online || !answer.trim()}>Submit answer</button>
              <button onClick={onContinueReading}>Continue reading</button>
            </div>
          </>
        )}
        {card.error && <div className="error" style={{ marginTop: 8 }}>{card.error}</div>}
      </div>
    );
  }
  return (
    <div className="card assistance-card">
      <div className="row" style={{ justifyContent: "space-between" }}>
        <h3>💬 Companion</h3>
        <button onClick={onClose} aria-label="Close">✕</button>
      </div>
      {card.loading ? <p><span className="loading" /></p> : <div style={{ whiteSpace: "pre-wrap" }}>{card.text}</div>}
    </div>
  );
}
