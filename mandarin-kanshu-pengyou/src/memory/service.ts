import { db } from "../db/database";
import type {
  BookMemory,
  ProviderProfile,
  ReaderLocation,
} from "../shared/domain";
import { emptyBookMemory } from "../shared/domain";
import { createId, now } from "../shared/id";
import {
  memoryPatchSystemPrompt,
  memoryPatchUserPrompt,
  initialMemorySystemPrompt,
  chapterSummarySystemPrompt,
} from "../providers/prompts";
import {
  memoryPatchSchema,
  initialMemorySchema,
} from "../providers/schemas";
import { requestStructured } from "../providers/structured";
import { z } from "zod";
import {
  applyMemoryPatch,
  compactMemoryForPrompt,
  likelyNewNamedEntity,
  memoryToMarkdown,
} from "./merge";
import { quoteUntrusted } from "../providers/untrusted";

const CONSOLIDATE_AFTER = 3;

export async function queueOrUpdateMemory(input: {
  bookId: string;
  chapterId: string;
  passage: string;
  contextBefore: string;
  contextAfter: string;
  location: ReaderLocation;
}): Promise<void> {
  const memory = (await db.bookMemory.get(input.bookId)) ?? emptyBookMemory(input.bookId);
  const urgent = likelyNewNamedEntity(input.passage, memory);

  await db.pendingMemoryCandidates.put({
    id: createId("pmc"),
    bookId: input.bookId,
    chapterId: input.chapterId,
    passage: input.passage,
    contextBefore: input.contextBefore,
    contextAfter: input.contextAfter,
    createdAt: now(),
    likelyNewEntity: urgent,
  });

  const pending = await db.pendingMemoryCandidates.where("bookId").equals(input.bookId).count();
  if (urgent || pending >= CONSOLIDATE_AFTER) {
    await consolidateMemory(input.bookId);
  }
}

export async function getMemoryProfile(): Promise<ProviderProfile | null> {
  const assignments = await db.taskModelAssignments.get("default");
  if (!assignments?.memoryProfileId) return null;
  return (await db.providerProfiles.get(assignments.memoryProfileId)) ?? null;
}

export async function consolidateMemory(bookId: string): Promise<BookMemory | null> {
  const profile = await getMemoryProfile();
  const pending = await db.pendingMemoryCandidates.where("bookId").equals(bookId).toArray();
  if (!pending.length) return (await db.bookMemory.get(bookId)) ?? null;
  if (!profile) {
    // Keep queued until a memory profile exists
    return (await db.bookMemory.get(bookId)) ?? null;
  }

  let memory = (await db.bookMemory.get(bookId)) ?? emptyBookMemory(bookId);
  const batch = pending.slice(0, 6);
  const combinedPassage = batch.map((p) => p.passage).join("\n\n");
  const context = batch
    .map((p) => `${p.contextBefore}\n${p.contextAfter}`)
    .join("\n");
  const chapterId = batch[0]!.chapterId;
  const location = {
    bookId,
    spineItemId: chapterId,
    textQuote: batch[0]!.passage.slice(0, 80),
    prefix: "",
    suffix: "",
  };

  try {
    const result = await requestStructured({
      profile,
      schema: memoryPatchSchema,
      task: "memory_patch",
      bookId,
      chapterId,
      messages: [
        { role: "system", content: memoryPatchSystemPrompt() },
        {
          role: "user",
          content: memoryPatchUserPrompt({
            passage: combinedPassage,
            context,
            existingMemory: compactMemoryForPrompt(memory),
            chapterId,
          }),
        },
      ],
    });

    memory = applyMemoryPatch(memory, result.data, location, chapterId);
    memory.currentChapterId = chapterId;

    await db.transaction(
      "rw",
      db.bookMemory,
      db.memoryRevisions,
      db.pendingMemoryCandidates,
      async () => {
        await db.bookMemory.put(memory);
        await db.memoryRevisions.put({
          id: createId("mrev"),
          bookId,
          revision: memory.revision,
          snapshot: memory,
          createdAt: now(),
          reason: "consolidate",
        });
        // Keep last 8 revisions
        const revs = await db.memoryRevisions.where("bookId").equals(bookId).toArray();
        revs.sort((a, b) => b.revision - a.revision);
        for (const old of revs.slice(8)) {
          await db.memoryRevisions.delete(old.id);
        }
        for (const p of batch) {
          await db.pendingMemoryCandidates.delete(p.id);
        }
      },
    );
    return memory;
  } catch {
    // Do not clear candidates on failure
    return memory;
  }
}

export async function runInitialMemoryExtraction(input: {
  bookId: string;
  sampleText: string;
  location: ReaderLocation;
  profile: ProviderProfile;
  signal?: AbortSignal;
}): Promise<BookMemory> {
  const result = await requestStructured({
    profile: input.profile,
    schema: initialMemorySchema,
    task: "memory_initial",
    bookId: input.bookId,
    chapterId: input.location.spineItemId,
    signal: input.signal,
    messages: [
      { role: "system", content: initialMemorySystemPrompt() },
      {
        role: "user",
        content: quoteUntrusted("Early book sample", input.sampleText),
      },
    ],
  });

  let memory = (await db.bookMemory.get(input.bookId)) ?? emptyBookMemory(input.bookId);
  memory = {
    ...memory,
    synopsis: result.data.synopsis || memory.synopsis,
    entities: [
      ...memory.entities,
      ...result.data.entities.map((e) => ({
        id: createId("ent"),
        type: e.type,
        canonicalName: e.canonicalName,
        aliases: e.aliases,
        description: e.description,
        confidence: e.confidence,
        firstSeenLocation: input.location,
        lastUpdatedLocation: input.location,
      })),
    ],
    updatedAt: now(),
    revision: memory.revision + 1,
  };

  await db.bookMemory.put(memory);
  await db.memoryRevisions.put({
    id: createId("mrev"),
    bookId: input.bookId,
    revision: memory.revision,
    snapshot: memory,
    createdAt: now(),
    reason: "initial",
  });
  return memory;
}

export async function leaveChapterCleanup(input: {
  bookId: string;
  leavingChapterId: string;
  chapterTitle: string;
  profile?: ProviderProfile | null;
}): Promise<void> {
  // Consolidate pending first
  await consolidateMemory(input.bookId);

  let memory = (await db.bookMemory.get(input.bookId)) ?? emptyBookMemory(input.bookId);
  const events = memory.currentChapterEvents.filter(
    (e) => e.chapterId === input.leavingChapterId,
  );

  let summary = events.map((e) => e.summary).join("；");
  let unresolved: string[] = [];

  if (input.profile && events.length) {
    try {
      const schema = z.object({
        summary: z.string(),
        unresolvedThreads: z.array(z.string()).default([]),
      });
      const result = await requestStructured({
        profile: input.profile,
        schema,
        task: "chapter_summary",
        bookId: input.bookId,
        chapterId: input.leavingChapterId,
        messages: [
          { role: "system", content: chapterSummarySystemPrompt() },
          {
            role: "user",
            content: quoteUntrusted(
              "Chapter events",
              events.map((e) => e.summary).join("\n"),
            ),
          },
        ],
      });
      summary = result.data.summary;
      unresolved = result.data.unresolvedThreads;
    } catch {
      // keep heuristic summary
    }
  }

  if (summary) {
    const completed = memory.completedChapterSummaries.filter(
      (c) => c.chapterId !== input.leavingChapterId,
    );
    completed.push({
      chapterId: input.leavingChapterId,
      title: input.chapterTitle,
      summary,
      unresolvedThreads: unresolved,
    });
    memory = {
      ...memory,
      completedChapterSummaries: completed,
      currentChapterEvents: memory.currentChapterEvents.filter(
        (e) => e.chapterId !== input.leavingChapterId,
      ),
      updatedAt: now(),
      revision: memory.revision + 1,
    };
    await db.bookMemory.put(memory);
  }

  // Delete raw technical cache for leaving chapter
  await db.transientChapterCache.delete(`${input.bookId}:${input.leavingChapterId}`);
}

export { memoryToMarkdown, compactMemoryForPrompt };
