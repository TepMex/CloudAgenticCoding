import { useEffect, useState } from "react";
import { computeStats, type Stats } from "../../statistics/service";
import { SCORE_LABELS } from "../../shared/domain";
import type { AssessmentScore } from "../../shared/domain";

export function StatsView({ bookId, onBack }: { bookId: string; onBack: () => void }) {
  const [stats, setStats] = useState<Stats | null>(null);
  useEffect(() => { computeStats(bookId).then(setStats); }, [bookId]);
  if (!stats) return <div className="content"><p>Loading…</p></div>;
  return (
    <div className="content">
      <div className="row" style={{ justifyContent: "space-between" }}>
        <h1>Statistics</h1>
        <button onClick={onBack}>← Back to reading</button>
      </div>
      <div className="grid2">
        <div className="card"><h3>Comprehension attempts</h3><p style={{ fontSize: 28 }}>{stats.totalAttempts}</p><p className="muted">{stats.firstAttempts} first attempts</p></div>
        <div className="card"><h3>Passages explained</h3><p style={{ fontSize: 28 }}>{stats.passagesExplained}</p></div>
        <div className="card"><h3>Reading sessions</h3><p style={{ fontSize: 28 }}>{stats.readingSessions}</p></div>
        <div className="card"><h3>Book progress</h3><div className="bar"><div style={{ width: Math.round(stats.bookProgress * 100) + "%" }} /></div><p className="muted">{Math.round(stats.bookProgress * 100)}%</p></div>
      </div>
      <div className="card">
        <h3>First-attempt score distribution</h3>
        {([0, 1, 2, 3, 4] as AssessmentScore[]).map((s) => (
          <div key={s} className="row" style={{ marginBottom: 6 }}>
            <span style={{ width: 90 }} className={"score-pill score-" + s}>{SCORE_LABELS[s]}</span>
            <div className="bar" style={{ flex: 1 }}><div style={{ width: (stats.firstAttempts ? (stats.scoreDistribution[s] / stats.firstAttempts) * 100 : 0) + "%" }} /></div>
            <span style={{ width: 30, textAlign: "right" }}>{stats.scoreDistribution[s]}</span>
          </div>
        ))}
      </div>
      <div className="card">
        <h3>Assistance</h3>
        <p>Average assistance level: <strong>{stats.avgAssistanceLevel.toFixed(2)}</strong></p>
        <p>Unassisted: <strong>{stats.unassistedCount}</strong> · Assisted: <strong>{stats.assistedCount}</strong></p>
      </div>
    </div>
  );
}
