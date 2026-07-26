import { useEffect, useState } from "react";
import { computeHeadlineStats, type HeadlineStats } from "./stats";
import { useUiStore } from "../app/ui-store";
import { ASSESSMENT_FRIENDLY } from "../shared/domain";

export function StatsView() {
  const setView = useUiStore((s) => s.setView);
  const [stats, setStats] = useState<HeadlineStats | null>(null);

  useEffect(() => {
    void computeHeadlineStats().then(setStats);
  }, []);

  return (
    <div className="page stats-page">
      <header className="page-header">
        <div>
          <p className="brand">看书朋友</p>
          <h1>Statistics</h1>
          <p className="lede">First attempts only — retries do not inflate scores.</p>
        </div>
        <button type="button" className="ghost" onClick={() => setView("library")}>
          Back
        </button>
      </header>
      {!stats ? (
        <p>Loading…</p>
      ) : (
        <div className="stats-grid">
          <p>First attempts: {stats.firstAttemptCount}</p>
          <p>Explained passages: {stats.passagesExplained}</p>
          <p>Reading sessions: {stats.readingSessions}</p>
          <p>Avg assistance level: {stats.averageAssistanceLevel.toFixed(2)}</p>
          <p>
            Unassisted / assisted: {stats.unassistedCount} / {stats.assistedCount}
          </p>
          <h2>Score distribution</h2>
          <ul>
            {([0, 1, 2, 3, 4] as const).map((s) => (
              <li key={s}>
                {ASSESSMENT_FRIENDLY[
                  s === 0
                    ? "missed"
                    : s === 1
                      ? "emerging"
                      : s === 2
                        ? "main_idea"
                        : s === 3
                          ? "strong"
                          : "deep"
                ]}
                : {stats.scoreDistribution[s]}
              </li>
            ))}
          </ul>
          <h2>Book progress</h2>
          <ul>
            {stats.books.map((b) => (
              <li key={b.id}>
                {b.title}: {Math.round(b.progress * 100)}%
              </li>
            ))}
          </ul>
        </div>
      )}
    </div>
  );
}
