import { db } from "../db/database";
import type { BookRecord, ChapterRecord } from "../shared/domain";
import { createId, now } from "../shared/id";
import { parseEpub } from "../reader/epub-adapter/parse";

async function sha256(buffer: ArrayBuffer): Promise<string> {
  const hash = await crypto.subtle.digest("SHA-256", buffer);
  return [...new Uint8Array(hash)].map((b) => b.toString(16).padStart(2, "0")).join("");
}

export async function importEpubFile(file: File | Blob, fileName?: string): Promise<BookRecord> {
  const buffer = await file.arrayBuffer();
  const parsed = await parseEpub(buffer);
  const bookId = createId("book");
  const contentHash = await sha256(buffer);

  const chapterTitles: Record<string, string> = {};
  const chapters: ChapterRecord[] = parsed.spine.map((s) => {
    chapterTitles[s.id] = s.title;
    return {
      id: `${bookId}:${s.id}`,
      bookId,
      spineItemId: s.id,
      href: s.href,
      title: s.title,
      order: s.order,
      html: s.html,
      plainText: s.plainText,
    };
  });

  const book: BookRecord = {
    id: bookId,
    title: parsed.title || fileName || "Untitled",
    author: parsed.author,
    language: parsed.language,
    importedAt: now(),
    lastOpenedAt: now(),
    spineItemIds: parsed.spine.map((s) => s.id),
    chapterTitles,
  };

  await db.transaction("rw", db.books, db.bookFiles, db.chapters, db.bookMemory, async () => {
    await db.books.put(book);
    await db.bookFiles.put({
      bookId,
      blob: new Blob([buffer], { type: "application/epub+zip" }),
      contentHash,
    });
    await db.chapters.bulkPut(chapters);
    await db.bookMemory.put({
      bookId,
      synopsis: "",
      entities: [],
      currentChapterEvents: [],
      completedChapterSummaries: [],
      currentChapterId: parsed.spine[0]?.id ?? null,
      updatedAt: now(),
      revision: 0,
    });
  });

  return book;
}

export async function listBooks(): Promise<BookRecord[]> {
  return db.books.orderBy("lastOpenedAt").reverse().toArray();
}

export async function getBook(bookId: string): Promise<BookRecord | undefined> {
  return db.books.get(bookId);
}

export async function getChapters(bookId: string): Promise<ChapterRecord[]> {
  return db.chapters.where("bookId").equals(bookId).sortBy("order");
}

export async function getChapterBySpine(
  bookId: string,
  spineItemId: string,
): Promise<ChapterRecord | undefined> {
  return db.chapters.get(`${bookId}:${spineItemId}`);
}

export async function deleteBookAndData(bookId: string): Promise<void> {
  await db.transaction(
    "rw",
    [
      db.books,
      db.bookFiles,
      db.chapters,
      db.readingPositions,
      db.annotations,
      db.explanations,
      db.assessmentAttempts,
      db.assessmentAnswers,
      db.bookMemory,
      db.memoryRevisions,
      db.pendingMemoryCandidates,
      db.transientChapterCache,
      db.companionReactions,
      db.readingSessions,
    ],
    async () => {
      const attempts = await db.assessmentAttempts.where("bookId").equals(bookId).toArray();
      const attemptIds = new Set(attempts.map((a) => a.id));
      await db.assessmentAnswers
        .filter((a) => attemptIds.has(a.attemptId))
        .delete();
      await db.assessmentAttempts.where("bookId").equals(bookId).delete();
      await db.explanations.where("bookId").equals(bookId).delete();
      await db.annotations.where("bookId").equals(bookId).delete();
      await db.companionReactions.where("bookId").equals(bookId).delete();
      await db.memoryRevisions.where("bookId").equals(bookId).delete();
      await db.pendingMemoryCandidates.where("bookId").equals(bookId).delete();
      await db.transientChapterCache.where("bookId").equals(bookId).delete();
      await db.readingSessions.where("bookId").equals(bookId).delete();
      await db.bookMemory.delete(bookId);
      await db.readingPositions.delete(bookId);
      await db.chapters.where("bookId").equals(bookId).delete();
      await db.bookFiles.delete(bookId);
      await db.books.delete(bookId);
    },
  );
}

export async function touchBookOpened(bookId: string): Promise<void> {
  await db.books.update(bookId, { lastOpenedAt: now() });
}
