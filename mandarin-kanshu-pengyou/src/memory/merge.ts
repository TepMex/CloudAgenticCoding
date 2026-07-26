import type {
  BookMemory,
  Confidence,
  MemoryEntity,
  MemoryEvent,
  ReaderLocation,
} from "../shared/domain";
import type { MemoryPatch } from "../providers/schemas";
import { createId, now } from "../shared/id";

const CONFIDENCE_RANK: Record<Confidence, number> = {
  uncertain: 0,
  probable: 1,
  certain: 2,
};

export function mergeConfidence(a: Confidence, b: Confidence): Confidence {
  return CONFIDENCE_RANK[a] >= CONFIDENCE_RANK[b] ? a : b;
}

export function compactMemoryForPrompt(memory: BookMemory, maxChars = 3500): string {
  const lines: string[] = [];
  if (memory.synopsis) lines.push(`Synopsis: ${memory.synopsis}`);
  for (const e of memory.entities.slice(0, 40)) {
    lines.push(
      `- [${e.type}/${e.confidence}] ${e.canonicalName}` +
        (e.aliases.length ? ` (aka ${e.aliases.join(", ")})` : "") +
        `: ${e.description}`,
    );
  }
  const chapterEvents = memory.currentChapterEvents.slice(-12);
  if (chapterEvents.length) {
    lines.push("Current chapter events:");
    for (const ev of chapterEvents) {
      lines.push(`  • (${ev.confidence}) ${ev.summary}`);
    }
  }
  const recentSummaries = memory.completedChapterSummaries.slice(-5);
  for (const s of recentSummaries) {
    lines.push(`Chapter ${s.title}: ${s.summary}`);
  }
  const text = lines.join("\n");
  return text.length > maxChars ? text.slice(0, maxChars) + "\n…" : text;
}

export function memoryToMarkdown(memory: BookMemory): string {
  const lines: string[] = [
    `# Book memory (rev ${memory.revision})`,
    "",
    "## Synopsis",
    memory.synopsis || "_empty_",
    "",
    "## Entities",
  ];
  for (const e of memory.entities) {
    lines.push(
      `- **${e.canonicalName}** _${e.type}_ [${e.confidence}]` +
        (e.aliases.length ? ` aliases: ${e.aliases.join(", ")}` : ""),
    );
    lines.push(`  ${e.description}`);
  }
  lines.push("", "## Current chapter events");
  for (const ev of memory.currentChapterEvents) {
    lines.push(`- [${ev.confidence}] ${ev.summary}`);
  }
  lines.push("", "## Completed chapters");
  for (const s of memory.completedChapterSummaries) {
    lines.push(`### ${s.title}`);
    lines.push(s.summary);
    if (s.unresolvedThreads.length) {
      lines.push("Unresolved:");
      for (const t of s.unresolvedThreads) lines.push(`- ${t}`);
    }
  }
  return lines.join("\n");
}

export function applyMemoryPatch(
  memory: BookMemory,
  patch: MemoryPatch,
  location: ReaderLocation,
  chapterId: string,
): BookMemory {
  const entities = [...memory.entities];
  const events = [...memory.currentChapterEvents];

  for (const ep of patch.entities) {
    if (ep.op === "remove") {
      const idx = entities.findIndex(
        (e) => e.id === ep.id || e.canonicalName === ep.canonicalName,
      );
      if (idx >= 0) entities.splice(idx, 1);
      continue;
    }
    if (!ep.canonicalName && !ep.id) continue;
    const existingIdx = entities.findIndex(
      (e) =>
        (ep.id && e.id === ep.id) ||
        (ep.canonicalName &&
          (e.canonicalName === ep.canonicalName ||
            e.aliases.includes(ep.canonicalName))),
    );
    if (existingIdx >= 0) {
      const cur = entities[existingIdx]!;
      entities[existingIdx] = {
        ...cur,
        type: ep.type ?? cur.type,
        canonicalName: ep.canonicalName ?? cur.canonicalName,
        aliases: unique([
          ...cur.aliases,
          ...(ep.aliases ?? []),
          ...(ep.canonicalName && ep.canonicalName !== cur.canonicalName
            ? [cur.canonicalName]
            : []),
        ]),
        description: ep.description ?? cur.description,
        confidence: ep.confidence
          ? mergeConfidence(cur.confidence, ep.confidence)
          : cur.confidence,
        lastUpdatedLocation: location,
      };
    } else if (ep.canonicalName && ep.type && ep.description && ep.confidence) {
      const entity: MemoryEntity = {
        id: ep.id ?? createId("ent"),
        type: ep.type,
        canonicalName: ep.canonicalName,
        aliases: ep.aliases ?? [],
        description: ep.description,
        confidence: ep.confidence,
        firstSeenLocation: location,
        lastUpdatedLocation: location,
      };
      entities.push(entity);
    }
  }

  for (const ev of patch.events) {
    if (ev.op === "remove") {
      const idx = events.findIndex((e) => e.id === ev.id);
      if (idx >= 0) events.splice(idx, 1);
      continue;
    }
    if (!ev.summary) continue;
    const event: MemoryEvent = {
      id: ev.id ?? createId("evt"),
      summary: ev.summary,
      participants: ev.participants ?? [],
      locationName: ev.locationName ?? null,
      confidence: ev.confidence ?? "probable",
      sourceLocation: location,
      chapterId,
    };
    events.push(event);
  }

  let synopsis = memory.synopsis;
  if (patch.synopsisUpdate) {
    synopsis = patch.synopsisUpdate;
  }

  // Attach unresolved threads onto current chapter summary slot if present
  const completed = [...memory.completedChapterSummaries];
  if (patch.unresolvedThreads?.length) {
    const last = completed[completed.length - 1];
    if (last && last.chapterId === chapterId) {
      completed[completed.length - 1] = {
        ...last,
        unresolvedThreads: unique([
          ...last.unresolvedThreads,
          ...patch.unresolvedThreads,
        ]),
      };
    }
  }

  return {
    ...memory,
    synopsis,
    entities: entities.slice(0, 200),
    currentChapterEvents: events.slice(-80),
    completedChapterSummaries: completed,
    updatedAt: now(),
    revision: memory.revision + 1,
  };
}

function unique(items: string[]): string[] {
  return [...new Set(items.filter(Boolean))];
}

/** Heuristic: passage likely introduces a new named entity (CJK proper-ish). */
export function likelyNewNamedEntity(passage: string, memory: BookMemory): boolean {
  const known = new Set(
    memory.entities.flatMap((e) => [e.canonicalName, ...e.aliases]),
  );
  // Sequences of 2–4 CJK chars that look like names (very rough)
  const candidates = passage.match(/[\u4e00-\u9fff]{2,4}/g) ?? [];
  for (const c of candidates) {
    if (!known.has(c) && /[们的了在是不]/.test(c) === false) {
      // Prefer title-case-like: if surrounded by verbs etc. — keep simple
      if (c.length >= 2) return true;
    }
  }
  return false;
}
