export type AssistanceLevel = 0 | 1 | 2 | 3;
export type Confidence = "certain" | "probable" | "uncertain";

export type ReaderLocation = {
  bookId: string; spineItemId: string; epubCfi?: string;
  textQuote: string; prefix: string; suffix: string; approximateProgress?: number;
};

export type MemoryEntity = {
  id: string; type: "character" | "place" | "organization" | "term";
  canonicalName: string; aliases: string[]; description: string; confidence: Confidence;
  firstSeenLocation: ReaderLocation; lastUpdatedLocation: ReaderLocation;
};

export type MemoryEvent = {
  id: string; summary: string; participants: string[]; locationName: string | null;
  confidence: Confidence; sourceLocation: ReaderLocation; chapterId: string;
};

export type ChapterSummary = { chapterId: string; title: string; summary: string; unresolvedThreads: string[] };

export type BookMemory = {
  bookId: string; synopsis: string; entities: MemoryEntity[];
  currentChapterEvents: MemoryEvent[]; completedChapterSummaries: ChapterSummary[]; updatedAt: number;
};

export type LocalBook = {
  id: string; title: string; author: string; fileName: string; mimeType: string;
  size: number; coverDataUrl?: string; addedAt: number; lastOpenedAt: number;
};

export type BookFile = { bookId: string; blob: Blob };

export type ChapterRecord = { id: string; bookId: string; index: number; href: string; label: string; textCache?: string };

export type ReadingPosition = {
  bookId: string; spineItemId: string; epubCfi?: string;
  textQuote: string; prefix: string; suffix: string; approximateProgress: number; updatedAt: number;
};

export type Annotation = { id: string; bookId: string; location: ReaderLocation; passage: string; manualSelection: string; createdAt: number };

export type Explanation = {
  id: string; annotationId: string; bookId: string; parentExplanationId?: string;
  level: AssistanceLevel; sourceText: string; text: string; profileId: string; createdAt: number;
};

export type AssessmentScore = 0 | 1 | 2 | 3 | 4;
export type AssessmentLabel = "missed" | "emerging" | "main_idea" | "strong" | "deep";

export type UnderstandingAssessment = {
  score: AssessmentScore; label: AssessmentLabel;
  coreMeaning: "incorrect" | "partial" | "correct";
  importantDetails: "missed" | "partial" | "correct";
  toneAndImplication: "not_detected" | "partial" | "strong";
  feedbackInNativeLanguage: string; correctedUnderstandingInNativeLanguage: string;
  keyClueInChinese: string; ambiguityNote: string | null;
  nextQuestionInChinese: string | null; nextQuestionInNativeLanguage: string | null;
  shouldContinueQuestioning: boolean;
};

export type AssessmentAnswer = {
  id: string; attemptId: string; index: number;
  questionInChinese: string | null; questionInNativeLanguage: string | null;
  answerInNativeLanguage: string; assessment: UnderstandingAssessment | null; createdAt: number;
};

export type AssessmentAttempt = {
  id: string; annotationId: string; bookId: string;
  initialScore: AssessmentScore | null; finalScore: AssessmentScore | null;
  assistanceLevel: AssistanceLevel; unassisted: boolean;
  questionCount: number; finished: boolean; finishedAt: number | null; createdAt: number;
};

export type ProviderCapabilities = {
  supportsStructuredOutput: boolean; supportsJsonMode: boolean; supportsTokenUsage: boolean;
  testedAt: number; ok: boolean; notes: string;
};

export type ProviderProfile = {
  id: string; name: string; baseUrl: string; apiKeyReference: string; model: string;
  advanced: { temperature?: number; maxOutputTokens?: number; chatCompletionsPath?: string };
  capabilities?: ProviderCapabilities;
};

export type TaskModelAssignments = { bookId: string; explainProfileId: string; assessProfileId: string; memoryProfileId: string; fallbackProfileId?: string };

export type ProviderSecret = { id: string; endpointHint: string; persisted: boolean; createdAt: number };

export type RequestUsageRecord = {
  id: string; profileId: string; task: string;
  promptTokens: number | null; completionTokens: number | null; totalTokens: number | null;
  ok: boolean; at: number;
};

export type Settings = {
  theme: "light" | "dark"; fontSize: number; lineHeight: number; contentWidth: number;
  hskLevel: number; learnerLanguage: string; rememberApiKeys: boolean; reduceMotion: boolean;
};

export type PendingMemoryCandidate = { id: string; bookId: string; annotationId: string; context: string; createdAt: number };
export type MemoryRevision = { id: string; bookId: string; revision: number; snapshot: BookMemory; createdAt: number };
export type TransientChapterCacheEntry = { id: string; bookId: string; chapterId: string; task: string; raw: string; createdAt: number };

export const DEFAULT_SETTINGS: Settings = {
  theme: "light", fontSize: 19, lineHeight: 1.9, contentWidth: 720,
  hskLevel: 4, learnerLanguage: "ru", rememberApiKeys: false, reduceMotion: false,
};

export const SCORE_LABELS: Record<AssessmentScore, string> = { 0: "Missed", 1: "Emerging", 2: "Main idea", 3: "Strong", 4: "Deep" };
export const SCORE_LABEL_KEYS: Record<AssessmentScore, AssessmentLabel> = { 0: "missed", 1: "emerging", 2: "main_idea", 3: "strong", 4: "deep" };

export function labelToScore(label: AssessmentLabel): AssessmentScore {
  return ({ missed: 0, emerging: 1, main_idea: 2, strong: 3, deep: 4 } as const)[label];
}
