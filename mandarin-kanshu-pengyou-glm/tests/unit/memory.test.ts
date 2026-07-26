import { test, expect } from "bun:test";
import { shouldUpdateMemoryImmediately, memoryToCompactString, memoryToDebugMarkdown } from "../../src/memory/service";
import type { BookMemory } from "../../src/shared/domain";

const empty: BookMemory = {
  bookId: "b", synopsis: "", entities: [], currentChapterEvents: [], completedChapterSummaries: [], updatedAt: 0,
};

test("shouldUpdateMemoryImmediately triggers on unknown term", () => {
  expect(shouldUpdateMemoryImmediately("方源走进了春秋蝉的房间。", empty).immediate).toBe(true);
});

test("shouldUpdateMemoryImmediately false on common word", () => {
  // "他们" is in the common-words stoplist, so it should NOT trigger.
  expect(shouldUpdateMemoryImmediately("他们。", empty).immediate).toBe(false);
});

test("shouldUpdateMemoryImmediately false when only known terms appear", () => {
  const mem: BookMemory = {
    ...empty,
    entities: [{ id: "1", type: "term", canonicalName: "方源", aliases: [], description: "", confidence: "certain", firstSeenLocation: empty as any, lastUpdatedLocation: empty as any }],
  };
  // "方源" is known; the heuristic only triggers on unknown 2-4 char runs.
  // "他" is 1 char (below threshold), so this passage has no unknown terms.
  expect(shouldUpdateMemoryImmediately("方源。", mem).immediate).toBe(false);
});

test("memoryToCompactString includes synopsis and entities", () => {
  const mem: BookMemory = {
    ...empty,
    synopsis: "A story",
    entities: [{ id: "1", type: "character", canonicalName: "方源", aliases: ["方"], description: "protagonist", confidence: "certain", firstSeenLocation: empty as any, lastUpdatedLocation: empty as any }],
  };
  const s = memoryToCompactString(mem);
  expect(s).toContain("Synopsis: A story");
  expect(s).toContain("[character] 方源");
  expect(s).toContain("[certain]");
});

test("memoryToDebugMarkdown renders markdown", () => {
  const s = memoryToDebugMarkdown(empty);
  expect(s).toContain("# Book memory");
  expect(s).toContain("_(empty)_");
});