/** Domain types for Mandarin Kanshu Pengyou — source of truth for app data shapes. */

export type Confidence = "certain" | "probable" | "uncertain";

export type AssistanceLevel = 0 | 1 | 2 | 3;

export type AssessmentScore = 0 | 1 | 2 | 3 | 4;

export type AssessmentLabel =
  | "missed"
  | "emerging"
  | "main_idea"
  | "strong"
  | "deep";

export const ASSESSMENT_LABELS: Record<AssessmentScore, AssessmentLabel> = {
  0: "missed",
  1: "emerging",
  2: "main_idea",
  3: "strong",
  4: "deep",
};

export const ASSESSMENT_FRIENDLY: Record<AssessmentLabel, string> = {
  missed: "Missed",
  emerging: "Emerging",
  main_idea: "Main idea",
  strong: "Strong",
  deep: "Deep",
};

export type ReaderLocation = {
  bookId: string;
  spineItemId: string;
  epubCfi?: string;
  textQuote: string;
  prefix: string;
  suffix: string;
  approximateProgress?: number;
};

export type BookRecord = {
  id: string;
  title: string;
  author: string;
  language: string;
  coverDataUrl?: string;
  importedAt: number;
  lastOpenedAt: number;
  spineItemIds: string[];
  chapterTitles: Record<string, string>;
};

export type BookFileRecord = {
  bookId: string;
  blob: Blob;
  contentHash: string;
};

export type ChapterRecord = {
  id: string;
  bookId: string;
  spineItemId: string;
  href: string;
  title: string;
  order: number;
  html: string;
  plainText: string;
};

export type ReadingPosition = {
  bookId: string;
  spineItemId: string;
  scrollRatio: number;
  updatedAt: number;
};

export type AnnotationKind = "explain" | "understand" | "companion";

export type AnnotationRecord = {
  id: string;
  bookId: string;
  location: ReaderLocation;
  kind: AnnotationKind;
  createdAt: number;
  updatedAt: number;
  collapsed?: boolean;
};

export type ExplanationLevel = {
  level: 1 | 2 | 3;
  text: string;
  createdAt: number;
  profileId: string;
};

export type ExplanationRecord = {
  id: string;
  annotationId: string;
  bookId: string;
  originalPassage: string;
  manualSelection: string;
  levels: ExplanationLevel[];
  highestLevelViewed: AssistanceLevel;
};

export type UnderstandingAssessment = {
  score: AssessmentScore;
  label: AssessmentLabel;
  coreMeaning: "incorrect" | "partial" | "correct";
  importantDetails: "missed" | "partial" | "correct";
  toneAndImplication: "not_detected" | "partial" | "strong";
  feedbackInNativeLanguage: string;
  correctedUnderstandingInNativeLanguage: string;
  keyClueInChinese: string;
  ambiguityNote: string | null;
  nextQuestionInChinese: string | null;
  nextQuestionInNativeLanguage: string | null;
  shouldContinueQuestioning: boolean;
};

export type AssessmentAnswer = {
  id: string;
  attemptId: string;
  questionIndex: number;
  questionInChinese: string | null;
  questionInNativeLanguage: string | null;
  answerText: string;
  submittedAt: number;
  assessment: UnderstandingAssessment | null;
};

export type AssessmentAttempt = {
  id: string;
  annotationId: string;
  bookId: string;
  location: ReaderLocation;
  passage: string;
  isFirstAttemptForPassage: boolean;
  initialScore: AssessmentScore | null;
  finalScore: AssessmentScore | null;
  assistanceLevel: AssistanceLevel;
  wasUnassistedInitially: boolean;
  createdAt: number;
  completedAt: number | null;
  status: "in_progress" | "completed" | "abandoned";
};

export type MemoryEntity = {
  id: string;
  type: "character" | "place" | "organization" | "term";
  canonicalName: string;
  aliases: string[];
  description: string;
  confidence: Confidence;
  firstSeenLocation: ReaderLocation;
  lastUpdatedLocation: ReaderLocation;
};

export type MemoryEvent = {
  id: string;
  summary: string;
  participants: string[];
  locationName: string | null;
  confidence: Confidence;
  sourceLocation: ReaderLocation;
  chapterId: string;
};

export type ChapterSummary = {
  chapterId: string;
  title: string;
  summary: string;
  unresolvedThreads: string[];
};

export type BookMemory = {
  bookId: string;
  synopsis: string;
  entities: MemoryEntity[];
  currentChapterEvents: MemoryEvent[];
  completedChapterSummaries: ChapterSummary[];
  currentChapterId: string | null;
  updatedAt: number;
  revision: number;
};

export type MemoryRevision = {
  id: string;
  bookId: string;
  revision: number;
  snapshot: BookMemory;
  createdAt: number;
  reason: string;
};

export type PendingMemoryCandidate = {
  id: string;
  bookId: string;
  chapterId: string;
  passage: string;
  contextBefore: string;
  contextAfter: string;
  createdAt: number;
  likelyNewEntity: boolean;
};

export type ProviderCapabilities = {
  corsOk: boolean;
  chatCompletionsOk: boolean;
  authOk: boolean;
  textCompletionOk: boolean;
  structuredOutputOk: boolean;
  jsonTextOk: boolean;
  cancellationOk: boolean | null;
  tokenUsageAvailable: boolean;
  testedAt: number;
  lastError?: string;
};

export type ProviderProfile = {
  id: string;
  name: string;
  baseUrl: string;
  apiKeyReference: string;
  model: string;
  advanced: {
    temperature?: number;
    maxOutputTokens?: number;
    chatCompletionsPath?: string;
  };
  capabilities?: ProviderCapabilities;
};

export type TaskModelAssignments = {
  explainProfileId: string;
  assessProfileId: string;
  memoryProfileId: string;
  fallbackProfileId?: string;
};

export type Appearance = "light" | "dark";

export type AppSettings = {
  id: "app";
  appearance: Appearance;
  fontSizePx: number;
  lineHeight: number;
  contentWidthCh: number;
  learnerLanguage: string;
  hskLevel: number;
  rememberApiKeys: boolean;
  companionEnabled: boolean;
};

export type RequestUsageRecord = {
  id: string;
  profileId: string;
  task: string;
  promptTokens?: number;
  completionTokens?: number;
  totalTokens?: number;
  usageAvailable: boolean;
  createdAt: number;
  bookId?: string;
};

export type TransientChapterCache = {
  id: string;
  bookId: string;
  chapterId: string;
  rawResponses: { requestId: string; raw: string; createdAt: number }[];
  updatedAt: number;
};

export type CompanionReactionRecord = {
  id: string;
  annotationId: string;
  bookId: string;
  text: string;
  createdAt: number;
  profileId: string;
};

export type ReadingSession = {
  id: string;
  bookId: string;
  startedAt: number;
  endedAt: number | null;
};

export type ProviderSecret = {
  reference: string;
  /** Present only when rememberApiKeys is true */
  apiKey?: string;
  baseUrlFingerprint: string;
  updatedAt: number;
};

export function emptyBookMemory(bookId: string): BookMemory {
  return {
    bookId,
    synopsis: "",
    entities: [],
    currentChapterEvents: [],
    completedChapterSummaries: [],
    currentChapterId: null,
    updatedAt: Date.now(),
    revision: 0,
  };
}

export function scoreToLabel(score: AssessmentScore): AssessmentLabel {
  return ASSESSMENT_LABELS[score];
}
