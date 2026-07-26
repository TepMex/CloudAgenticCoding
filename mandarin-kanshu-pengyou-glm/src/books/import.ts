import { db } from "../db/database";
import { createEpubAdapter, type EpubRendererAdapter } from "../reader/epub-adapter";
import { uuid } from "../shared/util";
import type { LocalBook, ChapterRecord } from "../shared/domain";

export async function importEpub(file: File): Promise<string> {
  const adapter = await createEpubAdapter();
  await adapter.open(file);
  const meta = adapter.metadata();
  const chapters = adapter.chapters();
  const id = uuid();
  const chapterRecords: ChapterRecord[] = chapters.map((c) => ({
    id: `${id}:${c.id}`, bookId: id, index: c.index, href: c.href, label: c.label,
  }));
  await db.transaction("rw", db.books, db.bookFiles, db.chapters, async () => {
    await db.books.add({
      id, title: meta.title || file.name.replace(/\.epub$/i, ""), author: meta.author,
      fileName: file.name, mimeType: file.type || "application/epub+zip",
      size: file.size, coverDataUrl: meta.coverDataUrl, addedAt: Date.now(), lastOpenedAt: Date.now(),
    });
    await db.bookFiles.add({ bookId: id, blob: file });
    await db.chapters.bulkAdd(chapterRecords);
  });
  adapter.destroy();
  return id;
}

export async function listBooks(): Promise<LocalBook[]> {
  return db.books.orderBy("lastOpenedAt").reverse().toArray();
}

export async function getBookFile(bookId: string): Promise<Blob | undefined> {
  const row = await db.bookFiles.get(bookId);
  return row?.blob;
}

export async function getChapters(bookId: string): Promise<ChapterRecord[]> {
  const rows = await db.chapters.where("bookId").equals(bookId).toArray();
  return rows.sort((a, b) => a.index - b.index);
}

export async function openAdapterForBook(bookId: string): Promise<{ adapter: EpubRendererAdapter; chapters: ChapterRecord[] }> {
  const blob = await getBookFile(bookId);
  if (!blob) throw new Error("Book file not found");
  const adapter = await createEpubAdapter();
  await adapter.open(blob);
  await db.books.update(bookId, { lastOpenedAt: Date.now() });
  const chapters = await getChapters(bookId);
  return { adapter, chapters };
}
