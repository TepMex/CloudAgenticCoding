import { db, getTaskAssignments } from "../db/database";
import { secrets } from "../providers/secrets";
import { ProviderClient, type ProviderClientOptions, type ChatResult } from "../providers/client";
import { assessPrompt, AssessmentSchema, ASSESSMENT_SCHEMA_JSON } from "../providers/prompts";
import { uuid } from "../shared/util";
import type { AssistanceLevel, AssessmentAnswer, AssessmentAttempt, ProviderProfile, UnderstandingAssessment } from "../shared/domain";

export type AssessRequest = {
  bookId: string; annotationId: string; passage: string; context: string; memory: string;
  answer: string; learnerLanguage: string; previousQuestions: { q: string; a: string }[];
  followUpCount: number; assistanceLevel: AssistanceLevel; unassisted: boolean;
};
export type AssessResult = { attempt: AssessmentAttempt; answer: AssessmentAnswer; assessment: UnderstandingAssessment; usage: ChatResult | null };

export async function runAssess(req: AssessRequest): Promise<AssessResult> {
  const assignments = await getTaskAssignments(req.bookId);
  const profileId = assignments?.assessProfileId;
  if (!profileId) throw new Error("No Assess profile assigned.");
  const profile = await db.providerProfiles.get(profileId);
  if (!profile) throw new Error("Assess profile not found.");
  const apiKey = secrets.get(profile.apiKeyReference);
  if (!apiKey) throw new Error("API key missing.");
  const opts = toOpts(profile, apiKey);
  const parts = assessPrompt({ passage: req.passage, context: req.context, memory: req.memory, answer: req.answer, learnerLanguage: req.learnerLanguage, followUpCount: req.followUpCount, previousQuestions: req.previousQuestions });
  const client = new ProviderClient();
  const structured = await client.structured([{ role: "system", content: parts.system }, { role: "user", content: parts.user }], { ...opts, schemaJson: JSON.stringify(ASSESSMENT_SCHEMA_JSON) }, AssessmentSchema, JSON.stringify(ASSESSMENT_SCHEMA_JSON), opts);
  if ("error" in structured) throw new Error("Malformed assessment response. Raw available.");
  const assessment = structured.data as unknown as UnderstandingAssessment;
  const shouldContinue = assessment.shouldContinueQuestioning && req.followUpCount < 3;
  let attempt!: AssessmentAttempt;
  let answer!: AssessmentAnswer;
  await db.transaction("rw", db.assessmentAttempts, db.assessmentAnswers, async () => {
    const existing = await db.assessmentAttempts.where("annotationId").equals(req.annotationId).toArray();
    const active = existing.find((a) => !a.finished);
    if (active) attempt = active;
    else {
      attempt = { id: uuid(), annotationId: req.annotationId, bookId: req.bookId, initialScore: req.followUpCount === 0 ? assessment.score : null, finalScore: null, assistanceLevel: req.assistanceLevel, unassisted: req.unassisted, questionCount: req.followUpCount, finished: false, finishedAt: null, createdAt: Date.now() };
      await db.assessmentAttempts.add(attempt);
    }
    answer = { id: uuid(), attemptId: attempt.id, index: req.followUpCount, questionInChinese: req.previousQuestions.length ? req.previousQuestions[req.previousQuestions.length - 1]?.q ?? null : null, questionInNativeLanguage: null, answerInNativeLanguage: req.answer, assessment, createdAt: Date.now() };
    await db.assessmentAnswers.add(answer);
    if (req.followUpCount === 0) attempt.initialScore = assessment.score;
    attempt.questionCount = req.followUpCount + 1;
    if (!shouldContinue) { attempt.finalScore = assessment.score; attempt.finished = true; attempt.finishedAt = Date.now(); }
    attempt.assistanceLevel = Math.max(attempt.assistanceLevel, req.assistanceLevel) as AssistanceLevel;
    await db.assessmentAttempts.put(attempt);
  });
  await recordUsage(profile.id, "assess", structured.usage);
  return { attempt, answer, assessment, usage: structured.usage };
}

export async function finishAttempt(attemptId: string): Promise<void> {
  const t = await db.assessmentAttempts.get(attemptId);
  if (!t) return;
  if (t.finalScore === null) {
    const answers = await db.assessmentAnswers.where("attemptId").equals(attemptId).toArray();
    const last = answers.sort((a, b) => b.index - a.index)[0];
    t.finalScore = last?.assessment?.score ?? t.initialScore ?? 0;
  }
  t.finished = true; t.finishedAt = Date.now();
  await db.assessmentAttempts.put(t);
}

function toOpts(profile: ProviderProfile, apiKey: string): ProviderClientOptions {
  return { baseUrl: profile.baseUrl, apiKey, model: profile.model, temperature: profile.advanced.temperature, maxOutputTokens: profile.advanced.maxOutputTokens, chatCompletionsPath: profile.advanced.chatCompletionsPath, supportsJsonMode: profile.capabilities?.supportsJsonMode, supportsStructuredOutput: profile.capabilities?.supportsStructuredOutput };
}

async function recordUsage(profileId: string, task: string, result: ChatResult | null): Promise<void> {
  await db.requestUsage.add({ id: uuid(), profileId, task, promptTokens: result?.promptTokens ?? null, completionTokens: result?.completionTokens ?? null, totalTokens: result?.totalTokens ?? null, ok: true, at: Date.now() });
}
