import { z } from "zod";
import type { UnderstandingAssessment } from "../shared/domain";

export const simplificationSchema = z.object({
  simplifiedChinese: z.string().min(1),
  terminologyNotes: z.array(z.string()).optional().default([]),
  uncertaintyNotes: z.array(z.string()).optional().default([]),
});

export type SimplificationResult = z.infer<typeof simplificationSchema>;

export const understandingAssessmentSchema = z.object({
  score: z.union([
    z.literal(0),
    z.literal(1),
    z.literal(2),
    z.literal(3),
    z.literal(4),
  ]),
  label: z.enum(["missed", "emerging", "main_idea", "strong", "deep"]),
  coreMeaning: z.enum(["incorrect", "partial", "correct"]),
  importantDetails: z.enum(["missed", "partial", "correct"]),
  toneAndImplication: z.enum(["not_detected", "partial", "strong"]),
  feedbackInNativeLanguage: z.string(),
  correctedUnderstandingInNativeLanguage: z.string(),
  keyClueInChinese: z.string(),
  ambiguityNote: z.string().nullable(),
  nextQuestionInChinese: z.string().nullable(),
  nextQuestionInNativeLanguage: z.string().nullable(),
  shouldContinueQuestioning: z.boolean(),
});

export type UnderstandingAssessmentParsed = z.infer<
  typeof understandingAssessmentSchema
>;

export function toAssessment(
  parsed: UnderstandingAssessmentParsed,
): UnderstandingAssessment {
  return parsed;
}

export const memoryEntityPatchSchema = z.object({
  op: z.enum(["upsert", "remove"]),
  id: z.string().optional(),
  type: z.enum(["character", "place", "organization", "term"]).optional(),
  canonicalName: z.string().optional(),
  aliases: z.array(z.string()).optional(),
  description: z.string().optional(),
  confidence: z.enum(["certain", "probable", "uncertain"]).optional(),
});

export const memoryEventPatchSchema = z.object({
  op: z.enum(["add", "remove"]),
  id: z.string().optional(),
  summary: z.string().optional(),
  participants: z.array(z.string()).optional(),
  locationName: z.string().nullable().optional(),
  confidence: z.enum(["certain", "probable", "uncertain"]).optional(),
});

export const memoryPatchSchema = z.object({
  synopsisUpdate: z.string().nullable().optional(),
  entities: z.array(memoryEntityPatchSchema).default([]),
  events: z.array(memoryEventPatchSchema).default([]),
  unresolvedThreads: z.array(z.string()).optional(),
});

export type MemoryPatch = z.infer<typeof memoryPatchSchema>;

export const initialMemorySchema = z.object({
  synopsis: z.string(),
  entities: z.array(
    z.object({
      type: z.enum(["character", "place", "organization", "term"]),
      canonicalName: z.string(),
      aliases: z.array(z.string()).default([]),
      description: z.string(),
      confidence: z.enum(["certain", "probable", "uncertain"]),
    }),
  ),
  unresolvedThreads: z.array(z.string()).default([]),
});

export const companionSchema = z.object({
  reaction: z.string().min(1),
  speculation: z.string().nullable().optional(),
  isPrediction: z.boolean().default(false),
});

export const repairWrapperSchema = z.object({
  repaired: z.unknown(),
});
