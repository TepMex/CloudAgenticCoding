import { createContext, useContext, useEffect, useReducer, useRef, useState, type ReactNode } from "react";
import { generateLesson, gradeWithModel, lookupGloss, pingProvider } from "./lib/api.ts";
import { buildOfflineLesson } from "./lib/lesson.ts";
import { endpointReady } from "./lib/settings.ts";
import { newId } from "./lib/seed.ts";
import { hashToRoute, routeToHash } from "./lib/route.ts";
import { localReview, reducer, rehydrate, STORAGE_KEY, type Action } from "./lib/store.ts";
import type { AppState, Book, CoverTone, Difficulty, Gloss, Note, Route } from "./lib/types.ts";

const TONES: CoverTone[] = ["gold", "navy", "ink", "paper"];

type Lookup = { hanzi: string; sentence: string };

type Api = {
  state: AppState;
  busy: string | null;
  toast: string | null;
  word: Gloss | null;
  lookup: Lookup | null;
  go: (route: Route) => void;
  openLesson: (bookId: string) => void;
  openWord: (id: string) => void;
  openLookup: (hanzi: string, sentence: string) => void;
  closeWord: () => void;
  setAnswer: (bookId: string, questionId: string, value: string) => void;
  setPhase: (bookId: string, phase: 1 | 2 | 3 | 4) => void;
  setDifficulty: (bookId: string, difficulty: Difficulty) => void;
  setComment: (bookId: string, comment: string) => void;
  submitReview: (bookId: string) => Promise<void>;
  resetAttempt: (bookId: string) => void;
  saveSettings: (settings: AppState["settings"]) => void;
  testConnection: () => Promise<string>;
  dismissBanner: () => void;
  addNote: (note: Omit<Note, "id">) => void;
  toggleGloss: (id: string) => void;
  cycleSkill: (id: string) => void;
  askWord: (hanzi: string, sentence: string) => Promise<void>;
  addBook: (input: { title: string; author: string; text: string }) => Promise<void>;
  resetData: () => void;
  speak: (text: string) => void;
  toastMessage: (message: string) => void;
};

const Ctx = createContext<Api | null>(null);

function loadInitial(): AppState {
  const route = hashToRoute(typeof location === "undefined" ? "" : location.hash);
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    return rehydrate(raw ? JSON.parse(raw) : null, route);
  } catch {
    return rehydrate(null, route);
  }
}

export function AppProvider({ children }: { children: ReactNode }) {
  const [state, dispatch] = useReducer(reducer, undefined, loadInitial);
  const [busy, setBusy] = useState<string | null>(null);
  const [toast, setToast] = useState<string | null>(null);
  const [wordId, setWordId] = useState<string | null>(null);
  const [lookup, setLookup] = useState<Lookup | null>(null);
  const skipPush = useRef(false);
  const hashReady = useRef(false);

  useEffect(() => {
    try {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(state));
    } catch {
      /* private mode / quota */
    }
  }, [state]);

  useEffect(() => {
    if (!toast) return;
    const timer = setTimeout(() => setToast(null), 3200);
    return () => clearTimeout(timer);
  }, [toast]);

  useEffect(() => {
    const onPop = () => {
      skipPush.current = true;
      dispatch({ type: "go", route: hashToRoute(location.hash) });
    };
    window.addEventListener("popstate", onPop);
    return () => window.removeEventListener("popstate", onPop);
  }, []);

  useEffect(() => {
    const next = routeToHash(state.route);
    const current = location.hash.replace(/^#/, "");
    if (current === next) {
      hashReady.current = true;
      return;
    }
    if (skipPush.current) {
      skipPush.current = false;
      hashReady.current = true;
      return;
    }
    const method = hashReady.current ? "pushState" : "replaceState";
    hashReady.current = true;
    history[method](null, "", `#${next}`);
  }, [state.route]);

  const send = (action: Action) => dispatch(action);
  const word = state.glosses.find(gloss => gloss.id === wordId) ?? null;

  const api: Api = {
    state,
    busy,
    toast,
    word,
    lookup,
    go: route => send({ type: "go", route }),
    openLesson: bookId => {
      if (!state.lessons.some(lesson => lesson.bookId === bookId)) {
        setToast("Для этой книги пока нет фрагмента.");
        return;
      }
      send({ type: "open-lesson", bookId });
    },
    openWord: id => {
      setLookup(null);
      setWordId(id);
    },
    openLookup: (hanzi, sentence) => {
      setWordId(null);
      setLookup({ hanzi, sentence });
    },
    closeWord: () => {
      setWordId(null);
      setLookup(null);
    },
    setAnswer: (bookId, questionId, value) => send({ type: "answer", bookId, questionId, value }),
    setPhase: (bookId, phase) => send({ type: "phase", bookId, phase }),
    setDifficulty: (bookId, difficulty) => send({ type: "difficulty", bookId, difficulty }),
    setComment: (bookId, comment) => send({ type: "comment", bookId, comment }),
    submitReview: async bookId => {
      const lesson = state.lessons.find(item => item.bookId === bookId);
      const attempt = state.attempts[bookId];
      if (!lesson || !attempt) return;
      setBusy("Проверяю ответы…");
      try {
        let review = localReview(lesson, attempt.answers);
        if (endpointReady(state.settings)) {
          try {
            review = await gradeWithModel(state.settings, lesson, attempt.answers, attempt.difficulty, attempt.comment);
          } catch (error) {
            const message = error instanceof Error ? error.message : "Модель недоступна";
            review = localReview(lesson, attempt.answers, `Разбор собран на устройстве. ${message}`);
          }
        }
        send({ type: "review", bookId, review });
      } finally {
        setBusy(null);
      }
    },
    resetAttempt: bookId => send({ type: "reset-attempt", bookId }),
    saveSettings: settings => send({ type: "settings", settings }),
    testConnection: () => pingProvider(state.settings),
    dismissBanner: () => send({ type: "dismiss-banner" }),
    addNote: note => {
      send({ type: "add-note", note: { ...note, id: newId("note") } });
      setToast(note.kind === "bookmark" ? "Закладка сохранена" : "Заметка сохранена");
    },
    toggleGloss: id => send({ type: "toggle-gloss", glossId: id }),
    cycleSkill: id => send({ type: "cycle-skill", skillId: id }),
    askWord: async (hanzi, sentence) => {
      if (!endpointReady(state.settings)) {
        setToast("Чтобы спросить модель, укажите endpoint в настройках.");
        return;
      }
      setBusy("Смотрю слово…");
      try {
        const gloss = await lookupGloss(state.settings, hanzi, sentence);
        send({ type: "upsert-gloss", gloss });
        setLookup(null);
        setWordId(gloss.id);
      } catch (error) {
        setToast(error instanceof Error ? error.message : "Не удалось объяснить слово");
      } finally {
        setBusy(null);
      }
    },
    addBook: async input => {
      const id = newId("book");
      const offline = buildOfflineLesson({
        bookId: id,
        chapterTitle: "Фрагмент 1",
        fragmentTitle: "Текст",
        passage: input.text,
        pool: state.glosses,
      });
      const book: Book = {
        id,
        title: input.title.trim(),
        author: input.author.trim() || "Без автора",
        cover: TONES[state.books.length % TONES.length] ?? "ink",
        status: "reading",
        progress: 0,
        lastSession: "Только что",
        place: "Фрагмент 1",
        words: 0,
        chars: input.text.length,
        observations: 0,
        chapters: [{ id: "c1", title: "Фрагмент 1", status: "current", detail: "Сейчас" }],
        recent: [],
        custom: true,
      };
      if (!endpointReady(state.settings)) {
        send({ type: "add-book", book, lesson: offline.lesson, glosses: offline.glosses });
        return;
      }
      setBusy("Собираю занятие…");
      try {
        const generated = await generateLesson(state.settings, input.text, id, "Фрагмент 1", "Текст");
        send({ type: "add-book", book, lesson: generated.lesson, glosses: generated.glosses });
      } catch (error) {
        send({ type: "add-book", book, lesson: offline.lesson, glosses: offline.glosses });
        setToast(error instanceof Error ? `Открыт локальный шаблон. ${error.message}` : "Открыт локальный шаблон.");
      } finally {
        setBusy(null);
      }
    },
    resetData: () => {
      setWordId(null);
      setLookup(null);
      send({ type: "reset" });
      setToast("Локальные данные сброшены");
    },
    speak: text => {
      if (!("speechSynthesis" in window)) {
        setToast("Озвучка недоступна в этом браузере.");
        return;
      }
      const utter = new SpeechSynthesisUtterance(text);
      utter.lang = "zh-CN";
      window.speechSynthesis.cancel();
      window.speechSynthesis.speak(utter);
    },
    toastMessage: setToast,
  };

  return <Ctx.Provider value={api}>{children}</Ctx.Provider>;
}

export function useApp(): Api {
  const value = useContext(Ctx);
  if (!value) throw new Error("useApp outside provider");
  return value;
}
