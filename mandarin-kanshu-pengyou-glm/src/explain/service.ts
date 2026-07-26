import { db, getTaskAssignments } from "../db/database";
import { secrets } from "../providers/secrets";
import { ProviderClient, type ProviderClientOptions, type ChatResult } from "../providers/client";
import { simplifyPrompt } from "../providers/prompts";
import { uuid } from "../shared/util";
import type { AssistanceLevel, Annotation, Explanation, ProviderProfile, ReaderLocation } from "../shared/domain";

export type ExplainRequest = {
  bookId: string; location: ReaderLocation; passage: string; manualSelection: string;
  context: string; memory: string; hskLevel: number; learnerLanguage: string;
  parentExplanationId?: string; sourceText?: string; currentLevel?: AssistanceLevel;
};
export type ExplainResult = { explanation: Explanation; usage: ChatResult | null };

export async function runExplain(req: ExplainRequest): Promise<ExplainResult> {
  const assignments = await getTaskAssignments(req.bookId);
  const profileId = assignments?.explainProfileId;
  if (!profileId) throw new Error("No Explain profile assigned.");
  const profile = await db.providerProfiles.get(profileId);
  if (!profile) throw new Error("Explain profile not found.");
  const apiKey = secrets.get(profile.apiKeyReference);
  if (!apiKey) throw new Error("API key missing. Re-enter it in Settings.");
  const currentLevel = req.currentLevel ?? 0;
  const nextLevel = Math.min(3, currentLevel + 1) as 1 | 2 | 3;
  const sourceText = req.sourceText ?? req.passage;
  const opts = toOpts(profile, apiKey);
  const parts = simplifyPrompt({ passage: sourceText, context: req.context, memory: req.memory, hskLevel: req.hskLevel, learnerLanguage: req.learnerLanguage, level: nextLevel });
  const client = new ProviderClient();
  const result = await client.chat([{ role: "system", content: parts.system }, { role: "user", content: parts.user }], opts);
  let explanation!: Explanation;
  await db.transaction("rw", db.annotations, db.explanations, async () => {
    const existing = await findAnnotation(req.bookId, req.location);
    const annotationId = existing?.id ?? uuid();
    if (!existing) {
      const ann: Annotation = { id: annotationId, bookId: req.bookId, location: req.location, passage: req.passage, manualSelection: req.manualSelection, createdAt: Date.now() };
      await db.annotations.add(ann);
    }
    explanation = { id: uuid(), annotationId, bookId: req.bookId, parentExplanationId: req.parentExplanationId, level: nextLevel, sourceText, text: result.text, profileId: profile.id, createdAt: Date.now() };
    await db.explanations.add(explanation);
  });
  await recordUsage(profile.id, "explain", result);
  return { explanation, usage: result };
}

export async function freshExplain(bookId: string, annotationId: string): Promise<ExplainResult> {
  const annotation = await db.annotations.get(annotationId);
  if (!annotation) throw new Error("Annotation not found.");
  const assignments = await getTaskAssignments(bookId);
  const profileId = assignments?.fallbackProfileId || assignments?.explainProfileId;
  if (!profileId) throw new Error("No fallback/explain profile configured.");
  const profile = await db.providerProfiles.get(profileId);
  if (!profile) throw new Error("Profile not found.");
  const apiKey = secrets.get(profile.apiKeyReference);
  if (!apiKey) throw new Error("API key missing.");
  const opts = toOpts(profile, apiKey);
  const parts = simplifyPrompt({ passage: annotation.passage, context: "", memory: "", hskLevel: 4, learnerLanguage: "ru", level: 1 });
  const client = new ProviderClient();
  const result = await client.chat([{ role: "system", content: parts.system }, { role: "user", content: parts.user }], opts);
  const explanation: Explanation = { id: uuid(), annotationId, bookId, parentExplanationId: undefined, level: 1, sourceText: annotation.passage, text: result.text, profileId: profile.id, createdAt: Date.now() };
  await db.explanations.add(explanation);
  await recordUsage(profile.id, "explain", result);
  return { explanation, usage: result };
}

export async function listExplanationsForAnnotation(annotationId: string): Promise<Explanation[]> {
  return db.explanations.where("annotationId").equals(annotationId).toArray();
}

async function findAnnotation(bookId: string, loc: ReaderLocation): Promise<Annotation | undefined> {
  const all = await db.annotations.where("bookId").equals(bookId).toArray();
  return all.find((a) => a.location.spineItemId === loc.spineItemId && a.location.textQuote === loc.textQuote);
}

function toOpts(profile: ProviderProfile, apiKey: string): ProviderClientOptions {
  return { baseUrl: profile.baseUrl, apiKey, model: profile.model, temperature: profile.advanced.temperature, maxOutputTokens: profile.advanced.maxOutputTokens, chatCompletionsPath: profile.advanced.chatCompletionsPath, supportsJsonMode: profile.capabilities?.supportsJsonMode, supportsStructuredOutput: profile.capabilities?.supportsStructuredOutput };
}

async function recordUsage(profileId: string, task: string, result: ChatResult): Promise<void> {
  await db.requestUsage.add({ id: uuid(), profileId, task, promptTokens: result.promptTokens, completionTokens: result.completionTokens, totalTokens: result.totalTokens, ok: true, at: Date.now() });
}
