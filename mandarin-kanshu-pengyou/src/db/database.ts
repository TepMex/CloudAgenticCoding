import Dexie, { type Table } from "dexie";
import type {
  AnnotationRecord,
  AppSettings,
  AssessmentAnswer,
  AssessmentAttempt,
  BookFileRecord,
  BookMemory,
  BookRecord,
  ChapterRecord,
  CompanionReactionRecord,
  ExplanationRecord,
  MemoryRevision,
  PendingMemoryCandidate,
  ProviderProfile,
  ProviderSecret,
  ReadingPosition,
  ReadingSession,
  RequestUsageRecord,
  TaskModelAssignments,
  TransientChapterCache,
} from "../shared/domain";

export class KanshuDB extends Dexie {
  books!: Table<BookRecord, string>;
  bookFiles!: Table<BookFileRecord, string>;
  chapters!: Table<ChapterRecord, string>;
  readingPositions!: Table<ReadingPosition, string>;
  annotations!: Table<AnnotationRecord, string>;
  explanations!: Table<ExplanationRecord, string>;
  assessmentAttempts!: Table<AssessmentAttempt, string>;
  assessmentAnswers!: Table<AssessmentAnswer, string>;
  providerProfiles!: Table<ProviderProfile, string>;
  providerSecrets!: Table<ProviderSecret, string>;
  taskModelAssignments!: Table<TaskModelAssignments & { id: string }, string>;
  bookMemory!: Table<BookMemory, string>;
  memoryRevisions!: Table<MemoryRevision, string>;
  pendingMemoryCandidates!: Table<PendingMemoryCandidate, string>;
  settings!: Table<AppSettings, string>;
  requestUsage!: Table<RequestUsageRecord, string>;
  transientChapterCache!: Table<TransientChapterCache, string>;
  companionReactions!: Table<CompanionReactionRecord, string>;
  readingSessions!: Table<ReadingSession, string>;

  constructor() {
    super("mandarin-kanshu-pengyou");
    this.version(1).stores({
      books: "id, lastOpenedAt, title",
      bookFiles: "bookId",
      chapters: "id, bookId, spineItemId, order, [bookId+order]",
      readingPositions: "bookId",
      annotations: "id, bookId, kind, createdAt, [bookId+kind]",
      explanations: "id, annotationId, bookId",
      assessmentAttempts: "id, annotationId, bookId, createdAt",
      assessmentAnswers: "id, attemptId",
      providerProfiles: "id, name",
      providerSecrets: "reference",
      taskModelAssignments: "id",
      bookMemory: "bookId",
      memoryRevisions: "id, bookId, revision",
      pendingMemoryCandidates: "id, bookId, chapterId",
      settings: "id",
      requestUsage: "id, profileId, createdAt",
      transientChapterCache: "id, bookId, chapterId",
      companionReactions: "id, annotationId, bookId",
      readingSessions: "id, bookId, startedAt",
    });
  }
}

export const db = new KanshuDB();

export const DEFAULT_SETTINGS: AppSettings = {
  id: "app",
  appearance: "light",
  fontSizePx: 20,
  lineHeight: 1.75,
  contentWidthCh: 42,
  learnerLanguage: "ru",
  hskLevel: 4,
  rememberApiKeys: false,
  companionEnabled: true,
};

export async function ensureDefaults(): Promise<void> {
  const existing = await db.settings.get("app");
  if (!existing) {
    await db.settings.put(DEFAULT_SETTINGS);
  }
  const assignments = await db.taskModelAssignments.get("default");
  if (!assignments) {
    await db.taskModelAssignments.put({
      id: "default",
      explainProfileId: "",
      assessProfileId: "",
      memoryProfileId: "",
    });
  }
}
