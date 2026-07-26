import { db } from "../db/database";
import type {
  AssistanceLevel,
  BookMemory,
  ExplanationRecord,
  ProviderProfile,
  ReaderLocation,
} from "../shared/domain";
import { createId, now } from "../shared/id";
import {
  simplificationSystemPrompt,
  simplificationUserPrompt,
} from "../providers/prompts";
import { simplificationSchema } from "../providers/schemas";
import { requestStructured, isAbortError } from "../providers/structured";
import { compactMemoryForPrompt } from "../memory/merge";
import { queueOrUpdateMemory } from "../memory/service";

export type ExplainInput = {
  bookId: string;
  chapterId: string;
  location: ReaderLocation;
  originalPassage: string;
  manualSelection: string;
  contextBefore: string;
  contextAfter: string;
  level: 1 | 2 | 3;
  sourceTextForIteration?: string;
  annotationId?: string;
  explanationId?: string;
  profile: ProviderProfile;
  hskLevel: number;
  signal?: AbortSignal;
  /** When true, use fallback profile — caller must pass that profile explicitly. */
  usingFallback?: boolean;
};

export type ExplainResult = {
  annotationId: string;
  explanation: ExplanationRecord;
  simplifiedChinese: string;
  cancelled: boolean;
};

export async function runExplain(input: ExplainInput): Promise<ExplainResult> {
  const memory =
    (await db.bookMemory.get(input.bookId)) ??
    ({
      bookId: input.bookId,
      synopsis: "",
      entities: [],
      currentChapterEvents: [],
      completedChapterSummaries: [],
      currentChapterId: input.chapterId,
      updatedAt: now(),
      revision: 0,
    } satisfies BookMemory);

  const chapterSummary =
    memory.completedChapterSummaries.find((c) => c.chapterId === input.chapterId)
      ?.summary ?? "";
  const recentEvents = memory.currentChapterEvents
    .filter((e) => e.chapterId === input.chapterId)
    .slice(-8)
    .map((e) => e.summary)
    .join("；");

  try {
    const result = await requestStructured({
      profile: input.profile,
      schema: simplificationSchema,
      task: `explain_l${input.level}`,
      bookId: input.bookId,
      chapterId: input.chapterId,
      signal: input.signal,
      messages: [
        {
          role: "system",
          content: simplificationSystemPrompt(input.level, input.hskLevel),
        },
        {
          role: "user",
          content: simplificationUserPrompt({
            originalPassage: input.originalPassage,
            contextBefore: input.contextBefore,
            contextAfter: input.contextAfter,
            bookMemoryCompact: compactMemoryForPrompt(memory),
            chapterSummary,
            recentEvents,
            sourceTextForIteration: input.sourceTextForIteration,
          }),
        },
      ],
    });

    if (input.signal?.aborted) {
      return {
        annotationId: input.annotationId ?? "",
        explanation: null as unknown as ExplanationRecord,
        simplifiedChinese: "",
        cancelled: true,
      };
    }

    const annotationId = input.annotationId ?? createId("ann");
    const explanationId = input.explanationId ?? createId("exp");

    await db.transaction("rw", db.annotations, db.explanations, async () => {
      const existingAnn = await db.annotations.get(annotationId);
      if (!existingAnn) {
        await db.annotations.put({
          id: annotationId,
          bookId: input.bookId,
          location: input.location,
          kind: "explain",
          createdAt: now(),
          updatedAt: now(),
        });
      } else {
        await db.annotations.update(annotationId, { updatedAt: now() });
      }

      const existing = await db.explanations.get(explanationId);
      const levelEntry = {
        level: input.level,
        text: result.data.simplifiedChinese,
        createdAt: now(),
        profileId: input.profile.id,
      };
      if (existing) {
        const levels = [
          ...existing.levels.filter((l) => l.level !== input.level),
          levelEntry,
        ].sort((a, b) => a.level - b.level);
        const highest = Math.max(
          existing.highestLevelViewed,
          input.level,
        ) as AssistanceLevel;
        await db.explanations.put({
          ...existing,
          levels,
          highestLevelViewed: highest,
        });
      } else {
        await db.explanations.put({
          id: explanationId,
          annotationId,
          bookId: input.bookId,
          originalPassage: input.originalPassage,
          manualSelection: input.manualSelection,
          levels: [levelEntry],
          highestLevelViewed: input.level,
        });
      }
    });

    const explanation = (await db.explanations.get(explanationId))!;

    // Memory update only after successful visible request
    void queueOrUpdateMemory({
      bookId: input.bookId,
      chapterId: input.chapterId,
      passage: input.originalPassage,
      contextBefore: input.contextBefore,
      contextAfter: input.contextAfter,
      location: input.location,
    });

    return {
      annotationId,
      explanation,
      simplifiedChinese: result.data.simplifiedChinese,
      cancelled: false,
    };
  } catch (e) {
    if (isAbortError(e)) {
      return {
        annotationId: input.annotationId ?? "",
        explanation: null as unknown as ExplanationRecord,
        simplifiedChinese: "",
        cancelled: true,
      };
    }
    throw e;
  }
}

export function nextSimplificationLevel(
  current: 1 | 2 | 3,
): 2 | 3 | null {
  if (current === 1) return 2;
  if (current === 2) return 3;
  return null;
}
