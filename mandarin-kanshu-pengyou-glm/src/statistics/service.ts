import { db } from "../db/database";
import type { AssessmentScore } from "../shared/domain";

export type Stats = {
  totalAttempts: number; firstAttempts: number;
  scoreDistribution: Record<AssessmentScore, number>;
  avgAssistanceLevel: number; unassistedCount: number; assistedCount: number;
  passagesExplained: number; readingSessions: number; bookProgress: number;
};

export async function computeStats(bookId: string): Promise<Stats> {
  const attempts = await db.assessmentAttempts.where("bookId").equals(bookId).toArray();
  const annotations = await db.annotations.where("bookId").equals(bookId).toArray();
  const byAnnotation = new Map<string, typeof attempts>();
  for (const a of attempts) { const arr = byAnnotation.get(a.annotationId) ?? []; arr.push(a); byAnnotation.set(a.annotationId, arr); }
  const firstAttempts = Array.from(byAnnotation.values()).map((arr) => arr.sort((a, b) => a.createdAt - b.createdAt)[0]);
  const dist: Record<AssessmentScore, number> = { 0: 0, 1: 0, 2: 0, 3: 0, 4: 0 };
  let levelSum = 0, unassisted = 0;
  for (const a of firstAttempts) {
    const score = (a.initialScore ?? 0) as AssessmentScore;
    dist[score] = (dist[score] ?? 0) + 1;
    levelSum += a.assistanceLevel;
    if (a.unassisted) unassisted++;
  }
  const position = await db.readingPositions.get(bookId);
  const progress = position?.approximateProgress ?? 0;
  return {
    totalAttempts: attempts.length, firstAttempts: firstAttempts.length, scoreDistribution: dist,
    avgAssistanceLevel: firstAttempts.length ? levelSum / firstAttempts.length : 0,
    unassistedCount: unassisted, assistedCount: firstAttempts.length - unassisted,
    passagesExplained: annotations.length,
    readingSessions: new Set(attempts.map((a) => Math.floor(a.createdAt / 86400000))).size,
    bookProgress: progress,
  };
}
