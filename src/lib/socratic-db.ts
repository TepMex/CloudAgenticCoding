const DB_NAME = "socratic-reading-agent";
const DB_VERSION = 1;

const STORE_CHAPTERS = "chapters";

export type ChunkBounds = {
  /** Inclusive start index in `chapterText` */
  start: number;
  /** Exclusive end index in `chapterText` (matches pipeline `nextCursor`) */
  end: number;
};

export type StoredChunkRound = {
  question: string;
  correctAnswer: string;
  userAnswer: string;
  passageGroundedAnalysis: string;
  llmOpinion: string | null;
};

export type ChapterPersisted = {
  id: string;
  bookId: string;
  chapterId: string;
  chunks: ChunkBounds[];
  /** One list of completed rounds per chunk index */
  chunkRounds: StoredChunkRound[][];
};

export function makeBookPersistenceId(fileName: string, byteSize: number): string {
  return `${encodeURIComponent(fileName)}:${byteSize}`;
}

export function makeChapterRecordId(bookId: string, chapterId: string): string {
  return `${bookId}::${encodeURIComponent(chapterId)}`;
}

function openDb(): Promise<IDBDatabase> {
  return new Promise((resolve, reject) => {
    const req = indexedDB.open(DB_NAME, DB_VERSION);
    req.onupgradeneeded = () => {
      const db = req.result;
      if (!db.objectStoreNames.contains(STORE_CHAPTERS)) {
        db.createObjectStore(STORE_CHAPTERS, { keyPath: "id" });
      }
    };
    req.onsuccess = () => resolve(req.result);
    req.onerror = () => reject(req.error ?? new Error("Failed to open IndexedDB"));
  });
}

export async function deleteChapterRecord(bookId: string, chapterId: string): Promise<void> {
  const id = makeChapterRecordId(bookId, chapterId);
  const db = await openDb();
  return new Promise((resolve, reject) => {
    const tx = db.transaction(STORE_CHAPTERS, "readwrite");
    tx.objectStore(STORE_CHAPTERS).delete(id);
    tx.oncomplete = () => {
      db.close();
      resolve();
    };
    tx.onerror = () => reject(tx.error ?? new Error("Failed to delete chapter"));
  });
}

export async function loadChapterRecord(bookId: string, chapterId: string): Promise<ChapterPersisted | null> {
  const id = makeChapterRecordId(bookId, chapterId);
  const db = await openDb();
  return new Promise((resolve, reject) => {
    const tx = db.transaction(STORE_CHAPTERS, "readonly");
    const store = tx.objectStore(STORE_CHAPTERS);
    const getReq = store.get(id);
    getReq.onsuccess = () => {
      const v = getReq.result as ChapterPersisted | undefined;
      resolve(v ?? null);
    };
    getReq.onerror = () => reject(getReq.error ?? new Error("Failed to read chapter"));
    tx.oncomplete = () => db.close();
  });
}

async function putChapterRecord(record: ChapterPersisted): Promise<void> {
  const db = await openDb();
  return new Promise((resolve, reject) => {
    const tx = db.transaction(STORE_CHAPTERS, "readwrite");
    tx.objectStore(STORE_CHAPTERS).put(record);
    tx.oncomplete = () => {
      db.close();
      resolve();
    };
    tx.onerror = () => reject(tx.error ?? new Error("Failed to save chapter"));
  });
}

function ensureChunkRoundsForChunks(
  chunks: ChunkBounds[],
  chunkRounds: StoredChunkRound[][],
): StoredChunkRound[][] {
  const next = chunkRounds.slice(0, chunks.length);
  while (next.length < chunks.length) next.push([]);
  return next;
}

/**
 * Store bounds for chunk `index`. Appends when `index === chunks.length`, otherwise replaces
 * that slot and truncates later chunks (for example after Reset session).
 */
export async function saveChunkBounds(
  bookId: string,
  chapterId: string,
  index: number,
  bounds: ChunkBounds,
): Promise<void> {
  const id = makeChapterRecordId(bookId, chapterId);
  const existing = (await loadChapterRecord(bookId, chapterId)) ?? {
    id,
    bookId,
    chapterId,
    chunks: [],
    chunkRounds: [],
  };

  const chunks = [...existing.chunks];
  if (index < chunks.length) {
    chunks[index] = bounds;
    chunks.length = index + 1;
  } else if (index === chunks.length) {
    chunks.push(bounds);
  } else {
    throw new Error(`Invalid chunk index ${index} for ${chunks.length} stored chunks`);
  }

  let chunkRounds = existing.chunkRounds.slice(0, chunks.length);
  chunkRounds = ensureChunkRoundsForChunks(chunks, chunkRounds);

  await putChapterRecord({
    id,
    bookId,
    chapterId,
    chunks,
    chunkRounds,
  });
}

export async function saveCompletedRound(
  bookId: string,
  chapterId: string,
  chunkIndex: number,
  round: StoredChunkRound,
): Promise<void> {
  const id = makeChapterRecordId(bookId, chapterId);
  const existing = await loadChapterRecord(bookId, chapterId);
  if (!existing) {
    await putChapterRecord({
      id,
      bookId,
      chapterId,
      chunks: [],
      chunkRounds: [[round]],
    });
    return;
  }

  const chunkRounds = ensureChunkRoundsForChunks(existing.chunks, existing.chunkRounds);
  const list = (chunkRounds[chunkIndex] ?? []).slice();
  list.push(round);
  chunkRounds[chunkIndex] = list;

  await putChapterRecord({
    ...existing,
    chunkRounds,
  });
}

/** First chunk index with no completed round, or `chunks.length` if all known chunks are done */
export function nextPersistedChunkIndex(record: ChapterPersisted | null): number {
  if (!record || record.chunks.length === 0) return 0;
  for (let i = 0; i < record.chunks.length; i++) {
    const rounds = record.chunkRounds[i];
    if (!rounds || rounds.length === 0) return i;
  }
  return record.chunks.length;
}
