export type QuizMode = "reading" | "meaning";

export type GameSettings = {
  quizMode: QuizMode;
};

const STORAGE_KEY = "hanzi-reading-settings";

const DEFAULT_SETTINGS: GameSettings = {
  quizMode: "reading",
};

export function isQuizMode(value: unknown): value is QuizMode {
  return value === "reading" || value === "meaning";
}

export function loadSettings(): GameSettings {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (!raw) return { ...DEFAULT_SETTINGS };
    const parsed = JSON.parse(raw) as Partial<GameSettings>;
    return {
      quizMode: isQuizMode(parsed.quizMode)
        ? parsed.quizMode
        : DEFAULT_SETTINGS.quizMode,
    };
  } catch {
    return { ...DEFAULT_SETTINGS };
  }
}

export function saveSettings(settings: GameSettings): void {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(settings));
  } catch {
    /* ignore quota / private mode */
  }
}

export function quizModeLabel(mode: QuizMode): string {
  return mode === "reading" ? "Reading (pinyin)" : "Meaning (keyword)";
}
