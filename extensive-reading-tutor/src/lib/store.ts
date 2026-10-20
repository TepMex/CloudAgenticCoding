import { gradeLocal } from "./grade.ts";
import { defaultSettings } from "./settings.ts";
import { SEED_VERSION, seedActivity, seedBooks, seedGlosses, seedLessons, seedNotes, seedSkills } from "./seed.ts";
import type { AppState, Attempt, Book, Difficulty, Gloss, Lesson, Note, Review, Route, Settings, Skill } from "./types.ts";
import { passageOf } from "./text.ts";

export const STORAGE_KEY = "extensive-reading-tutor-v1";

export function createInitialState(route: Route = { name: "library" }): AppState {
  return {
    seedVersion: SEED_VERSION,
    settings: defaultSettings,
    books: seedBooks,
    lessons: seedLessons,
    glosses: seedGlosses,
    notes: seedNotes,
    skills: seedSkills,
    activity: seedActivity,
    attempts: {},
    bannerDismissed: false,
    route,
  };
}

export function emptyAttempt(bookId: string, lessonId: string): Attempt {
  return {
    bookId,
    lessonId,
    phase: 1,
    answers: {},
    difficulty: "ok",
    comment: "",
    review: null,
    credited: false,
  };
}

function todayISO(): string {
  const now = new Date();
  const month = String(now.getMonth() + 1).padStart(2, "0");
  const day = String(now.getDate()).padStart(2, "0");
  return `${now.getFullYear()}-${month}-${day}`;
}

function withGloss(glosses: Gloss[], gloss: Gloss): Gloss[] {
  const index = glosses.findIndex(item => item.id === gloss.id || item.hanzi === gloss.hanzi);
  if (index < 0) return [...glosses, gloss];
  const next = glosses.slice();
  const prev = glosses[index];
  next[index] = { ...gloss, status: prev?.status ?? gloss.status };
  return next;
}

function patchTokens(lesson: Lesson, gloss: Gloss): Lesson {
  let changed = false;
  const tokens = lesson.tokens.flatMap(token => {
    if (token.glossId || token.t !== gloss.hanzi) return [token];
    changed = true;
    return [{ t: gloss.hanzi, glossId: gloss.id }];
  });
  return changed ? { ...lesson, tokens } : lesson;
}

export type Action =
  | { type: "go"; route: Route }
  | { type: "ensure-attempt"; bookId: string }
  | { type: "open-lesson"; bookId: string }
  | { type: "answer"; bookId: string; questionId: string; value: string }
  | { type: "phase"; bookId: string; phase: 1 | 2 | 3 | 4 }
  | { type: "difficulty"; bookId: string; difficulty: Difficulty }
  | { type: "comment"; bookId: string; comment: string }
  | { type: "review"; bookId: string; review: Review }
  | { type: "reset-attempt"; bookId: string }
  | { type: "settings"; settings: Settings }
  | { type: "dismiss-banner" }
  | { type: "add-note"; note: Note }
  | { type: "toggle-gloss"; glossId: string }
  | { type: "cycle-skill"; skillId: string }
  | { type: "upsert-gloss"; gloss: Gloss }
  | { type: "add-book"; book: Book; lesson: Lesson; glosses: Gloss[] }
  | { type: "replace-lesson"; lesson: Lesson; glosses: Gloss[] }
  | { type: "reset" };

const STATUS_CYCLE: Gloss["status"][] = ["repeat", "learning", "known"];

function creditBook(book: Book, lesson: Lesson): Book {
  const chars = passageOf(lesson.tokens).length;
  return {
    ...book,
    progress: book.status === "done" ? 100 : Math.min(96, book.progress + 4),
    lastSession: `${lesson.chapterTitle} · Сегодня`,
    place: lesson.chapterTitle,
    words: book.words + 5,
    chars: book.chars + chars,
    observations: book.observations + lesson.keyMoments.length,
    recent: [
      { title: `${lesson.chapterTitle} · ${lesson.fragmentTitle}`, when: "Сегодня · занятие", done: true },
      ...book.recent.filter(row => row.title !== `${lesson.chapterTitle} · ${lesson.fragmentTitle}`),
    ].slice(0, 4),
  };
}

export function reducer(state: AppState, action: Action): AppState {
  switch (action.type) {
    case "go":
      return { ...state, route: action.route };
    case "ensure-attempt": {
      if (state.attempts[action.bookId]) return state;
      const lesson = state.lessons.find(item => item.bookId === action.bookId);
      if (!lesson) return state;
      return {
        ...state,
        attempts: { ...state.attempts, [action.bookId]: emptyAttempt(action.bookId, lesson.id) },
      };
    }
    case "open-lesson": {
      const lesson = state.lessons.find(item => item.bookId === action.bookId);
      if (!lesson) return state;
      const attempt = state.attempts[action.bookId] ?? emptyAttempt(action.bookId, lesson.id);
      return {
        ...state,
        attempts: { ...state.attempts, [action.bookId]: attempt },
        route: { name: "lesson", bookId: action.bookId, phase: attempt.phase },
      };
    }
    case "answer":
    case "phase":
    case "difficulty":
    case "comment": {
      const attempt = state.attempts[action.bookId];
      if (!attempt) return state;
      const next: Attempt = { ...attempt };
      if (action.type === "answer") next.answers = { ...attempt.answers, [action.questionId]: action.value };
      if (action.type === "phase") next.phase = action.phase;
      if (action.type === "difficulty") next.difficulty = action.difficulty;
      if (action.type === "comment") next.comment = action.comment;
      const route = action.type === "phase" ? { name: "lesson" as const, bookId: action.bookId, phase: action.phase } : state.route;
      return { ...state, attempts: { ...state.attempts, [action.bookId]: next }, route };
    }
    case "review": {
      const attempt = state.attempts[action.bookId];
      const lesson = state.lessons.find(item => item.bookId === action.bookId);
      if (!attempt || !lesson) return state;
      const credited = attempt.credited;
      const books = credited
        ? state.books
        : state.books.map(book => (book.id === action.bookId ? creditBook(book, lesson) : book));
      const activity = credited ? state.activity : { ...state.activity, [todayISO()]: 2 as const };
      return {
        ...state,
        books,
        activity,
        attempts: {
          ...state.attempts,
          [action.bookId]: { ...attempt, review: action.review, phase: 4, credited: true },
        },
        route: { name: "lesson", bookId: action.bookId, phase: 4 },
      };
    }
    case "reset-attempt": {
      const attempt = state.attempts[action.bookId];
      const lesson = state.lessons.find(item => item.bookId === action.bookId);
      if (!attempt || !lesson) return state;
      return {
        ...state,
        attempts: {
          ...state.attempts,
          [action.bookId]: { ...emptyAttempt(action.bookId, lesson.id), credited: attempt.credited },
        },
        route: { name: "lesson", bookId: action.bookId, phase: 1 },
      };
    }
    case "settings":
      return { ...state, settings: action.settings };
    case "dismiss-banner":
      return { ...state, bannerDismissed: true };
    case "add-note":
      return { ...state, notes: [action.note, ...state.notes] };
    case "toggle-gloss": {
      return {
        ...state,
        glosses: state.glosses.map(gloss => {
          if (gloss.id !== action.glossId) return gloss;
          const index = STATUS_CYCLE.indexOf(gloss.status);
          const status = STATUS_CYCLE[(index + 1) % STATUS_CYCLE.length] ?? "learning";
          return { ...gloss, status };
        }),
      };
    }
    case "cycle-skill":
      return {
        ...state,
        skills: state.skills.map(skill => {
          if (skill.id !== action.skillId) return skill;
          const index = STATUS_CYCLE.indexOf(skill.status);
          const status = STATUS_CYCLE[(index + 1) % STATUS_CYCLE.length] ?? "learning";
          return { ...skill, status };
        }),
      };
    case "upsert-gloss": {
      const glosses = withGloss(state.glosses, action.gloss);
      return { ...state, glosses, lessons: state.lessons.map(lesson => patchTokens(lesson, action.gloss)) };
    }
    case "add-book":
      return {
        ...state,
        books: [action.book, ...state.books],
        lessons: [...state.lessons.filter(lesson => lesson.bookId !== action.book.id), action.lesson],
        glosses: action.glosses.reduce(withGloss, state.glosses),
        attempts: { ...state.attempts, [action.book.id]: emptyAttempt(action.book.id, action.lesson.id) },
        route: { name: "lesson", bookId: action.book.id, phase: 1 },
      };
    case "replace-lesson":
      return {
        ...state,
        lessons: state.lessons.map(lesson => (lesson.bookId === action.lesson.bookId ? action.lesson : lesson)),
        glosses: action.glosses.reduce(withGloss, state.glosses),
        attempts: {
          ...state.attempts,
          [action.lesson.bookId]: emptyAttempt(action.lesson.bookId, action.lesson.id),
        },
        route: { name: "lesson", bookId: action.lesson.bookId, phase: 1 },
      };
    case "reset":
      return createInitialState({ name: "settings" });
    default:
      return state;
  }
}

function isSettings(value: unknown): value is Settings {
  if (!value || typeof value !== "object") return false;
  const row = value as Settings;
  return typeof row.apiBaseUrl === "string" && typeof row.model === "string" && typeof row.hsk === "string";
}

export function rehydrate(raw: unknown, route: Route): AppState {
  const initial = createInitialState(route);
  if (!raw || typeof raw !== "object") return initial;
  const saved = raw as Partial<AppState>;
  const settings = isSettings(saved.settings) ? { ...defaultSettings, ...saved.settings } : initial.settings;
  if (saved.seedVersion !== SEED_VERSION || !Array.isArray(saved.books) || !Array.isArray(saved.lessons)) {
    return { ...initial, settings };
  }
  return {
    ...initial,
    settings,
    books: saved.books as Book[],
    lessons: saved.lessons as Lesson[],
    glosses: Array.isArray(saved.glosses) ? (saved.glosses as Gloss[]) : initial.glosses,
    notes: Array.isArray(saved.notes) ? (saved.notes as Note[]) : initial.notes,
    skills: Array.isArray(saved.skills) ? (saved.skills as Skill[]) : initial.skills,
    activity: saved.activity && typeof saved.activity === "object" ? (saved.activity as AppState["activity"]) : initial.activity,
    attempts: saved.attempts && typeof saved.attempts === "object" ? (saved.attempts as AppState["attempts"]) : {},
    bannerDismissed: Boolean(saved.bannerDismissed),
    route,
  };
}

export function localReview(lesson: Lesson, answers: Record<string, string>, warning?: string): Review {
  return { items: gradeLocal(lesson, answers), source: "local", warning };
}
