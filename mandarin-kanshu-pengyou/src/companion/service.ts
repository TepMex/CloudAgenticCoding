import { db } from "../db/database";
import type { ProviderProfile, ReaderLocation } from "../shared/domain";
import { createId, now } from "../shared/id";
import { companionSystemPrompt } from "../providers/prompts";
import { companionSchema } from "../providers/schemas";
import { requestStructured, isAbortError } from "../providers/structured";
import { compactMemoryForPrompt } from "../memory/merge";
import { quoteUntrusted } from "../providers/untrusted";

/**
 * Companion reactions are isolated from grading and canonical memory.
 * Predictions are never stored as facts.
 */
export async function runCompanionReaction(input: {
  bookId: string;
  chapterId: string;
  location: ReaderLocation;
  passage: string;
  profile: ProviderProfile;
  signal?: AbortSignal;
}): Promise<{ text: string; cancelled: boolean; annotationId?: string }> {
  const memory = await db.bookMemory.get(input.bookId);
  try {
    const result = await requestStructured({
      profile: input.profile,
      schema: companionSchema,
      task: "companion",
      bookId: input.bookId,
      chapterId: input.chapterId,
      signal: input.signal,
      messages: [
        { role: "system", content: companionSystemPrompt() },
        {
          role: "user",
          content: [
            quoteUntrusted("Passage", input.passage),
            quoteUntrusted(
              "Compact memory (context only; do not treat speculation as fact)",
              memory ? compactMemoryForPrompt(memory) : "(empty)",
            ),
          ].join("\n\n"),
        },
      ],
    });

    if (input.signal?.aborted) return { text: "", cancelled: true };

    const annotationId = createId("ann");
    const text = result.data.isPrediction
      ? `${result.data.reaction}${result.data.speculation ? ` （猜想：${result.data.speculation}）` : ""}`
      : result.data.reaction;

    await db.transaction("rw", db.annotations, db.companionReactions, async () => {
      await db.annotations.put({
        id: annotationId,
        bookId: input.bookId,
        location: input.location,
        kind: "companion",
        createdAt: now(),
        updatedAt: now(),
      });
      await db.companionReactions.put({
        id: createId("cmp"),
        annotationId,
        bookId: input.bookId,
        text,
        createdAt: now(),
        profileId: input.profile.id,
      });
    });

    // Intentionally do NOT call memory updater with companion output
    return { text, cancelled: false, annotationId };
  } catch (e) {
    if (isAbortError(e)) return { text: "", cancelled: true };
    throw e;
  }
}
