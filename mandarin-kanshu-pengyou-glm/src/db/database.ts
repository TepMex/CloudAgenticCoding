import Dexie, { type EntityTable, type Table } from "dexie";
import type {
  Annotation, AssessmentAnswer, AssessmentAttempt, BookFile, BookMemory, ChapterRecord,
  Explanation, LocalBook, MemoryRevision, PendingMemoryCandidate, ProviderProfile,
  ProviderSecret, ReadingPosition, RequestUsageRecord, Settings, TaskModelAssignments,
  TransientChapterCacheEntry,
} from "../shared/domain";
import { DEFAULT_SETTINGS } from "../shared/domain";

export class ReadingDb extends Dexie {
  books!: EntityTable<LocalBook, "id">;
  bookFiles!: EntityTable<BookFile, "bookId">;
  chapters!: EntityTable<ChapterRecord, "id">;
  readingPositions!: EntityTable<ReadingPosition, "bookId">;
  annotations!: EntityTable<Annotation, "id">;
  explanations!: EntityTable<Explanation, "id">;
  assessmentAttempts!: EntityTable<AssessmentAttempt, "id">;
  assessmentAnswers!: EntityTable<AssessmentAnswer, "id">;
  providerProfiles!: EntityTable<ProviderProfile, "id">;
  providerSecrets!: EntityTable<ProviderSecret, "id">;
  taskModelAssignments!: Table<TaskModelAssignments, string>;
  bookMemory!: EntityTable<BookMemory, "bookId">;
  memoryRevisions!: EntityTable<MemoryRevision, "id">;
  pendingMemoryCandidates!: EntityTable<PendingMemoryCandidate, "id">;
  settings!: EntityTable<{ key: string; value: unknown }, "key">;
  requestUsage!: EntityTable<RequestUsageRecord, "id">;
  transientChapterCache!: EntityTable<TransientChapterCacheEntry, "id">;

  constructor() {
    super("mandarin-kanshu-pengyou-glm");
    this.version(1).stores({
      books: "id, addedAt, lastOpenedAt",
      bookFiles: "bookId",
      chapters: "id, bookId, index",
      readingPositions: "bookId",
      annotations: "id, bookId",
      explanations: "id, annotationId, bookId, level",
      assessmentAttempts: "id, annotationId, bookId, createdAt",
      assessmentAnswers: "id, attemptId, createdAt",
      providerProfiles: "id",
      providerSecrets: "id, endpointHint",
      taskModelAssignments: "bookId",
      bookMemory: "bookId",
      memoryRevisions: "id, bookId, revision",
      pendingMemoryCandidates: "id, bookId, createdAt",
      settings: "key",
      requestUsage: "id, profileId, at",
      transientChapterCache: "id, bookId, chapterId, createdAt",
    });
  }
}

export const db = new ReadingDb();

export async function getSettings(): Promise<Settings> {
  const row = await db.settings.get("app");
  return { ...DEFAULT_SETTINGS, ...((row?.value ?? {}) as Partial<Settings>) };
}

export async function saveSettings(patch: Partial<Settings>): Promise<void> {
  const current = await getSettings();
  await db.settings.put({ key: "app", value: { ...current, ...patch } });
}

export async function getTaskAssignments(bookId: string): Promise<TaskModelAssignments | undefined> {
  const a = await db.taskModelAssignments.get(bookId);
  if (a) return a;
  return db.taskModelAssignments.get("_global");
}

export async function saveTaskAssignments(bookId: string, a: TaskModelAssignments): Promise<void> {
  await db.taskModelAssignments.put({ ...a, bookId });
}

export async function annotationsForSpineItem(bookId: string, spineItemId: string): Promise<Annotation[]> {
  const all = await db.annotations.where("bookId").equals(bookId).toArray();
  return all.filter((a) => a.location.spineItemId === spineItemId);
}

export async function deleteBook(bookId: string): Promise<void> {
  await db.transaction("rw", [
    db.books, db.bookFiles, db.chapters, db.readingPositions, db.annotations, db.explanations,
    db.assessmentAttempts, db.assessmentAnswers, db.bookMemory, db.memoryRevisions,
    db.pendingMemoryCandidates, db.taskModelAssignments, db.transientChapterCache,
  ], async () => {
    const annIds = (await db.annotations.where("bookId").equals(bookId).toArray()).map((a) => a.id);
    await db.explanations.where("bookId").equals(bookId).delete();
    const attemptIds = (await db.assessmentAttempts.where("bookId").equals(bookId).toArray()).map((a) => a.id);
    if (attemptIds.length) await db.assessmentAnswers.where("attemptId").anyOf(attemptIds).delete();
    await db.assessmentAttempts.where("bookId").equals(bookId).delete();
    await db.annotations.bulkDelete(annIds);
    await db.bookMemory.delete(bookId);
    await db.memoryRevisions.where("bookId").equals(bookId).delete();
    await db.pendingMemoryCandidates.where("bookId").equals(bookId).delete();
    await db.taskModelAssignments.delete(bookId);
    await db.transientChapterCache.where("bookId").equals(bookId).delete();
    await db.chapters.where("bookId").equals(bookId).delete();
    await db.readingPositions.delete(bookId);
    await db.bookFiles.delete(bookId);
    await db.books.delete(bookId);
  });
}

export async function clearTransientChapterCache(bookId: string, chapterId: string): Promise<void> {
  const entries = await db.transientChapterCache.where("bookId").equals(bookId).toArray();
  await db.transientChapterCache.bulkDelete(entries.filter((e) => e.chapterId === chapterId).map((e) => e.id));
}
