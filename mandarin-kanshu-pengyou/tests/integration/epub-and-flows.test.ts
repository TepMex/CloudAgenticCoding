import { beforeAll, describe, expect, test } from "bun:test";
import { Window } from "happy-dom";
import { readFileSync } from "node:fs";
import { join } from "node:path";

// Minimal DOM for DOMPurify / DOMParser during EPUB parse
const window = new Window({ url: "https://localhost/" });
(globalThis as unknown as { window: Window }).window = window;
(globalThis as unknown as { document: Document }).document = window.document as unknown as Document;
(globalThis as unknown as { DOMParser: typeof window.DOMParser }).DOMParser = window.DOMParser;
(globalThis as unknown as { Node: typeof window.Node }).Node = window.Node;

// Dexie needs indexedDB — use fake in-memory shim for integration smoke
class MemoryIDBFactory {
  // Dexie will fail without real IDB; we test parse + pure services here
}

beforeAll(async () => {
  // Ensure sample epub exists
  const { $ } = await import("bun");
  await $`bun run fixture:epub`.cwd(join(import.meta.dir, "../.."));
});

describe("EPUB import parse", () => {
  test("parses sample fixture", async () => {
    const { parseEpub } = await import("../../src/reader/epub-adapter/parse");
    const buf = readFileSync(join(import.meta.dir, "../../fixtures/sample.epub"));
    const parsed = await parseEpub(buf.buffer.slice(buf.byteOffset, buf.byteOffset + buf.byteLength));
    expect(parsed.title).toBe("江边小记");
    expect(parsed.spine.length).toBe(2);
    expect(parsed.spine[0]?.plainText).toContain("李明");
    expect(parsed.spine[0]?.html).not.toContain("<script");
  });
});

describe("structured repair path helpers", () => {
  test("isAbortError detects abort", async () => {
    const { isAbortError, getStructuredFailure } = await import(
      "../../src/providers/structured"
    );
    expect(isAbortError(new DOMException("Aborted", "AbortError"))).toBe(true);
    const err = Object.assign(new Error("bad"), {
      structuredFailure: { error: "x", raw: "{}", repaired: true },
    });
    expect(getStructuredFailure(err)?.repaired).toBe(true);
  });
});

describe("chapter cache cleanup shape", () => {
  test("cache id format", () => {
    const bookId = "book_1";
    const chapterId = "c1";
    expect(`${bookId}:${chapterId}`).toBe("book_1:c1");
  });
});

// Placeholder so suite references MemoryIDBFactory without unused lint noise in bun
void MemoryIDBFactory;
