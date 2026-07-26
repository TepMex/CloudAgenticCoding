import type { z } from "zod";
import type { ProviderProfile } from "../shared/domain";
import { chatCompletion, type ChatMessage } from "./client";
import { tryParseWithSchema } from "./json";
import { repairSystemPrompt } from "./prompts";
import { db } from "../db/database";
import { createId, now } from "../shared/id";

export type StructuredRequest<T> = {
  profile: ProviderProfile;
  messages: ChatMessage[];
  schema: z.ZodType<T>;
  task: string;
  bookId?: string;
  chapterId?: string;
  signal?: AbortSignal;
  preferJsonMode?: boolean;
};

export type StructuredSuccess<T> = {
  data: T;
  raw: string;
  repaired: boolean;
};

export type StructuredFailure = {
  error: string;
  raw: string;
  repaired: boolean;
};

/**
 * Run a structured LLM call with one automatic repair attempt.
 * Does not stream. Honours AbortSignal — caller must not persist on abort.
 */
export async function requestStructured<T>(
  req: StructuredRequest<T>,
): Promise<StructuredSuccess<T>> {
  const first = await chatCompletion({
    profile: req.profile,
    messages: req.messages,
    signal: req.signal,
    jsonMode: req.preferJsonMode ?? true,
    task: req.task,
    bookId: req.bookId,
  });

  if (req.signal?.aborted) throw new DOMException("Aborted", "AbortError");

  await cacheRaw(req.bookId, req.chapterId, first.content);

  const parsed = tryParseWithSchema(req.schema, first.content);
  if (parsed.ok) {
    return { data: parsed.data, raw: first.content, repaired: false };
  }

  const repairMessages: ChatMessage[] = [
    { role: "system", content: repairSystemPrompt() },
    {
      role: "user",
      content: `Expected schema description: validate as JSON for task ${req.task}.\nMalformed response:\n${first.content}\nValidation error:\n${parsed.error}`,
    },
  ];

  const repaired = await chatCompletion({
    profile: req.profile,
    messages: repairMessages,
    signal: req.signal,
    jsonMode: true,
    task: `${req.task}_repair`,
    bookId: req.bookId,
  });

  if (req.signal?.aborted) throw new DOMException("Aborted", "AbortError");
  await cacheRaw(req.bookId, req.chapterId, repaired.content);

  const parsed2 = tryParseWithSchema(req.schema, repaired.content);
  if (parsed2.ok) {
    return { data: parsed2.data, raw: repaired.content, repaired: true };
  }

  const failure: StructuredFailure = {
    error: parsed2.error,
    raw: repaired.content,
    repaired: true,
  };
  throw Object.assign(new Error(`Malformed model output after repair: ${failure.error}`), {
    structuredFailure: failure,
  });
}

async function cacheRaw(
  bookId: string | undefined,
  chapterId: string | undefined,
  raw: string,
): Promise<void> {
  if (!bookId || !chapterId) return;
  const id = `${bookId}:${chapterId}`;
  const existing = await db.transientChapterCache.get(id);
  const entry = {
    requestId: createId("raw"),
    raw,
    createdAt: now(),
  };
  if (existing) {
    await db.transientChapterCache.put({
      ...existing,
      rawResponses: [...existing.rawResponses, entry].slice(-20),
      updatedAt: now(),
    });
  } else {
    await db.transientChapterCache.put({
      id,
      bookId,
      chapterId,
      rawResponses: [entry],
      updatedAt: now(),
    });
  }
}

export function isAbortError(e: unknown): boolean {
  return (
    (e instanceof DOMException && e.name === "AbortError") ||
    (e instanceof Error && (/abort/i.test(e.message) || e.name === "AbortError"))
  );
}

export function getStructuredFailure(e: unknown): StructuredFailure | null {
  if (e && typeof e === "object" && "structuredFailure" in e) {
    return (e as { structuredFailure: StructuredFailure }).structuredFailure;
  }
  return null;
}
