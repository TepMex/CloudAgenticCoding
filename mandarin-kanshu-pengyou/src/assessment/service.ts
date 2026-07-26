import { db } from "../db/database";
import type {
  AssistanceLevel,
  AssessmentAttempt,
  BookMemory,
  ProviderProfile,
  ReaderLocation,
  UnderstandingAssessment,
} from "../shared/domain";
import { createId, now } from "../shared/id";
import {
  assessmentSystemPrompt,
  assessmentUserPrompt,
} from "../providers/prompts";
import {
  toAssessment,
  understandingAssessmentSchema,
} from "../providers/schemas";
import { requestStructured, isAbortError } from "../providers/structured";
import { compactMemoryForPrompt } from "../memory/merge";
import { queueOrUpdateMemory } from "../memory/service";

export type AssessInput = {
  bookId: string;
  chapterId: string;
  location: ReaderLocation;
  passage: string;
  contextBefore: string;
  contextAfter: string;
  learnerAnswer: string;
  learnerLanguage: string;
  profile: ProviderProfile;
  assistanceLevel: AssistanceLevel;
  attemptId?: string;
  annotationId?: string;
  questionIndex: number;
  questionInChinese?: string | null;
  questionInNativeLanguage?: string | null;
  signal?: AbortSignal;
};

export async function runAssessment(input: AssessInput): Promise<{
  attempt: AssessmentAttempt;
  assessment: UnderstandingAssessment;
  cancelled: boolean;
}> {
  const memory = (await db.bookMemory.get(input.bookId)) as BookMemory | undefined;

  let attemptId = input.attemptId;
  let annotationId = input.annotationId;

  if (!attemptId) {
    annotationId = annotationId ?? createId("ann");
    attemptId = createId("att");

    const priorForPassage = await db.assessmentAttempts
      .where("bookId")
      .equals(input.bookId)
      .filter(
        (a) =>
          a.location.textQuote === input.location.textQuote &&
          a.location.spineItemId === input.location.spineItemId,
      )
      .count();

    await db.transaction("rw", db.annotations, db.assessmentAttempts, async () => {
      await db.annotations.put({
        id: annotationId!,
        bookId: input.bookId,
        location: input.location,
        kind: "understand",
        createdAt: now(),
        updatedAt: now(),
      });
      await db.assessmentAttempts.put({
        id: attemptId!,
        annotationId: annotationId!,
        bookId: input.bookId,
        location: input.location,
        passage: input.passage,
        isFirstAttemptForPassage: priorForPassage === 0,
        initialScore: null,
        finalScore: null,
        assistanceLevel: input.assistanceLevel,
        wasUnassistedInitially: input.assistanceLevel === 0,
        createdAt: now(),
        completedAt: null,
        status: "in_progress",
      });
    });
  } else {
    // Update assistance level to max viewed
    const att = await db.assessmentAttempts.get(attemptId);
    if (att && input.assistanceLevel > att.assistanceLevel) {
      await db.assessmentAttempts.update(attemptId, {
        assistanceLevel: input.assistanceLevel,
      });
    }
  }

  const priorAnswers = await db.assessmentAnswers.where("attemptId").equals(attemptId!).toArray();
  const priorQAs = priorAnswers
    .map(
      (a) =>
        `Q: ${a.questionInChinese ?? "(initial)"}\nA: ${a.answerText}\nScore: ${a.assessment?.score ?? "-"}`,
    )
    .join("\n---\n");

  try {
    const result = await requestStructured({
      profile: input.profile,
      schema: understandingAssessmentSchema,
      task: input.questionIndex === 0 ? "assess_initial" : "assess_followup",
      bookId: input.bookId,
      chapterId: input.chapterId,
      signal: input.signal,
      messages: [
        {
          role: "system",
          content: assessmentSystemPrompt(input.learnerLanguage),
        },
        {
          role: "user",
          content: assessmentUserPrompt({
            passage: input.passage,
            contextBefore: input.contextBefore,
            contextAfter: input.contextAfter,
            bookMemoryCompact: memory ? compactMemoryForPrompt(memory) : "",
            learnerAnswer: input.learnerAnswer,
            learnerLanguage: input.learnerLanguage,
            questionIndex: input.questionIndex,
            priorQAs,
          }),
        },
      ],
    });

    if (input.signal?.aborted) {
      throw new DOMException("Aborted", "AbortError");
    }

    const assessment = toAssessment(result.data);
    // Cap questions at 3 follow-ups after initial (questionIndex 0..3)
    if (input.questionIndex >= 3) {
      assessment.shouldContinueQuestioning = false;
      assessment.nextQuestionInChinese = null;
      assessment.nextQuestionInNativeLanguage = null;
    }

    const answerId = createId("ans");
    await db.transaction("rw", db.assessmentAnswers, db.assessmentAttempts, async () => {
      await db.assessmentAnswers.put({
        id: answerId,
        attemptId: attemptId!,
        questionIndex: input.questionIndex,
        questionInChinese: input.questionInChinese ?? null,
        questionInNativeLanguage: input.questionInNativeLanguage ?? null,
        answerText: input.learnerAnswer,
        submittedAt: now(),
        assessment,
      });

      const patch: Partial<AssessmentAttempt> = {
        finalScore: assessment.score,
      };
      if (input.questionIndex === 0) {
        patch.initialScore = assessment.score;
      }
      const stop =
        !assessment.shouldContinueQuestioning || input.questionIndex >= 3;
      if (stop) {
        patch.status = "completed";
        patch.completedAt = now();
      }
      await db.assessmentAttempts.update(attemptId!, patch);
    });

    void queueOrUpdateMemory({
      bookId: input.bookId,
      chapterId: input.chapterId,
      passage: input.passage,
      contextBefore: input.contextBefore,
      contextAfter: input.contextAfter,
      location: input.location,
    });

    const attempt = (await db.assessmentAttempts.get(attemptId!))!;
    return { attempt, assessment, cancelled: false };
  } catch (e) {
    if (isAbortError(e)) {
      const attempt = (await db.assessmentAttempts.get(attemptId!))!;
      return {
        attempt,
        assessment: null as unknown as UnderstandingAssessment,
        cancelled: true,
      };
    }
    throw e;
  }
}

export async function bumpAttemptAssistance(
  attemptId: string,
  level: AssistanceLevel,
): Promise<void> {
  const att = await db.assessmentAttempts.get(attemptId);
  if (!att) return;
  if (level > att.assistanceLevel) {
    await db.assessmentAttempts.update(attemptId, { assistanceLevel: level });
  }
}
