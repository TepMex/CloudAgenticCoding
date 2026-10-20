export type WordStatus = "repeat" | "learning" | "known";
export type GlossKind = "word" | "idiom" | "grammar";
export type QuestionCategory = "sense" | "words" | "grammar";
export type Verdict = "correct" | "partial" | "attention";
export type Difficulty = "easy" | "ok" | "hard";
export type CoverTone = "gold" | "navy" | "ink" | "paper";

export type Token = { t: string; glossId?: string };

export type Gloss = {
  id: string;
  hanzi: string;
  pinyin: string;
  gloss: string;
  kind: GlossKind;
  example: string;
  exampleGloss: string;
  status: WordStatus;
  lastSeen: string;
};

export type Question = {
  id: string;
  category: QuestionCategory;
  title: string;
  prompt: string;
  kind: "mcq" | "open";
  options?: { key: string; text: string }[];
  answerKey?: string;
  explanation: string;
  examples: string[];
  keywords: string[];
  opinion?: boolean;
};

export type Lesson = {
  id: string;
  bookId: string;
  chapterTitle: string;
  fragmentTitle: string;
  intro: string;
  hints: string[];
  tokens: Token[];
  questions: Question[];
  keyMoments: string[];
};

export type Chapter = {
  id: string;
  title: string;
  status: "done" | "current" | "unread";
  detail: string;
};

export type SessionRow = {
  title: string;
  when: string;
  done: boolean;
};

export type Book = {
  id: string;
  title: string;
  author: string;
  cover: CoverTone;
  status: "reading" | "done";
  progress: number;
  lastSession: string;
  place: string;
  words: number;
  chars: number;
  observations: number;
  chapters: Chapter[];
  recent: SessionRow[];
  custom?: boolean;
};

export type Note = {
  id: string;
  bookId: string;
  kind: "note" | "bookmark";
  title: string;
  body: string;
  meta: string;
};

export type Skill = {
  id: string;
  title: string;
  detail: string;
  status: WordStatus;
};

export type GradedItem = {
  id: string;
  verdict: Verdict;
  headline: string;
  explanation: string;
  examples: string[];
};

export type Review = {
  items: GradedItem[];
  keyMoments?: string[];
  source: "local" | "model";
  warning?: string;
};

export type Attempt = {
  bookId: string;
  lessonId: string;
  phase: 1 | 2 | 3 | 4;
  answers: Record<string, string>;
  difficulty: Difficulty;
  comment: string;
  review: Review | null;
  credited: boolean;
};

export type Settings = {
  apiBaseUrl: string;
  apiKey: string;
  model: string;
  hsk: "HSK 3" | "HSK 4" | "HSK 5" | "HSK 6";
  explainLanguage: "Русский" | "English" | "中文";
  difficulty: "Лёгкая" | "Средняя" | "Высокая";
  depth: "Кратко" | "Обычно" | "Подробно";
  moreComprehension: boolean;
  reviewOld: boolean;
};

export type Route =
  | { name: "library" }
  | { name: "study" }
  | { name: "profile" }
  | { name: "settings" }
  | { name: "knowledge"; tab: "words" | "grammar" | "skills" }
  | { name: "book"; bookId: string; tab: "progress" | "chapters" | "notes" }
  | { name: "lesson"; bookId: string; phase: 1 | 2 | 3 | 4 };

export type AppState = {
  seedVersion: number;
  settings: Settings;
  books: Book[];
  lessons: Lesson[];
  glosses: Gloss[];
  notes: Note[];
  skills: Skill[];
  activity: Record<string, 1 | 2>;
  attempts: Record<string, Attempt>;
  bannerDismissed: boolean;
  route: Route;
};
