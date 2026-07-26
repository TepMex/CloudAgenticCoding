import { db, getTaskAssignments } from "../db/database";
import { secrets } from "../providers/secrets";
import { ProviderClient, type ProviderClientOptions, type ChatResult } from "../providers/client";
import { companionPrompt } from "../providers/prompts";
import { uuid } from "../shared/util";

export type CompanionResult = { text: string; usage: ChatResult | null };

export async function runCompanion(bookId: string, context: string, memory: string): Promise<CompanionResult> {
  const assignments = await getTaskAssignments(bookId);
  const profileId = assignments?.assessProfileId || assignments?.explainProfileId;
  if (!profileId) throw new Error("No profile assigned.");
  const profile = await db.providerProfiles.get(profileId);
  if (!profile) throw new Error("Profile not found.");
  const apiKey = secrets.get(profile.apiKeyReference);
  if (!apiKey) throw new Error("API key missing.");
  const opts: ProviderClientOptions = { baseUrl: profile.baseUrl, apiKey, model: profile.model, temperature: profile.advanced.temperature ?? 0.8, maxOutputTokens: profile.advanced.maxOutputTokens ?? 300, chatCompletionsPath: profile.advanced.chatCompletionsPath };
  const parts = companionPrompt({ context, memory });
  const client = new ProviderClient();
  const result = await client.chat([{ role: "system", content: parts.system }, { role: "user", content: parts.user }], opts);
  await db.requestUsage.add({ id: uuid(), profileId: profile.id, task: "companion", promptTokens: result.promptTokens, completionTokens: result.completionTokens, totalTokens: result.totalTokens, ok: true, at: Date.now() });
  return { text: result.text, usage: result };
}
