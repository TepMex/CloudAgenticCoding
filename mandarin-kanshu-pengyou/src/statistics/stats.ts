import { db } from "../db/database";
import type { AssessmentScore, AssistanceLevel } from "../shared/domain";

export type HeadlineStats = {
  firstAttemptCount: number;
  scoreDistribution: Record<AssessmentScore, number>;
  averageAssistanceLevel: number;
  unassistedCount: number;
  assistedCount: number;
  passagesExplained: number;
  readingSessions: number;
  books: { id: string; title: string; progress: number }[];
};

export async function computeHeadlineStats(): Promise<HeadlineStats> {
  const attempts = await db.assessmentAttempts.toArray();
  const first = attempts.filter((a) => a.isFirstAttemptForPassage && a.initialScore != null);

  const scoreDistribution: Record<AssessmentScore, number> = {
    0: 0,
    1: 0,
    2: 0,
    3: 0,
    4: 0,
  };
  let assistSum = 0;
  let unassisted = 0;
  let assisted = 0;
  for (const a of first) {
    const s = a.initialScore as AssessmentScore;
    scoreDistribution[s] += 1;
    assistSum += a.assistanceLevel;
    if (a.wasUnassistedInitially) unassisted++;
    else assisted++;
  }

  const explanations = await db.explanations.count();
  const sessions = await db.readingSessions.count();
  const books = await db.books.toArray();
  const positions = await db.readingPositions.toArray();
  const posMap = new Map(positions.map((p) => [p.bookId, p]));

  return {
    firstAttemptCount: first.length,
    scoreDistribution,
    averageAssistanceLevel: first.length
      ? assistSum / first.length
      : (0 as AssistanceLevel),
    unassistedCount: unassisted,
    assistedCount: assisted,
    passagesExplained: explanations,
    readingSessions: sessions,
    books: books.map((b) => {
      const pos = posMap.get(b.id);
      const idx = pos ? b.spineItemIds.indexOf(pos.spineItemId) : 0;
      const progress =
        b.spineItemIds.length > 0
          ? (Math.max(0, idx) + (pos?.scrollRatio ?? 0)) / b.spineItemIds.length
          : 0;
      return { id: b.id, title: b.title, progress };
    }),
  };
}

/** Pure helper for tests: first-attempt filter */
export function firstAttemptScores<T extends {
  isFirstAttemptForPassage: boolean;
  initialScore: AssessmentScore | null;
}>(attempts: T[]): AssessmentScore[] {
  return attempts
    .filter((a) => a.isFirstAttemptForPassage && a.initialScore != null)
    .map((a) => a.initialScore as AssessmentScore);
}
