import type { UnderstandingAssessment, ReaderLocation } from "../../shared/domain";

export type ActiveCard =
  | { kind: "explain"; annotationId: string; location: ReaderLocation; passage: string; manualSelection: string; level: number; sourceText: string; text: string; loading: boolean; error: string | null; collapsed: boolean }
  | { kind: "understand"; annotationId: string; location: ReaderLocation; passage: string; manualSelection: string; attemptId?: string; followUpCount: number; lastAssessment: UnderstandingAssessment | null; loading: boolean; error: string | null; collapsed: boolean }
  | { kind: "companion"; text: string; loading: boolean; collapsed: boolean };
