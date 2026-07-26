import { db, getTaskAssignments } from "../db/database";
import { secrets } from "../providers/secrets";
import { ProviderClient, type ProviderClientOptions, type ChatResult } from "../providers/client";
import {
  memoryPatchPrompt, initialMemoryPrompt, MemoryPatchSchema, MEMORY_PATCH_SCHEMA_JSON,
  InitialMemorySchema, INITIAL_MEMORY_SCHEMA_JSON, type MemoryPatch, type InitialMemory,
} from "../providers/prompts";
import { uuid } from "../shared/util";
import type { BookMemory, ChapterSummary, ProviderProfile, ReaderLocation } from "../shared/domain";

const MAX_REVISIONS = 10;

export async function getBookMemory(bookId: string): Promise<BookMemory> {
  const existing = await db.bookMemory.get(bookId);
  if (existing) return existing;
  const empty: BookMemory = { bookId, synopsis: "", entities: [], currentChapterEvents: [], completedChapterSummaries: [], updatedAt: Date.now() };
  await db.bookMemory.put(empty);
  return empty;
}

export async function runInitialMemoryExtraction(bookId: string, excerpt: string): Promise<BookMemory> {
  const assignments = await getTaskAssignments(bookId);
  const profileId = assignments?.memoryProfileId;
  if (!profileId) throw new Error("No Memory profile assigned.");
  const profile = await db.providerProfiles.get(profileId);
  if (!profile) throw new Error("Memory profile not found.");
  const apiKey = secrets.get(profile.apiKeyReference);
  if (!apiKey) throw new Error("API key missing.");
  const opts = toOpts(profile, apiKey);
  const parts = initialMemoryPrompt({ excerpt });
  const client = new ProviderClient();
  const structured = await client.structured([{ role: "system", content: parts.system }, { role: "user", content: parts.user }], { ...opts, schemaJson: JSON.stringify(INITIAL_MEMORY_SCHEMA_JSON) }, InitialMemorySchema, JSON.stringify(INITIAL_MEMORY_SCHEMA_JSON), opts);
  if ("error" in structured) throw new Error("Initial memory extraction failed.");
  const data = structured.data as unknown as InitialMemory;
  const memory: BookMemory = {
    bookId, synopsis: data.synopsis || "",
    entities: data.addEntities.map((e) => ({ id: uuid(), type: e.type, canonicalName: e.canonicalName, aliases: e.aliases || [], description: e.description, confidence: e.confidence, firstSeenLocation: emptyLoc(bookId), lastUpdatedLocation: emptyLoc(bookId) })),
    currentChapterEvents: [], completedChapterSummaries: [], updatedAt: Date.now(),
  };
  await db.transaction("rw", db.bookMemory, db.memoryRevisions, async () => { await saveRevision(bookId, await getBookMemory(bookId)); await db.bookMemory.put(memory); });
  await recordUsage(profile.id, "initialMemory", structured.usage);
  return memory;
}

export async function applyMemoryPatch(bookId: string, patch: MemoryPatch, sourceLocation: ReaderLocation, chapterId: string): Promise<BookMemory> {
  const current = await getBookMemory(bookId);
  const entities = [...current.entities];
  for (const add of patch.addEntities || []) {
    const existing = entities.find((e) => e.canonicalName === add.canonicalName || (add.aliases || []).some((a) => e.aliases.includes(a)));
    if (!existing) entities.push({ id: uuid(), type: add.type, canonicalName: add.canonicalName, aliases: add.aliases || [], description: add.description, confidence: add.confidence, firstSeenLocation: sourceLocation, lastUpdatedLocation: sourceLocation });
  }
  for (const upd of patch.updateEntities || []) {
    const e = entities.find((x) => x.canonicalName === upd.canonicalName || x.aliases.includes(upd.canonicalName));
    if (e) { if (upd.description) e.description = upd.description; for (const a of upd.addAliases || []) if (!e.aliases.includes(a)) e.aliases.push(a); if (upd.confidence) e.confidence = upd.confidence; e.lastUpdatedLocation = sourceLocation; }
  }
  const events = [...current.currentChapterEvents];
  for (const ev of patch.addEvents || []) events.push({ id: uuid(), summary: ev.summary, participants: ev.participants || [], locationName: ev.locationName ?? null, confidence: ev.confidence, sourceLocation, chapterId });
  const completed = [...current.completedChapterSummaries];
  if (patch.completedChapterSummary) {
    const cs = patch.completedChapterSummary;
    const idx = completed.findIndex((c) => c.chapterId === cs.chapterId);
    const entry: ChapterSummary = { chapterId: cs.chapterId, title: cs.title, summary: cs.summary, unresolvedThreads: cs.unresolvedThreads || [] };
    if (idx >= 0) completed[idx] = entry; else completed.push(entry);
  }
  const next: BookMemory = { bookId, synopsis: patch.synopsis ?? current.synopsis, entities, currentChapterEvents: events, completedChapterSummaries: completed, updatedAt: Date.now() };
  await db.transaction("rw", db.bookMemory, db.memoryRevisions, async () => { await saveRevision(bookId, current); await db.bookMemory.put(next); });
  return next;
}

export async function summarizeChapter(bookId: string, chapterId: string, chapterTitle: string): Promise<void> {
  const memory = await getBookMemory(bookId);
  if (memory.currentChapterEvents.length === 0) return;
  const summary = memory.currentChapterEvents.map((e) => e.summary).join("；").slice(0, 500);
  const completed: ChapterSummary = { chapterId, title: chapterTitle, summary: summary || "(no events recorded)", unresolvedThreads: [] };
  const idx = memory.completedChapterSummaries.findIndex((c) => c.chapterId === chapterId);
  if (idx >= 0) memory.completedChapterSummaries[idx] = completed; else memory.completedChapterSummaries.push(completed);
  memory.currentChapterEvents = [];
  memory.updatedAt = Date.now();
  await db.transaction("rw", db.bookMemory, db.memoryRevisions, async () => { await saveRevision(bookId, await getBookMemory(bookId)); await db.bookMemory.put(memory); });
}

export function shouldUpdateMemoryImmediately(passage: string, existingMemory: BookMemory): { immediate: boolean; reason: string } {
  const known = new Set<string>();
  for (const e of existingMemory.entities) { known.add(e.canonicalName); for (const a of e.aliases) known.add(a); }
  const cjkRuns = passage.match(/[\u4e00-\u9fff]{2,4}/g) || [];
  for (const run of cjkRuns) {
    if (!known.has(run) && run.length >= 2 && !COMMON_WORDS.has(run)) return { immediate: true, reason: "new term" };
  }
  return { immediate: false, reason: "queue" };
}

const COMMON_WORDS = new Set(["他们", "我们", "你们", "这个", "那个", "什么", "怎么", "可以", "已经", "现在", "一个", "没有", "不是", "知道", "起来", "过来", "回去", "出来", "东西", "时候", "地方", "这样", "那样", "因为", "所以", "如果", "但是", "不过", "还是", "或者", "其实", "只是", "不要", "不能", "不会", "这是", "那是", "他的", "她的", "我的", "你的", "自己", "一下", "一直", "走了", "看了", "说着", "想道", "笑道", "道了", "看着", "想着", "听着", "说着", "做着", "拿着", "放着", "坐在", "站在", "走在", "跑着", "飞着", "活着", "死了", "来了", "去了", "回了", "到了", "开了", "关了", "住了", "停了", "动了", "变了", "成了", "好了", "完了", "罢了", "的话", "的话", "的话", "这里", "那里", "哪里", "为什么", "怎么办", "怎么样", "是不是", "有没有", "能不能", "可不可以", "然后", "后来", "马上", "立刻", "突然", "慢慢", "渐渐", "终于", "果然", "竟然", "居然", "也许", "或许", "应该", "可能", "一定", "必须", "需要", "希望", "喜欢", "觉得", "认为", "以为", "发现", "觉得", "看着", "听着", "想着", "说着", "笑着", "哭着", "走着", "站着", "坐着", "躺着", "拿着", "放着", "开着", "关着", "打着", "写着", "读着", "吃着", "喝着", "睡着", "醒着", "活着", "死了", "来了", "去了", "回了", "到了", "走了", "跑了", "飞了", "开了", "关了", "住了", "停了", "动了", "变了", "成了", "好了", "完了", "罢了"]);

export async function runMemoryPatch(bookId: string, context: string, sourceLocation: ReaderLocation, chapterId: string): Promise<BookMemory | null> {
  const assignments = await getTaskAssignments(bookId);
  const profileId = assignments?.memoryProfileId;
  if (!profileId) return null;
  const profile = await db.providerProfiles.get(profileId);
  if (!profile) return null;
  const apiKey = secrets.get(profile.apiKeyReference);
  if (!apiKey) return null;
  const memory = await getBookMemory(bookId);
  const opts = toOpts(profile, apiKey);
  const parts = memoryPatchPrompt({ context, existingMemory: memoryToCompactString(memory) });
  const client = new ProviderClient();
  const structured = await client.structured([{ role: "system", content: parts.system }, { role: "user", content: parts.user }], { ...opts, schemaJson: JSON.stringify(MEMORY_PATCH_SCHEMA_JSON) }, MemoryPatchSchema, JSON.stringify(MEMORY_PATCH_SCHEMA_JSON), opts);
  if ("error" in structured) return null;
  const patch = structured.data as unknown as MemoryPatch;
  const next = await applyMemoryPatch(bookId, patch, sourceLocation, chapterId);
  await recordUsage(profile.id, "memoryPatch", structured.usage);
  return next;
}

export function memoryToCompactString(m: BookMemory): string {
  const lines: string[] = [];
  if (m.synopsis) lines.push("Synopsis: " + m.synopsis);
  if (m.entities.length) { lines.push("Entities:"); for (const e of m.entities) lines.push("  [" + e.type + "] " + e.canonicalName + (e.aliases.length ? " (" + e.aliases.join(", ") + ")" : "") + " — " + e.description + " [" + e.confidence + "]"); }
  if (m.currentChapterEvents.length) { lines.push("Current chapter events:"); for (const ev of m.currentChapterEvents) lines.push("  - " + ev.summary + " [" + ev.confidence + "]"); }
  if (m.completedChapterSummaries.length) { lines.push("Completed chapters:"); for (const c of m.completedChapterSummaries) lines.push("  - " + c.title + ": " + c.summary); }
  return lines.join("\n");
}

export function memoryToDebugMarkdown(m: BookMemory): string {
  const lines: string[] = ["# Book memory", ""];
  lines.push("**Synopsis:** " + (m.synopsis || "_(empty)_"), "");
  lines.push("## Entities (" + m.entities.length + ")", "");
  for (const e of m.entities) { lines.push("- **" + e.canonicalName + "** (" + e.type + ", _" + e.confidence + "_)"); if (e.aliases.length) lines.push("  - aliases: " + e.aliases.join(", ")); lines.push("  - " + e.description); }
  lines.push("", "## Current chapter events (" + m.currentChapterEvents.length + ")", "");
  for (const ev of m.currentChapterEvents) lines.push("- " + ev.summary + " [" + ev.confidence + "]");
  lines.push("", "## Completed chapter summaries (" + m.completedChapterSummaries.length + ")", "");
  for (const c of m.completedChapterSummaries) { lines.push("### " + c.title); lines.push(c.summary); if (c.unresolvedThreads.length) lines.push("_Unresolved:_ " + c.unresolvedThreads.join("; ")); }
  return lines.join("\n");
}

async function saveRevision(bookId: string, snapshot: BookMemory): Promise<void> {
  const count = await db.memoryRevisions.where("bookId").equals(bookId).count();
  await db.memoryRevisions.add({ id: uuid(), bookId, revision: count + 1, snapshot, createdAt: Date.now() });
  const all = await db.memoryRevisions.where("bookId").equals(bookId).toArray();
  if (all.length > MAX_REVISIONS) {
    const old = all.sort((a, b) => a.revision - b.revision).slice(0, all.length - MAX_REVISIONS);
    await db.memoryRevisions.bulkDelete(old.map((r) => r.id));
  }
}

function emptyLoc(bookId: string): ReaderLocation { return { bookId, spineItemId: "", textQuote: "", prefix: "", suffix: "" }; }

function toOpts(profile: ProviderProfile, apiKey: string): ProviderClientOptions {
  return { baseUrl: profile.baseUrl, apiKey, model: profile.model, temperature: profile.advanced.temperature, maxOutputTokens: profile.advanced.maxOutputTokens, chatCompletionsPath: profile.advanced.chatCompletionsPath, supportsJsonMode: profile.capabilities?.supportsJsonMode, supportsStructuredOutput: profile.capabilities?.supportsStructuredOutput };
}

async function recordUsage(profileId: string, task: string, result: ChatResult | null): Promise<void> {
  await db.requestUsage.add({ id: uuid(), profileId, task, promptTokens: result?.promptTokens ?? null, completionTokens: result?.completionTokens ?? null, totalTokens: result?.totalTokens ?? null, ok: true, at: Date.now() });
}
