const DB_NAME = "ffmpeg-commands";
const DB_VERSION = 1;
const STORE = "vectors";

type StoredVector = {
  corpusId: string;
  dim: number;
  buffer: ArrayBuffer;
};

function openDb(): Promise<IDBDatabase> {
  return new Promise((resolve, reject) => {
    const request = indexedDB.open(DB_NAME, DB_VERSION);
    request.onupgradeneeded = () => {
      const db = request.result;
      if (!db.objectStoreNames.contains(STORE)) db.createObjectStore(STORE);
    };
    request.onsuccess = () => resolve(request.result);
    request.onerror = () => reject(request.error);
  });
}

export async function readVectorCache(corpusId: string, dim: number): Promise<Map<string, Float32Array>> {
  const db = await openDb();
  const found = new Map<string, Float32Array>();
  await new Promise<void>((resolve, reject) => {
    const tx = db.transaction(STORE, "readonly");
    const store = tx.objectStore(STORE);
    const request = store.openCursor();
    request.onsuccess = () => {
      const cursor = request.result;
      if (!cursor) return;
      const value = cursor.value as StoredVector;
      if (value.corpusId === corpusId && value.dim === dim) {
        found.set(String(cursor.key), new Float32Array(value.buffer));
      }
      cursor.continue();
    };
    tx.oncomplete = () => resolve();
    tx.onerror = () => reject(tx.error);
  });
  db.close();
  return found;
}

export async function writeVectorCache(
  corpusId: string,
  dim: number,
  entries: { id: string; vector: Float32Array }[],
): Promise<void> {
  if (!entries.length) return;
  const db = await openDb();
  await new Promise<void>((resolve, reject) => {
    const tx = db.transaction(STORE, "readwrite");
    const store = tx.objectStore(STORE);
    for (const entry of entries) {
      const copy = entry.vector.buffer.slice(entry.vector.byteOffset, entry.vector.byteOffset + entry.vector.byteLength);
      const value: StoredVector = { corpusId, dim, buffer: copy };
      store.put(value, entry.id);
    }
    tx.oncomplete = () => resolve();
    tx.onerror = () => reject(tx.error);
  });
  db.close();
}
